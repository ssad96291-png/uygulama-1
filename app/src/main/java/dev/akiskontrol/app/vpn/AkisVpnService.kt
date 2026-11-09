package dev.akiskontrol.app.vpn

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import dev.akiskontrol.app.MainActivity
import dev.akiskontrol.app.R
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.core.*
import dev.akiskontrol.engine.mobile.Bridge
import dev.akiskontrol.engine.mobile.Mobile
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/** One Android VPN, local packet processing; outbound sockets are bound to the physical network. */
class AkisVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()
    private val stopping = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    // Android service commands and cleanup completion update these only on the main thread.
    private var latestStartId = 0
    private var restartRequested = false
    private var cleanupComplete = false
    private var destroyed = false
    private var stopMessage = "Koruma kapalı"
    private var stopIsError = false
    private lateinit var repository: AppRepository
    private lateinit var connectivity: ConnectivityManager
    private lateinit var quotaAlerts: QuotaAlerts
    private var tun: ParcelFileDescriptor? = null
    private var lastNetwork: Network? = null
    private var lastKind: NetworkKind = NetworkKind.NONE
    private var lastPolicy: List<Any>? = null
    private var callbackRegistered = false
    private var observeJob: Job? = null
    private var reconfigureJob: Job? = null
    private var lastErrorLogTime = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = requestReconfigure()
        override fun onLost(network: Network) = requestReconfigure()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = requestReconfigure()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = requestReconfigure()
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository.get(this)
        quotaAlerts = QuotaAlerts(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        val channel = NotificationChannel(CHANNEL, "İnternet kontrolü", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Akış yerel VPN korumasının durumu"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (destroyed) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP) {
            shutdown("Koruma kapalı")
            return START_NOT_STICKY
        }
        try {
            if (stopping.get()) {
                // A sticky redelivery must not undo a user stop or a permission revocation.
                if (intent == null) {
                    if (cleanupComplete) finishShutdown()
                    return START_NOT_STICKY
                }
                if (prepare(this) != null) {
                    shutdown("VPN izni gerekli", error = true)
                    return START_NOT_STICKY
                }
                showForeground("Koruma yeniden hazırlanıyor")
                restartRequested = true
                repository.setProtection(ProtectionStatus.STARTING, "Koruma yeniden hazırlanıyor")
                // A newer start can reach AMS before stopSelfResult, but be delivered to us
                // afterward. Resume immediately if its preceding cleanup has already finished.
                if (cleanupComplete) return if (resumeAfterCleanup()) START_STICKY else START_NOT_STICKY
                return START_STICKY
            }
            return if (startProtection()) START_STICKY else START_NOT_STICKY
        } catch (e: Exception) {
            startFailed(e)
            return START_NOT_STICKY
        }
    }

    private fun showForeground(message: String) {
        val notification = notification(message)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun startProtection(): Boolean {
        showForeground("Koruma hazırlanıyor")
        if (prepare(this) != null) {
            shutdown("VPN izni gerekli", error = true)
            return false
        }
        stopIsError = false
        stopMessage = "Koruma kapalı"
        if (repository.state.value.status != ProtectionStatus.RUNNING) {
            repository.setProtection(ProtectionStatus.STARTING, "Yerel koruma hazırlanıyor")
        }
        if (!callbackRegistered) {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
            connectivity.registerNetworkCallback(request, networkCallback)
            callbackRegistered = true
        }
        if (observeJob == null) {
            observeJob = serviceScope.launch {
                repository.state.collect { state ->
                    quotaAlerts.evaluate(state)
                    val key = policyKey(state)
                    if (key != lastPolicy) requestReconfigure()
                }
            }
        }
        requestReconfigure()
        return true
    }

    private fun startFailed(error: Exception) {
        repository.log("Başlatma hatası: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}")
        shutdown("Koruma başlatılamadı. Ayrıntılar bakım günlüğünde.", error = true)
    }

    private fun resumeAfterCleanup(): Boolean {
        restartRequested = false
        cleanupComplete = false
        stopping.set(false)
        return try { startProtection() } catch (e: Exception) { startFailed(e); false }
    }

    @Synchronized private fun requestReconfigure() {
        if (stopping.get()) return
        reconfigureJob?.cancel()
        reconfigureJob = serviceScope.launch {
            delay(120)
            synchronized(lifecycleLock) { reconfigure() }
        }
    }

    private fun choosePhysicalNetwork(): Pair<Network, NetworkKind>? = connectivity.allNetworks.mapNotNull { network ->
        val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
            else -> return@mapNotNull null
        }
        val score = (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 1000 else 0) + if (kind == NetworkKind.WIFI) 100 else 50
        Triple(network, kind, score)
    }.maxByOrNull { it.third }?.let { it.first to it.second }

    private fun policyKey(state: AppState): List<Any> = listOf(state.rules, state.globalQuota,
        state.settings.defaultWifiAllowed, state.settings.defaultMobileAllowed, state.temporaryAllowUntil)

    private fun reconfigure() {
        if (stopping.get()) return
        try {
            val physical = choosePhysicalNetwork()
            val key = policyKey(repository.state.value)
            val kind = physical?.second ?: NetworkKind.NONE
            // Keep the TUN and its routes alive during changes. Closing it would briefly bypass rules.
            if (tun == null) {
                repository.setProtection(ProtectionStatus.STARTING, "Yerel koruma hazırlanıyor")
                tun = Builder().setSession("Akış · internet kontrolü")
                    .setMtu(1500).addAddress("10.111.0.2", 32).addAddress("fd71:616b:6973::2", 128)
                    .addRoute("0.0.0.0", 0).addRoute("::", 0).addDnsServer("10.111.0.1")
                    .setBlocking(false).setMetered(false).addDisallowedApplication(packageName)
                    .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                    .establish() ?: throw IllegalStateException("VPN izni artık geçerli değil")
                hasActiveTunnel = true
            }
            if (physical?.first == lastNetwork && key == lastPolicy && Mobile.running()) return
            Mobile.stop()
            lastNetwork = null
            lastPolicy = key
            repository.setNetwork(kind)
            if (physical == null) {
                setUnderlyingNetworks(emptyArray())
                repository.setProtection(ProtectionStatus.STARTING, "Wi-Fi veya mobil bağlantı bekleniyor; trafik kapalı")
                updateNotification("Bağlantı bekleniyor · trafik kapalı")
                return
            }
            val network = physical.first
            setUnderlyingNetworks(arrayOf(network))
            if (stopping.get()) return
            Mobile.start(tun!!.fd.toLong(), 1500, AndroidBridge(network, kind))
            if (stopping.get()) { Mobile.stop(); return }
            lastNetwork = network
            lastKind = kind
            repository.setProtection(ProtectionStatus.RUNNING, if (kind == NetworkKind.WIFI) "Wi-Fi kuralları etkin" else "Mobil veri kuralları etkin")
            repository.log("Koruma etkin · ${if (kind == NetworkKind.WIFI) "Wi-Fi" else "mobil veri"}")
            updateNotification(repository.state.value.statusMessage)
        } catch (e: Exception) {
            runCatching { Mobile.stop() }
            lastNetwork = null
            val message = if (tun != null) "Ağ motoru durdu; koruma tüneli trafiği kapalı tutuyor"
                else "Koruma başlatılamadı; internet trafiği denetlenmiyor"
            repository.setProtection(ProtectionStatus.ERROR, message)
            repository.log("Ağ motoru: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(180)}")
            updateNotification(message)
        }
    }

    private inner class AndroidBridge(private val network: Network, private val kind: NetworkKind) : Bridge {
        override fun protect(fd: Long): Boolean = try {
            if (stopping.get() || !this@AkisVpnService.protect(fd.toInt())) false
            else {
                ParcelFileDescriptor.fromFd(fd.toInt()).use { network.bindSocket(it.fileDescriptor) }
                true
            }
        } catch (_: Exception) { false }

        override fun dnsServer(): String = connectivity.getLinkProperties(network)?.dnsServers?.firstOrNull()?.hostAddress.orEmpty()

        override fun owner(protocol: Long, srcIP: String, srcPort: Long, dstIP: String, dstPort: Long): Long = try {
            val src = InetSocketAddress(InetAddress.getByName(srcIP), srcPort.toInt())
            val dst = InetSocketAddress(InetAddress.getByName(dstIP), dstPort.toInt())
            var uid = connectivity.getConnectionOwnerUid(protocol.toInt(), src, dst)
            if (uid < 0 && protocol.toInt() == OsConstants.IPPROTO_UDP) {
                val any = if (src.address.address.size == 16) "::" else "0.0.0.0"
                uid = connectivity.getConnectionOwnerUid(protocol.toInt(), src, InetSocketAddress(InetAddress.getByName(any), 0))
            }
            // Android 13+ SDK sandboxes have separate UIDs but belong to the host app.
            // Match the OS VPN UID-range behavior so they share its rules and accounting.
            (if (Build.VERSION.SDK_INT >= 33) appUidForSdkSandbox(uid) else uid).toLong()
        } catch (_: Exception) { -1L }

        override fun reserve(uid: Long, upload: Boolean, requestedBytes: Long): Long =
            if (stopping.get()) 0 else repository.reserve(uid.toInt(), kind, upload, requestedBytes.toInt())
        override fun reserveDatagram(uid: Long, upload: Boolean, requestedBytes: Long): Long =
            if (stopping.get()) 0 else repository.reserveDatagram(uid.toInt(), kind, upload, requestedBytes.toInt())
        override fun reservedBytes(token: Long): Long = repository.reservedBytes(token).toLong()
        override fun delay(token: Long): Long = repository.delay(token)
        override fun complete(token: Long, actualBytes: Long) = repository.complete(token, actualBytes.toInt())
        override fun event(message: String) {
            // Bound repeated endpoint/owner errors, without retaining domains or packet contents.
            val now = SystemClock.elapsedRealtime()
            synchronized(this@AkisVpnService) {
                if (now - lastErrorLogTime > 2000) { lastErrorLogTime = now; repository.log(message.take(200)) }
            }
        }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AkisVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_status)
            .setContentTitle("Akış koruması").setContentText(message).setOngoing(true)
            .setContentIntent(open).addAction(0, "Durdur", stop).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    private fun updateNotification(message: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message)) }

    private fun shutdown(message: String, error: Boolean = false) {
        restartRequested = false
        stopMessage = message
        stopIsError = error
        if (!stopping.compareAndSet(false, true)) {
            if (cleanupComplete) finishShutdown()
            return
        }
        cleanupComplete = false
        observeJob?.cancel(); observeJob = null
        reconfigureJob?.cancel(); reconfigureJob = null
        if (callbackRegistered) { runCatching { connectivity.unregisterNetworkCallback(networkCallback) }; callbackRegistered = false }
        serviceScope.launch {
            synchronized(lifecycleLock) {
                runCatching { Mobile.stop() }
                runCatching { tun?.close() }; tun = null; hasActiveTunnel = false
                lastNetwork = null; lastKind = NetworkKind.NONE; lastPolicy = null
                runCatching { repository.flush() }.onFailure { repository.log("Son kullanım kayıtları diske yazılamadı") }
            }
            withContext(Dispatchers.Main.immediate) { finishShutdown() }
        }
    }

    private fun finishShutdown() {
        if (destroyed) return
        cleanupComplete = true
        if (restartRequested) {
            resumeAfterCleanup()
            return
        }
        repository.setProtection(if (stopIsError) ProtectionStatus.ERROR else ProtectionStatus.STOPPED, stopMessage)
        repository.log(stopMessage)
        // Unlike stopSelf(), the token check cannot destroy a newer start already accepted
        // by AMS but not yet delivered to onStartCommand on this process's main thread.
        val stopped = if (latestStartId > 0) stopSelfResult(latestStartId) else { stopSelf(); true }
        if (stopped) stopForeground(STOP_FOREGROUND_REMOVE)
        else runCatching { updateNotification(stopMessage) }
    }

    override fun onRevoke() {
        // VpnService may deliver revocation on a Binder thread. Serialize it with start/stop
        // commands; shutdown cancels any queued restart and resumption rechecks VPN consent.
        mainHandler.post {
            if (!destroyed) shutdown("VPN izni kaldırıldı veya başka bir VPN etkinleştirildi", error = true)
        }
    }
    override fun onDestroy() {
        destroyed = true
        restartRequested = false
        stopping.set(true)
        if (callbackRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        serviceScope.cancel()
        synchronized(lifecycleLock) {
            runCatching { Mobile.stop() }
            runCatching { tun?.close() }; tun = null; hasActiveTunnel = false
            if (::repository.isInitialized) {
                runCatching { repository.flush() }
                if (stopIsError) repository.setProtection(ProtectionStatus.ERROR, stopMessage)
                else if (repository.state.value.status != ProtectionStatus.ERROR) repository.setProtection(ProtectionStatus.STOPPED, "Koruma kapalı")
            }
        }
        super.onDestroy()
    }
    companion object {
        @Volatile var hasActiveTunnel: Boolean = false
            private set
        const val ACTION_STOP = "dev.akiskontrol.app.STOP"
        private const val CHANNEL = "akis.protection"
        private const val NOTIFICATION_ID = 41
    }
}
