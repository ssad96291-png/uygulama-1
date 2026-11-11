package dev.akiskontrol.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.core.AppRule
import dev.akiskontrol.core.NetworkRule
import dev.akiskontrol.core.ProtectionStatus
import dev.akiskontrol.core.QuotaAction
import dev.akiskontrol.core.QuotaPeriod
import java.io.File
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

    private fun freshObject(selector: BySelector): UiObject2? {
        // UiAutomator 2.3.0 does not invalidate cached Compose nodes on recent Android.
        // A field can refresh independently while its sibling tab/label remains stale.
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return device.findObject(selector)
    }

    private fun objectOnScreen(selector: BySelector): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            freshObject(selector)?.let { return it }
            SystemClock.sleep(150)
        }
        runCatching { device.dumpWindowHierarchy(File(context.cacheDir, "mint-ui-failure.xml")) }
        throw AssertionError("Expected visible UI element: $selector; hierarchy saved to mint-ui-failure.xml")
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) throw AssertionError(message)
            SystemClock.sleep(150)
        }
    }

    private fun downloadField(): UiObject2 {
        val field = objectOnScreen(By.desc("İndirme değeri"))
        // Material3 exposes this semantics label as a child of the editable node.
        // Follow its ancestors first; a description-only node has no editable child.
        var candidate: UiObject2? = field
        repeat(4) {
            val current = candidate ?: return@repeat
            if (current.className == "android.widget.EditText") return current
            candidate = current.parent
        }
        return field.findObject(By.clazz("android.widget.EditText"))
            ?: throw AssertionError("Download amount must expose an editable accessibility node")
    }

    private fun expectDownload(text: String) {
        val end = SystemClock.uptimeMillis() + timeout
        var observed = "not read"
        while (SystemClock.uptimeMillis() < end) {
            val read = runCatching { downloadField().text }
            if (read.isSuccess && read.getOrNull() == text) return
            observed = read.fold({ "text=$it" }, { "${it.javaClass.simpleName}: ${it.message}" })
            SystemClock.sleep(150)
        }
        throw AssertionError("Expected download draft $text; last observation: $observed")
    }

    private fun resumedActivity(excluding: MainActivity? = null): MainActivity {
        var result: MainActivity? = null
        waitFor("Expected a resumed MainActivity distinct from the previous instance") {
            instrumentation.runOnMainSync {
                result = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull { it !== excluding }
            }
            result != null
        }
        return result!!
    }

    private fun revealNetworkControls() {
        val activity = resumedActivity()
        instrumentation.runOnMainSync {
            activity.currentFocus?.clearFocus()
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        device.waitForIdle()
        freshObject(By.scrollable(true))?.scroll(Direction.UP, 1f)
        device.waitForIdle()
    }

    private fun visibleKeyboardTop(activity: MainActivity): Int? {
        var top: Int? = null
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                top = decor.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
        }
        return top
    }

    @Test
    fun landscapeKeyboardKeepsAmountAndSaveVisible() {
        assertEquals("Use an idle dedicated test emulator", ProtectionStatus.STOPPED, repository.state.value.status)
        val info = context.packageManager.getApplicationInfo("dev.akiskontrol.probe", 0)
        val label = context.packageManager.getApplicationLabel(info).toString()
        val originalRule = repository.ruleFor(info.uid)
        val originalSettings = repository.state.value.settings
        val baseline = AppRule(info.uid, wifi = NetworkRule(downloadBps = 12_000))
        var originalOrientation: Int? = null
        try {
            repository.saveSettings(originalSettings.copy(darkTheme = false, showSystemApps = false))
            repository.saveRule(baseline)
            repository.refreshApps()
            device.wakeUp()
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val activity = resumedActivity()
            originalOrientation = activity.requestedOrientation
            objectOnScreen(By.desc("Ayarları aç"))
            objectOnScreen(By.clazz("android.widget.EditText")).text = label
            objectOnScreen(By.text(label).clazz("android.widget.TextView")).click()
            objectOnScreen(By.desc("Kaydetmeden geri dön"))
            instrumentation.runOnMainSync {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor("Expected landscape configuration") {
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            assertTrue("Run this regression on a phone with a short side of at most 360 dp",
                context.resources.configuration.screenHeightDp <= 360)
            val landscape = resumedActivity(excluding = activity)
            waitFor("Expected download field in the scrolling form") {
                if (freshObject(By.desc("İndirme değeri")) != null) true else {
                    freshObject(By.scrollable(true))?.scroll(Direction.DOWN, .5f)
                    false
                }
            }
            downloadField().click()
            instrumentation.runOnMainSync {
                WindowCompat.getInsetsController(landscape.window, landscape.window.decorView)
                    .show(WindowInsetsCompat.Type.ime())
            }
            waitFor("Expected the real system keyboard to be visible") {
                visibleKeyboardTop(landscape) != null
            }
            val minimumTouchHeight = (48 * context.resources.displayMetrics.density).toInt()
            waitFor("The amount and Save must each remain at least 48 dp tall above the keyboard") {
                val keyboardTop = visibleKeyboardTop(landscape) ?: return@waitFor false
                val amount = runCatching { downloadField().visibleBounds }.getOrNull()
                val save = freshObject(By.text("Kaydet"))?.let {
                    var button = it
                    while (!button.isClickable) button = button.parent ?: return@let null
                    button.visibleBounds
                }
                amount != null && save != null && amount.height() >= minimumTouchHeight &&
                    save.height() >= minimumTouchHeight && amount.bottom <= keyboardTop &&
                    save.bottom <= keyboardTop && !Rect.intersects(amount, save)
            }
            expectDownload("12")
            downloadField().text = "6"
            expectDownload("6")
            assertTrue("Screenshot must be saved", device.takeScreenshot(File(context.cacheDir, "mint-landscape-ime-after.png")))
            downloadField().text = "-2"
            objectOnScreen(By.text("Kaydet")).click()
            objectOnScreen(By.text("İşaretli alanları kontrol et."))
            assertEquals("Invalid compact save must preserve the stored rule", baseline, repository.ruleFor(info.uid))
            downloadField().text = "6"
            expectDownload("6")
            objectOnScreen(By.text("Kaydet")).click()
            waitFor("Compact Save must persist the edited amount and preserve the other network") {
                repository.ruleFor(info.uid) == baseline.copy(wifi = baseline.wifi.copy(downloadBps = 6_000))
            }
            waitFor("Compact Save must close the editor") { freshObject(By.desc("Kaydetmeden geri dön")) == null }
        } finally {
            try {
                originalOrientation?.let { orientation ->
                    val current = resumedActivity()
                    instrumentation.runOnMainSync { current.requestedOrientation = orientation }
                }
            } finally {
                repository.saveRule(originalRule)
                repository.saveSettings(originalSettings)
            }
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
        try {
            repository.saveSettings(originalSettings.copy(darkTheme = false, showSystemApps = false))
            repository.saveRule(baseline)
            repository.refreshApps()
            device.wakeUp()
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val activity = resumedActivity()
            objectOnScreen(By.desc("Ayarları aç"))
            // Search keeps the probe row visible even when many apps are installed.
            objectOnScreen(By.clazz("android.widget.EditText")).text = label
            assertEquals("Fixture rule must be set before opening the editor", baseline, repository.ruleFor(info.uid))
            objectOnScreen(By.text(label).clazz("android.widget.TextView")).click()
            objectOnScreen(By.desc("Kaydetmeden geri dön"))
            expectDownload("12") // Existing byte rate is presented as KB/sn.
            downloadField().text = "3"
            expectDownload("3")
            revealNetworkControls()
            objectOnScreen(By.text("Mobil veri")).click()
            objectOnScreen(By.desc("Mobil veri internet erişimi"))
            expectDownload("21")
            downloadField().text = "5"
            expectDownload("5")
            assertEquals("Typing must not save either network", baseline, repository.ruleFor(info.uid))

            // Activity recreation exercises the same saved-state path as a rotation,
            // without depending on a particular emulator display orientation.
            instrumentation.runOnMainSync { activity.recreate() }
            val recreated = resumedActivity(excluding = activity)
            revealNetworkControls()
            assertNotSame("A new activity instance must restore the editor", activity, recreated)
            objectOnScreen(By.desc("Kaydetmeden geri dön"))
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
            waitFor("Save must leave the editor") { freshObject(By.desc("Kaydetmeden geri dön")) == null }
            assertEquals("Returning from the editor must preserve the app search", label,
                objectOnScreen(By.clazz("android.widget.EditText")).text)

            objectOnScreen(By.text(label).clazz("android.widget.TextView")).click()
            objectOnScreen(By.desc("Kaydetmeden geri dön"))
            expectDownload("3")
            downloadField().text = "-2"
            expectDownload("-2")
            objectOnScreen(By.text("Kaydet")).click()
            objectOnScreen(By.text("İşaretli alanları kontrol et."))
            assertNotNull("Invalid input must keep the editor open", freshObject(By.desc("Kaydetmeden geri dön")))
            assertEquals("Invalid save must preserve both saved rules", saved, repository.ruleFor(info.uid))
            objectOnScreen(By.desc("Kaydetmeden geri dön")).click()
            waitFor("Back must close the editor") { freshObject(By.desc("Kaydetmeden geri dön")) == null }
            assertEquals("Discarding an invalid draft must preserve the app search", label,
                objectOnScreen(By.clazz("android.widget.EditText")).text)
            assertEquals("Back must discard the invalid draft", saved, repository.ruleFor(info.uid))
            assertEquals("Editing app rules must not alter global quota", originalGlobalQuota, repository.state.value.globalQuota)
        } finally {
            repository.saveRule(originalRule)
            repository.saveSettings(originalSettings)
            repository.setGlobalQuota(originalGlobalQuota)
        }
    }
}
