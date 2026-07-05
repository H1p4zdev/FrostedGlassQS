package com.lunaris.frostedglass

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FrostedGlassQS"
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: [LOADED] Package: ${param.packageName}")

        if (param.packageName != "com.android.systemui") return

        XposedBridge.log("$TAG: *** SystemUI detected! Applying hooks... ***")

        // QS Tiles
        try {
            TileBlurHook.hookTiles(param.classLoader)
            XposedBridge.log("$TAG: Tile hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Tile hook error: ${e.message}")
        }

        // QS Panel
        try {
            TileBlurHook.hookPanel(param.classLoader)
            XposedBridge.log("$TAG: Panel hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Panel hook error: ${e.message}")
        }

        // Power Menu
        try {
            TileBlurHook.hookPowerMenu(param.classLoader)
            XposedBridge.log("$TAG: Power menu hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Power menu hook error: ${e.message}")
        }

        // Lockscreen
        try {
            TileBlurHook.hookLockscreen(param.classLoader)
            XposedBridge.log("$TAG: Lockscreen hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen hook error: ${e.message}")
        }

        XposedBridge.log("$TAG: *** All hooks applied! ***")
    }
}
