package com.lunaris.frostedglass

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FrostedGlassQS"
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        // Log ALL packages to verify module loads
        XposedBridge.log("$TAG: [LOADED] Package: ${param.packageName}")

        if (param.packageName != "com.android.systemui") return

        XposedBridge.log("$TAG: *** SystemUI detected! Applying hooks... ***")

        try {
            TileBlurHook.hookTiles(param.classLoader)
            XposedBridge.log("$TAG: Tile hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Tile hook error: ${e.message}")
            XposedBridge.log(e)
        }

        try {
            TileBlurHook.hookPanel(param.classLoader)
            XposedBridge.log("$TAG: Panel hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Panel hook error: ${e.message}")
            XposedBridge.log(e)
        }

        XposedBridge.log("$TAG: *** All hooks applied! ***")
    }
}
