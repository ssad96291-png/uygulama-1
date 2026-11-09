package dev.akiskontrol.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.app.ui.AkisApp
import dev.akiskontrol.app.vpn.AkisVpnService
import dev.akiskontrol.core.ProtectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppRepository
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startProtection() else repository.setProtection(ProtectionStatus.STOPPED, "VPN izni verilmedi")
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpn() }
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) saveDocument { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(repository.exportCsv()) } }
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) saveDocument { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(repository.exportBackup()) } }
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching {
                contentResolver.openInputStream(uri)!!.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(out.size().toLong() + size <= 32L * 1024 * 1024) { "Yedek dosyası çok büyük" }
                        out.write(buffer, 0, size)
                    }
                    val bytes = out.toByteArray()
                    repository.importBackup(bytes.toString(Charsets.UTF_8))
                }
            } }
            toast(outcome.fold({ "Kurallar içe aktarıldı" }, { it.message ?: "Yedek açılamadı" }))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = AppRepository.get(this)
        repository.refreshApps()
        setContent {
            AkisApp(repository, ::toggleProtection,
                { exportCsv.launch("Akis-kullanim.csv") }, { exportBackup.launch("Akis-kurallar.json") },
                { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                { runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }.onFailure { toast("VPN ayarları açılamadı") } })
        }
    }
    override fun onResume() { super.onResume(); if (::repository.isInitialized) repository.refreshApps() }
    private fun toggleProtection() {
        val state = repository.state.value
        if (state.status == ProtectionStatus.RUNNING || state.status == ProtectionStatus.STARTING || state.status == ProtectionStatus.ERROR && isOurVpnActive()) {
            startService(Intent(this, AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP)); return
        }
        val manager = getSystemService(ConnectivityManager::class.java)
        val otherVpn = manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val message = if (otherVpn) "Başka bir VPN bağlantısı açık. Akış etkinleştiğinde Android bu bağlantıyı kapatır. İki VPN aynı profilde birlikte çalışamaz.\n\nAkış, izin ve limitleri telefonda uygular; uzak bir VPN sunucusuna bağlanmaz. Mevcut VPN yerine Akış'ı başlatmak istiyor musun?"
            else "Akış, seçtiğin uygulamaların internet izinlerini, hızlarını ve kotalarını telefonda uygulamak için Android VPN iznini kullanır. Uzak bir VPN sunucusuna bağlanmaz. Kullanım kayıtları cihazında saklanır.\n\nKoruma çalışırken Android bir VPN göstergesi ve durum bildirimi gösterir."
        AlertDialog.Builder(this).setTitle(if (otherVpn) "Mevcut VPN değişecek" else "Yerel internet kontrolü")
            .setMessage(message).setNegativeButton("Vazgeç", null).setPositiveButton("Başlat") { _, _ ->
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpn()
            }.show()
    }
    private fun isOurVpnActive(): Boolean = AkisVpnService.hasActiveTunnel
    private fun requestVpn() { val permission = VpnService.prepare(this); if (permission != null) vpnPermission.launch(permission) else startProtection() }
    private fun startProtection() {
        repository.setProtection(ProtectionStatus.STARTING, "Koruma başlatılıyor")
        runCatching { ContextCompat.startForegroundService(this, Intent(this, AkisVpnService::class.java)) }
            .onFailure { repository.setProtection(ProtectionStatus.ERROR, "Koruma başlatılamadı"); repository.log(it.message ?: "Hizmet hatası") }
    }
    private fun saveDocument(write: () -> Unit) = lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching(write) }
        toast(result.fold({ "Dosya kaydedildi" }, { it.message ?: "Dosya kaydedilemedi" }))
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
