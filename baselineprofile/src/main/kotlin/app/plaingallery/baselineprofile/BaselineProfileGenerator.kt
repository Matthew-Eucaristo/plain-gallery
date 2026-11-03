package app.plaingallery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the hot paths (start, scroll the grid, open and swipe the viewer, switch tabs) so ART
 * compiles them ahead of time. Run on a device with some photos:
 * `./gradlew :app:generateReleaseBaselineProfile`
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "ACCESS_MEDIA_LOCATION").forEach {
            shell.executeShellCommand("pm grant $PACKAGE android.permission.$it").close()
        }

        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT)

        // Swipe rather than fling a found scrollable: the tab pager is scrollable too (sideways) and may be found first.
        val x = device.displayWidth / 2
        repeat(2) { device.swipe(x, device.displayHeight * 4 / 5, x, device.displayHeight / 5, 8) }
        repeat(2) { device.swipe(x, device.displayHeight / 5, x, device.displayHeight * 4 / 5, 8) }

        // Open the first photo, swipe through a few, go back.
        device.findObject(By.descContains(".jpg"))?.click()
        device.waitForIdle()
        repeat(3) { device.swipe(device.displayWidth * 4 / 5, device.displayHeight / 2, device.displayWidth / 5, device.displayHeight / 2, 10) }
        device.pressBack()
        device.waitForIdle()

        listOf("Inbox", "Albums", "Search", "Photos").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }

        // Swipe between tabs (away from the edges, which are system Back).
        val y = device.displayHeight / 2
        repeat(2) { device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 10); device.waitForIdle() }
        repeat(2) { device.swipe(device.displayWidth / 5, y, device.displayWidth * 4 / 5, y, 10); device.waitForIdle() }
    }

    private companion object {
        const val PACKAGE = "app.plaingallery"
        const val TIMEOUT = 10_000L
    }
}
