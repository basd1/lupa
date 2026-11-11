package dev.bastian.lupa.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through what people do first: start, look at the summary, open the list, scroll it, open one app, then
 * visit the other tabs. The classes and methods this touches end up in the Baseline Profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForFirstScan()

        openApps()
        scrollApps()

        // Open the first row, which brings up the detail screen with the score breakdown, then come back.
        device.findObject(By.res("apps_list")).children.firstOrNull()?.click()
        device.wait(Until.hasObject(By.text("Open app settings")), 10_000)
        device.pressBack()
        device.wait(Until.hasObject(By.res("apps_list")), 10_000)

        device.findObject(By.text("Changes")).click()
        device.wait(Until.hasObject(By.text("Analyze now")), 10_000)
        device.findObject(By.text("Settings")).click()
        device.wait(Until.hasObject(By.text("Periodic analysis")), 10_000)
        device.findObject(By.text("Summary")).click()
        device.wait(Until.hasObject(By.textContains("analysed")), 10_000)
    }
}
