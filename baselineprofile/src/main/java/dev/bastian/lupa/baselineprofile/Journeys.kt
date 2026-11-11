package dev.bastian.lupa.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "dev.bastian.lupa"

private const val WAIT_MS = 60_000L

/** The summary shows "N apps analysed" once the first scan has finished, so the app is really ready then. */
fun MacrobenchmarkScope.waitForFirstScan() {
    check(device.wait(Until.hasObject(By.textContains("analysed")), WAIT_MS)) {
        "The summary never showed the result of the first scan"
    }
}

fun MacrobenchmarkScope.openApps() {
    device.findObject(By.text("Apps")).click()
    check(device.wait(Until.hasObject(By.res("apps_list")), WAIT_MS)) { "The apps list did not appear" }
}

/** Scrolls the apps list down and back up a few times. */
fun MacrobenchmarkScope.scrollApps(rounds: Int = 3) {
    val list = device.findObject(By.res("apps_list"))
    // Keep the gesture away from the system navigation area.
    list.setGestureMargin(device.displayWidth / 5)
    repeat(rounds) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}
