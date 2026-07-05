package com.lunaris.frostedglass

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FrostedGlassQS"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return

        XposedBridge.log("$TAG: Hooking SystemUI...")

        try {
            // Hook QS Tile views
            TileBlurHook.hookTiles(param.classLoader)
            
            // Hook QS Panel
            TileBlurHook.hookPanel(param.classLoader)

            XposedBridge.log("$TAG: All hooks applied successfully!")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying hooks: ${e.message}")
            XposedBridge.log(e)
        }
    }
}
