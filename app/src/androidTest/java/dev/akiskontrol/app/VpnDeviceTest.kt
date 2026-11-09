package dev.akiskontrol.app

import android.content.Intent
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.app.vpn.AkisVpnService
import dev.akiskontrol.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires :probe installed and tools/probe_server.py running on the emulator host. */
@RunWith(AndroidJUnit4::class)
class VpnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository = AppRepository.get(context)
    // Software emulators can deliver Android service callbacks much later than phones.
    // This changes only readiness waits; traffic success and rate assertions stay identical.
    private val readinessTimeout = InstrumentationRegistry.getArguments()
        .getString("readinessTimeoutMs")?.toLongOrNull()?.coerceIn(15_000L, 180_000L) ?: 15_000L
    private fun waitFor(timeout: Long = readinessTimeout, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!condition()) { if (System.currentTimeMillis() > end) fail("Timed out: ${repository.state.value.statusMessage}"); Thread.sleep(150) }
    }
    private fun probe(mode: String = "tcp", bytes: Int = 32768): JSONObject {
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        device.executeShellCommand("am force-stop dev.akiskontrol.probe")
        device.executeShellCommand("am start -W -n dev.akiskontrol.probe/.ProbeActivity --es runId $id --es mode $mode --ei bytes $bytes")
        var result: JSONObject? = null
        waitFor(30000) {
            result = runCatching { JSONObject(device.executeShellCommand("run-as dev.akiskontrol.probe cat files/result-$id.json")) }.getOrNull()
            result != null
        }
        return result!!
    }
    /** Requires tools/probe_dns_server.py and emulator -dns-server 127.0.0.1. */
    @Test fun hostnameConnectionsWorkBeforeDuringAndAfterVpn() {
        device.executeShellCommand("am start -W -n dev.akiskontrol.app/.MainActivity")
        context.startService(Intent(context, AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
        waitFor { !AkisVpnService.hasActiveTunnel }
        repository.refreshApps()
        val uid = context.packageManager.getApplicationInfo("dev.akiskontrol.probe", 0).uid
        val original = repository.ruleFor(uid)
        val originalGlobal = repository.state.value.globalQuota
        try {
            repository.setGlobalQuota(GlobalQuota())
            repository.saveRule(AppRule(uid))
            val baseline = probe("hostname")
            assertTrue("Fixture DNS/HTTP failed without VPN: $baseline", baseline.getBoolean("success"))
            val ipv6Baseline = probe("ipv6-connect")
            assertFalse("Fixture must not reach the reserved IPv6 target: $ipv6Baseline", ipv6Baseline.getBoolean("success"))
            device.executeShellCommand("appops set dev.akiskontrol.app ACTIVATE_VPN allow")
            assertNull("VPN consent must be granted", VpnService.prepare(context))
            device.executeShellCommand("am start -W -n dev.akiskontrol.app/.MainActivity")
            context.startForegroundService(Intent(context, AkisVpnService::class.java))
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING }
            val allowed = probe("hostname")
            assertTrue("System DNS + hostname HTTP failed through VPN: $allowed", allowed.getBoolean("success"))
            val ipv6 = probe("ipv6-connect")
            assertFalse("VPN falsely completed unreachable IPv6 TCP handshake: $ipv6", ipv6.getBoolean("success"))
            repository.saveRule(AppRule(uid, wifi = NetworkRule(allowed = false)))
            Thread.sleep(700)
            val blocked = probe("hostname")
            assertFalse("Blocked app reached hostname endpoint: $blocked", blocked.getBoolean("success"))
            repository.saveRule(AppRule(uid))
            Thread.sleep(700)
            val restored = probe("hostname")
            assertTrue("Hostname HTTP failed after allowing app: $restored", restored.getBoolean("success"))
            context.startService(Intent(context, AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
            waitFor { !AkisVpnService.hasActiveTunnel }
            assertTrue("Hostname HTTP failed after VPN stopped", probe("hostname").getBoolean("success"))
        } finally {
            repository.saveRule(original)
            repository.setGlobalQuota(originalGlobal)
            context.startService(Intent(context, AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
        }
    }
    /** Requires both emulator Wi-Fi and cellular data, plus the local DNS fixture. */
    @Test fun hostnameConnectionsFollowWifiAndMobileRules() {
        device.executeShellCommand("svc wifi enable")
        device.executeShellCommand("am start -W -n dev.akiskontrol.app/.MainActivity")
        repository.refreshApps()
        val uid = context.packageManager.getApplicationInfo("dev.akiskontrol.probe", 0).uid
        val original = repository.ruleFor(uid)
        val originalGlobal = repository.state.value.globalQuota
        try {
            repository.setGlobalQuota(GlobalQuota())
            repository.saveRule(AppRule(uid, wifi = NetworkRule(allowed = false)))
            device.executeShellCommand("appops set dev.akiskontrol.app ACTIVATE_VPN allow")
            assertNull("VPN consent must be granted", VpnService.prepare(context))
            context.startForegroundService(Intent(context, AkisVpnService::class.java))
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING && repository.state.value.network == NetworkKind.WIFI }
            assertFalse("Wi-Fi block did not apply", probe("hostname").getBoolean("success"))
            device.executeShellCommand("svc wifi disable")
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING && repository.state.value.network == NetworkKind.MOBILE }
            val mobile = probe("hostname")
            assertTrue("Allowed mobile hostname HTTP failed: $mobile", mobile.getBoolean("success"))
            val ipv6 = probe("ipv6-connect")
            assertFalse("IPv4-only mobile uplink falsely completed IPv6 connect: $ipv6", ipv6.getBoolean("success"))
            repository.saveRule(AppRule(uid, mobile = NetworkRule(allowed = false)))
            Thread.sleep(700)
            assertFalse("Mobile block did not apply", probe("hostname").getBoolean("success"))
            device.executeShellCommand("svc wifi enable")
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING && repository.state.value.network == NetworkKind.WIFI }
            val wifi = probe("hostname")
            assertTrue("Allowed Wi-Fi hostname HTTP failed after mobile handoff: $wifi", wifi.getBoolean("success"))
        } finally {
            device.executeShellCommand("svc wifi enable")
            repository.saveRule(original)
            repository.setGlobalQuota(originalGlobal)
            context.startService(Intent(context, AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
        }
    }
    @Test fun realPacketsRespectWifiRulesRatesQuotasAndRestart() {
        device.executeShellCommand("am start -W -n dev.akiskontrol.app/.MainActivity")
        repository.refreshApps()
        val uid = context.packageManager.getApplicationInfo("dev.akiskontrol.probe",0).uid
        val original = repository.ruleFor(uid)
        val originalGlobal = repository.state.value.globalQuota
        try {
            repository.setGlobalQuota(GlobalQuota())
            repository.saveRule(AppRule(uid))
            device.executeShellCommand("appops set dev.akiskontrol.app ACTIVATE_VPN allow")
            val consent = VpnService.prepare(context)
            if (consent != null) {
                context.startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val button = device.wait(Until.findObject(By.res("android:id/button1")),10000)
                assertNotNull("Android VPN consent dialog", button); button.click()
            }
            context.startForegroundService(Intent(context, AkisVpnService::class.java))
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING }
            assertEquals(NetworkKind.WIFI, repository.state.value.network)
            val allowed = probe(); assertTrue("Allowed TCP failed: $allowed", allowed.getBoolean("success"))
            val udp = probe("udp",1200); assertTrue("Allowed UDP failed: $udp",udp.getBoolean("success"))
            repository.saveRule(AppRule(uid, wifi=NetworkRule(allowed=false)))
            Thread.sleep(700)
            val blocked = probe(); assertFalse("Blocked TCP passed: $blocked",blocked.getBoolean("success"))
            repository.saveRule(AppRule(uid,wifi=NetworkRule(downloadBps=8192)))
            Thread.sleep(700)
            val paced = probe(bytes=16384); assertTrue("Paced TCP failed: $paced",paced.getBoolean("success"))
            assertTrue("16KB at 8KB/s completed too fast: $paced",paced.getLong("elapsedMs")>=1800)
            repository.saveRule(AppRule(uid,wifi=NetworkRule(downloadBps=1024)))
            Thread.sleep(700)
            val pacedUdp = probe("udp",1200); assertTrue("Low rate UDP dropped: $pacedUdp",pacedUdp.getBoolean("success"))
            assertTrue(pacedUdp.getLong("elapsedMs")>=1100)
            repository.saveRule(AppRule(uid,wifi=NetworkRule(quotaBytes=1)))
            Thread.sleep(700)
            val quota = probe(); assertFalse("Exhausted quota passed: $quota",quota.getBoolean("success"))
            repository.saveRule(AppRule(uid))
            Thread.sleep(700)
            context.startService(Intent(context,AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
            waitFor { !AkisVpnService.hasActiveTunnel }
            device.executeShellCommand("am start -W -n dev.akiskontrol.app/.MainActivity")
            context.startForegroundService(Intent(context, AkisVpnService::class.java))
            waitFor { repository.state.value.status == ProtectionStatus.RUNNING }
            assertTrue("Engine restart failed",probe().getBoolean("success"))
            repository.flush()
            assertTrue("Usage not counted",repository.state.value.usage.any { it.uid==uid && it.downloadBytes>0 })
        } finally {
            repository.saveRule(original); repository.setGlobalQuota(originalGlobal)
            context.startService(Intent(context,AkisVpnService::class.java).setAction(AkisVpnService.ACTION_STOP))
        }
    }
}
