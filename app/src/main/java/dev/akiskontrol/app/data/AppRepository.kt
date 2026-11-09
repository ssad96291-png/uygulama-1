package dev.akiskontrol.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import dev.akiskontrol.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One process-wide owner of configuration and traffic accounting. Admission/settlement are
 * synchronous and atomic; UI snapshots are published each second and disk writes every three.
 * Temporary grants bypass app rules and speed limits, but always respect the global quota.
 */
class AppRepository private constructor(context: Context) {
    private val context = context.applicationContext
    private val lock = Any()
    private val diskLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = AtomicFile(File(this.context.filesDir, "traffic-state.json"))
    private val admin = AdminAuth(this.context.filesDir)
    private val policy = PolicyEngine()
    private val refreshing = AtomicBoolean(false)
    private val refreshPending = AtomicBoolean(false)
    // Guarded by lock. A changed UID cannot borrow a previous package's rules while
    // PackageManager discovery is in progress. An APK update retains its identity.
    private var identityEpoch = 0L
    private val pendingIdentityUids = mutableSetOf<Int>()
    private val replacingUids = mutableSetOf<Int>()
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
            synchronized(lock) {
                identityEpoch++
                if (uid >= 0) {
                    pendingIdentityUids.add(uid)
                    if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        replacingUids.add(uid)
                    } else replacingUids.remove(uid)
                }
            }
            refreshApps()
        }
    }
    private val loaded = loadState()
    private var identities = loaded.first.identities
    private var undoRules: Map<Int, AppRule>? = null
    private var generation = 0L
    private var persistedGeneration = 0L
    private var usageDirty = false
    private val mutableState = MutableStateFlow(AppState(
        rules = loaded.first.rules,
        usage = loaded.first.usage,
        globalQuota = loaded.first.globalQuota,
        settings = loaded.first.settings,
        temporaryAllowUntil = loaded.first.temporary.filterValues { it > System.currentTimeMillis() },
        logs = loaded.second?.let { listOf(LogEntry(System.currentTimeMillis(), it)) } ?: emptyList(),
    ))
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    init {
        // Revalidate saved identities before any previously configured UID can send data.
        // Android may have reassigned it while our process was stopped.
        pendingIdentityUids.addAll(loaded.first.identities.keys + loaded.first.rules.keys + loaded.first.temporary.keys)
        policy.restoreUsage(loaded.first.usage)
        configureLocked()
        // This singleton and receiver both live for the application process lifetime.
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this.context, packageReceiver, packages, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                synchronized(lock) {
                    val current = mutableState.value
                    val activeTemporary = current.temporaryAllowUntil.filterValues { it > System.currentTimeMillis() }
                    if (activeTemporary != current.temporaryAllowUntil) {
                        mutableState.value = current.copy(temporaryAllowUntil = activeTemporary)
                        configureLocked()
                        changedLocked()
                    }
                    publishUsageLocked()
                }
                if (++seconds == 3) {
                    seconds = 0
                    runCatching { flush() }.onFailure { log("Ayarlar kaydedilemedi; depolama alanını denetleyin") }
                }
            }
        }
        refreshApps()
    }

    /** PackageManager work and labels stay off the UI thread. Shared UIDs expose a single row. */
    fun refreshApps() {
        refreshPending.set(true)
        launchRefreshWorker()
    }

    private fun launchRefreshWorker() {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (refreshPending.getAndSet(false)) {
                    val epoch = synchronized(lock) { identityEpoch }
                    try {
                        val discovery = discoverApps()
                        synchronized(lock) {
                            if (epoch != identityEpoch) {
                                // A package event arrived during the scan. Never publish its stale
                                // result or clear the newer UID guard; immediately scan again.
                                refreshPending.set(true)
                            } else {
                                // PACKAGE_REMOVED with REPLACING is only the middle of an update.
                                // Preserve its old identity and rules until ADDED/REPLACED arrives.
                                val stable = if (replacingUids.isEmpty()) discovery else Discovery(
                                    discovery.apps.filterNot { it.uid in replacingUids } +
                                        mutableState.value.apps.filter { it.uid in replacingUids },
                                    discovery.identities.filterKeys { it !in replacingUids } +
                                        identities.filterKeys { it in replacingUids },
                                )
                                reconcileDiscoveryLocked(stable)
                                pendingIdentityUids.removeAll { it !in replacingUids }
                            }
                        }
                    } catch (failure: Exception) {
                        // Keep affected UIDs closed until a successful later scan.
                        log("Uygulama listesi okunamadı: ${failure.javaClass.simpleName}")
                    }
                }
            } finally {
                refreshing.set(false)
                // Covers a request arriving between the loop's final check and releasing ownership.
                if (refreshPending.get()) launchRefreshWorker()
            }
        }
    }

    fun ruleFor(uid: Int): AppRule = synchronized(lock) {
        require(uid >= 0) { "Uygulama kimliği geçersiz" }
        val current = mutableState.value
        current.rules[uid] ?: AppRule(uid,
            wifi = NetworkRule(allowed = current.settings.defaultWifiAllowed),
            mobile = NetworkRule(allowed = current.settings.defaultMobileAllowed))
    }

    fun saveRule(rule: AppRule) = saveRules(listOf(rule))

    fun saveRules(rules: List<AppRule>) {
        require(rules.map { it.uid }.distinct().size == rules.size) { "Yinelenen uygulama kuralı" }
        if (rules.isEmpty()) return
        synchronized(lock) {
            val current = mutableState.value
            undoRules = current.rules.toMap()
            mutableState.value = current.copy(rules = current.rules + rules.associateBy { it.uid })
            configureLocked()
            changedLocked()
            logLocked("${rules.size} uygulamanın ağ kuralları güncellendi")
        }
    }

    fun undoLastRules(): Boolean = synchronized(lock) {
        val previous = undoRules ?: return@synchronized false
        undoRules = null
        mutableState.value = mutableState.value.copy(rules = previous)
        configureLocked()
        changedLocked()
        logLocked("Son kural değişikliği geri alındı")
        true
    }

    fun setGlobalQuota(quota: GlobalQuota) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(globalQuota = quota)
        configureLocked()
        changedLocked()
    }

    fun saveSettings(settings: AppSettings) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(settings = settings)
        configureLocked()
        changedLocked()
    }

    fun allowTemporarily(uid: Int, minutes: Int) = synchronized(lock) {
        require(uid >= 0 && minutes in 1..1_440) { "Geçici izin 1–1440 dakika olmalı" }
        val until = System.currentTimeMillis() + minutes * 60_000L
        mutableState.value = mutableState.value.copy(temporaryAllowUntil = mutableState.value.temporaryAllowUntil + (uid to until))
        configureLocked()
        changedLocked()
        logLocked("$minutes dakika geçici izin verildi; genel kota geçerlidir")
    }

    fun clearTemporary(uid: Int) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(temporaryAllowUntil = mutableState.value.temporaryAllowUntil - uid)
        configureLocked()
        changedLocked()
    }

    fun setProtection(status: ProtectionStatus, message: String) = synchronized(lock) {
        val safeMessage = message.take(500)
        mutableState.value = mutableState.value.copy(status = status, statusMessage = safeMessage)
        logLocked(safeMessage)
    }

    fun setNetwork(network: NetworkKind) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(network = network)
    }

    fun log(message: String) = synchronized(lock) { logLocked(message.take(500)) }

    fun reserve(uid: Int, network: NetworkKind, upload: Boolean, requestedBytes: Int): Long = synchronized(lock) {
        if (uid in pendingIdentityUids) 0L else policy.reserve(uid, network, upload, requestedBytes)
    }

    fun reserveDatagram(uid: Int, network: NetworkKind, upload: Boolean, requestedBytes: Int): Long = synchronized(lock) {
        if (uid in pendingIdentityUids) 0L else policy.reserveDatagram(uid, network, upload, requestedBytes)
    }

    fun reservedBytes(token: Long): Int = synchronized(lock) { policy.reservedBytes(token) }
    fun delay(token: Long): Long = synchronized(lock) { policy.delay(token) }

    fun complete(token: Long, actualBytes: Int) = synchronized(lock) {
        val existed = policy.reservedBytes(token) > 0
        policy.complete(token, actualBytes)
        if (existed && actualBytes > 0) {
            usageDirty = true
            changedLocked()
        }
    }

    /** Synchronous durability boundary for service shutdown and document operations. */
    fun flush() = synchronized(diskLock) {
        val capture = synchronized(lock) {
            if (generation == persistedGeneration) return@synchronized null
            publishUsageLocked()
            generation to snapshotLocked()
        } ?: return@synchronized
        val encoded = StateJson.encode(capture.second, backup = false).toByteArray(Charsets.UTF_8)
        val stream = storage.startWrite()
        try {
            stream.write(encoded)
            storage.finishWrite(stream)
        } catch (failure: Exception) {
            storage.failWrite(stream)
            throw failure
        }
        synchronized(lock) { persistedGeneration = capture.first }
    }

    fun exportCsv(): String {
        val snapshot = synchronized(lock) {
            publishUsageLocked()
            Triple(mutableState.value.usage, mutableState.value.apps.associateBy { it.uid }, identities.toMap())
        }
        return buildString {
            append("date,uid,packages,application,network,download_bytes,upload_bytes,total_bytes\r\n")
            snapshot.first.forEach { entry ->
                val app = snapshot.second[entry.uid]
                val fields = listOf(entry.day, entry.uid.toString(), snapshot.third[entry.uid].orEmpty().joinToString(";"),
                    app?.label ?: "UID ${entry.uid}", entry.network.name, entry.downloadBytes.toString(),
                    entry.uploadBytes.toString(), addSaturated(entry.downloadBytes, entry.uploadBytes).toString())
                append(fields.joinToString(",", transform = ::csvCell)).append("\r\n")
            }
        }
    }

    fun exportBackup(): String {
        // A fresh package map makes even an export immediately after startup portable.
        val discovery = discoverApps()
        val snapshot = synchronized(lock) {
            reconcileDiscoveryLocked(discovery)
            publishUsageLocked()
            snapshotLocked()
        }
        return StateJson.encode(snapshot, backup = true)
    }

    /** All parsing and identity remapping complete before any live state is changed. */
    fun importBackup(json: String) {
        val imported = StateJson.decode(json, backup = true)
        val discovery = discoverApps()
        val currentByPackage = discovery.identities.flatMap { (uid, packages) -> packages.map { it to uid } }.toMap()
        val targetUids = imported.identities.mapValues { (_, packages) -> packages.mapNotNull(currentByPackage::get).toSet() }
        val remappedRules = linkedMapOf<Int, AppRule>()
        var skippedRules = 0
        imported.rules.forEach { (oldUid, rule) ->
            val targets = targetUids[oldUid].orEmpty()
            if (targets.isEmpty()) skippedRules++
            targets.forEach { uid ->
                val remapped = rule.copy(uid = uid)
                require(remappedRules[uid] == null || remappedRules[uid] == remapped) {
                    "Bu cihazda ortak UID kullanan uygulamaların yedek kuralları çakışıyor"
                }
                remappedRules[uid] = remapped
            }
        }
        // An old shared UID may split into several UIDs on another device. Its historical bytes
        // cannot be assigned fairly, so skip those rows instead of duplicating their usage.
        val remappedUsage = linkedMapOf<Triple<Int, NetworkKind, String>, UsageEntry>()
        var skippedUsage = 0
        imported.usage.forEach { usage ->
            val uid = targetUids[usage.uid]?.singleOrNull()
            if (uid == null) skippedUsage++ else {
                val key = Triple(uid, usage.network, usage.day)
                val previous = remappedUsage[key]
                remappedUsage[key] = usage.copy(uid = uid,
                    downloadBytes = addSaturated(previous?.downloadBytes ?: 0L, usage.downloadBytes),
                    uploadBytes = addSaturated(previous?.uploadBytes ?: 0L, usage.uploadBytes))
            }
        }
        // Validate the complete replacement with an isolated policy before touching the live one.
        PolicyEngine().apply {
            configure(remappedRules, imported.globalQuota, imported.settings, emptyMap())
            restoreUsage(remappedUsage.values.toList())
        }
        synchronized(lock) {
            check(mutableState.value.status !in listOf(ProtectionStatus.STARTING, ProtectionStatus.RUNNING) && policy.pendingReservationCount() == 0) {
                "Yedeği yüklemeden önce korumayı durdurun"
            }
            policy.restoreUsage(remappedUsage.values.toList())
            identities = discovery.identities
            undoRules = null
            mutableState.value = mutableState.value.copy(apps = discovery.apps, rules = remappedRules.toMap(),
                usage = remappedUsage.values.toList(), globalQuota = imported.globalQuota, settings = imported.settings,
                temporaryAllowUntil = emptyMap())
            configureLocked()
            usageDirty = false
            changedLocked()
            logLocked("Yedek yüklendi. Atlanan: $skippedRules kural, $skippedUsage kullanım kaydı; paket kimlikleri bu cihazla eşleştirildi")
        }
        flush()
    }

    fun hasAdmin(): Boolean = admin.hasAdmin()
    fun createAdmin(username: String, password: String) = admin.create(username, password)
    fun verifyAdmin(username: String, password: String): Boolean = admin.verify(username, password)

    private fun configureLocked() {
        val current = mutableState.value
        policy.configure(current.rules, current.globalQuota, current.settings, current.temporaryAllowUntil)
    }

    private fun changedLocked() { generation++ }

    private fun reconcileDiscoveryLocked(discovery: Discovery) {
        val current = mutableState.value
        val usageUids = policy.snapshotUsage().mapTo(mutableSetOf()) { it.uid }
        val reconciliation = reconcileIdentities(identities, discovery.identities,
            usageUids + current.rules.keys + current.temporaryAllowUntil.keys)
        reconciliation.archivedUids.forEach { (uid, archiveUid) -> policy.archiveUid(uid, archiveUid) }
        val retired = reconciliation.archivedUids.keys
        if (identities != reconciliation.identities) {
            identities = reconciliation.identities
            changedLocked()
        }
        mutableState.value = current.copy(apps = discovery.apps, rules = current.rules - retired,
            temporaryAllowUntil = current.temporaryAllowUntil - retired)
        if (retired.isNotEmpty()) {
            usageDirty = true
            publishUsageLocked()
            undoRules = null
            configureLocked()
            changedLocked()
            logLocked("${retired.size} eski uygulamanın kullanımı arşivlendi; yeni kimliklere eski kurallar uygulanmadı")
        }
    }

    private fun publishUsageLocked() {
        if (!usageDirty) return
        mutableState.value = mutableState.value.copy(usage = policy.snapshotUsage())
        usageDirty = false
    }

    private fun snapshotLocked(): StoredState {
        val current = mutableState.value
        return StoredState(current.rules, current.usage, current.globalQuota, current.settings,
            identities.toMap(), current.temporaryAllowUntil)
    }

    private fun logLocked(message: String) {
        val current = mutableState.value
        if (current.logs.lastOrNull()?.let { it.message == message && System.currentTimeMillis() - it.time < 5_000 } == true) return
        mutableState.value = current.copy(logs = (current.logs + LogEntry(System.currentTimeMillis(), message)).takeLast(200))
    }

    private fun loadState(): Pair<StoredState, String?> = try {
        val text = storage.openRead().use { input ->
            require(input.channel.size() <= StateJson.MAX_JSON_CHARS.toLong()) { "Kayıt dosyası çok büyük" }
            input.readBytes().toString(Charsets.UTF_8)
        }
        StateJson.decode(text, backup = false) to null
    } catch (_: FileNotFoundException) {
        StoredState() to null
    } catch (_: Exception) {
        StoredState(settings = AppSettings(defaultWifiAllowed = false, defaultMobileAllowed = false)) to
            "Kayıt dosyası okunamadı. Güvenlik için varsayılan Wi-Fi ve mobil erişimi kapatıldı"
    }

    private data class Discovery(val apps: List<AppEntry>, val identities: Map<Int, List<String>>)

    @Suppress("DEPRECATION")
    private fun discoverApps(): Discovery {
        val manager = context.packageManager
        val packages = manager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            // Akış's native outbound sockets and its own package are outside the local VPN.
            // Offering editable controls for that UID would promise enforcement we cannot give.
            .filter { it.applicationInfo?.uid?.let { uid -> uid >= 0 && uid != context.applicationInfo.uid } == true }
        val groups = packages.groupBy { requireNotNull(it.applicationInfo).uid }
            .filterValues { group -> group.any { Manifest.permission.INTERNET in it.requestedPermissions.orEmpty() } }
        val packageMap = linkedMapOf<Int, List<String>>()
        val apps = groups.map { (uid, group) ->
            val sorted = group.sortedBy { it.packageName }
            val names = sorted.map { it.packageName }
            packageMap[uid] = names
            val labels = sorted.map { item ->
                runCatching { manager.getApplicationLabel(requireNotNull(item.applicationInfo)).toString() }.getOrDefault(item.packageName)
            }.distinct()
            val label = if (names.size > 1) labels.joinToString(" · ") + " (ortak UID)" else labels.single()
            val isSystem = group.any { (requireNotNull(it.applicationInfo).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0 }
            AppEntry(uid, names.first(), label, isSystem)
        }.sortedWith(compareBy<AppEntry> { it.isSystem }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.uid })
        return Discovery(apps, packageMap)
    }

    private fun csvCell(value: String): String {
        // Labels are controlled by installed packages. Prevent spreadsheet formula execution.
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    companion object {
        // The constructor normalizes to applicationContext; no Activity is retained.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: AppRepository? = null
        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }
        private fun addSaturated(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }
}
