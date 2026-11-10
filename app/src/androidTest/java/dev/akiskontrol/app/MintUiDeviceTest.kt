package dev.akiskontrol.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.core.AppRule
import dev.akiskontrol.core.NetworkRule
import dev.akiskontrol.core.ProtectionStatus
import dev.akiskontrol.core.QuotaAction
import dev.akiskontrol.core.QuotaPeriod
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the dedicated emulator with :probe installed; never starts VPN traffic. */
@RunWith(AndroidJUnit4::class)
class MintUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository = AppRepository.get(context)
    private val timeout = 120_000L

    private fun objectOnScreen(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), timeout)
            ?: throw AssertionError("Expected visible UI element: $selector")

    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) throw AssertionError(message)
            SystemClock.sleep(150)
        }
    }

    private fun downloadField(): UiObject2 {
        val field = objectOnScreen(By.desc("İndirme değeri"))
        return if (field.className == "android.widget.EditText") field
        else field.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Download amount must expose an editable accessibility node")
    }

    private fun expectDownload(text: String) {
        waitFor("Expected download draft $text") {
            runCatching { downloadField().text == text }.getOrDefault(false)
        }
    }

    @Test
    fun independentDraftsSurviveRecreationAndInvalidInputDoesNotOverwriteSavedRules() {
        assertEquals("Use an idle dedicated test emulator", ProtectionStatus.STOPPED, repository.state.value.status)
        val info = context.packageManager.getApplicationInfo("dev.akiskontrol.probe", 0)
        val label = context.packageManager.getApplicationLabel(info).toString()
        val originalRule = repository.ruleFor(info.uid)
        val originalSettings = repository.state.value.settings
        val originalGlobalQuota = repository.state.value.globalQuota
        val baseline = AppRule(
            info.uid,
            wifi = NetworkRule(
                downloadBps = 12_000, uploadBps = 7_000, quotaBytes = 200_000_000,
                period = QuotaPeriod.MONTHLY, renewalDay = 15,
                quotaAction = QuotaAction.SLOW, afterQuotaBps = 16_000,
            ),
            mobile = NetworkRule(allowed = false, downloadBps = 21_000, uploadBps = 9_000),
        )
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            repository.saveSettings(originalSettings.copy(darkTheme = false, showSystemApps = false))
            repository.saveRule(baseline)
            repository.refreshApps()
            device.wakeUp()
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, timeout)
                ?: throw AssertionError("MainActivity did not launch")
            objectOnScreen(By.desc("Ayarları aç"))
            // Search keeps the probe row visible even when many apps are installed.
            objectOnScreen(By.clazz("android.widget.EditText")).text = label
            objectOnScreen(By.text(label).clazz("android.widget.TextView")).click()
            objectOnScreen(By.text("Bağlantı kuralları"))
            expectDownload("12") // Existing byte rate is presented as KB/sn.
            downloadField().text = "3"
            expectDownload("3")
            objectOnScreen(By.text("Mobil veri")).click()
            objectOnScreen(By.desc("Mobil veri internet erişimi"))
            expectDownload("21")
            downloadField().text = "5"
            expectDownload("5")
            assertEquals("Typing must not save either network", baseline, repository.ruleFor(info.uid))

            // Activity recreation exercises the same saved-state path as a rotation,
            // without depending on a particular emulator display orientation.
            instrumentation.runOnMainSync { activity.recreate() }
            val recreated = instrumentation.waitForMonitorWithTimeout(monitor, timeout)
                ?: throw AssertionError("MainActivity did not recreate")
            assertNotSame("A new activity instance must restore the editor", activity, recreated)
            objectOnScreen(By.text("Bağlantı kuralları"))
            objectOnScreen(By.desc("Mobil veri internet erişimi"))
            expectDownload("5")
            objectOnScreen(By.text("Wi-Fi")).click()
            objectOnScreen(By.desc("Wi-Fi internet erişimi"))
            expectDownload("3")
            assertEquals("Recreation must not save drafts", baseline, repository.ruleFor(info.uid))
            objectOnScreen(By.text("Kaydet")).click()
            val saved = baseline.copy(
                wifi = baseline.wifi.copy(downloadBps = 3_000),
                mobile = baseline.mobile.copy(downloadBps = 5_000),
            )
            waitFor("Both networks and untouched quota fields must be saved correctly") {
                repository.ruleFor(info.uid) == saved
            }
            assertTrue("Save must leave the editor", device.wait(Until.gone(By.text("Bağlantı kuralları")), timeout))

            objectOnScreen(By.text(label).clazz("android.widget.TextView")).click()
            objectOnScreen(By.text("Bağlantı kuralları"))
            expectDownload("3")
            downloadField().text = "-2"
            expectDownload("-2")
            objectOnScreen(By.text("Kaydet")).click()
            objectOnScreen(By.text("İşaretli alanları kontrol et."))
            assertNotNull("Invalid input must keep the editor open", device.findObject(By.text("Bağlantı kuralları")))
            assertEquals("Invalid save must preserve both saved rules", saved, repository.ruleFor(info.uid))
            objectOnScreen(By.desc("Kaydetmeden geri dön")).click()
            assertTrue("Back must close the editor", device.wait(Until.gone(By.text("Bağlantı kuralları")), timeout))
            assertEquals("Back must discard the invalid draft", saved, repository.ruleFor(info.uid))
            assertEquals("Editing app rules must not alter global quota", originalGlobalQuota, repository.state.value.globalQuota)
        } finally {
            instrumentation.removeMonitor(monitor)
            repository.saveRule(originalRule)
            repository.saveSettings(originalSettings)
            repository.setGlobalQuota(originalGlobalQuota)
        }
    }
}
