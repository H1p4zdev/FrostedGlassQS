package com.lunaris.frostedglass

import android.content.Context
import android.content.res.Resources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FrostedGlassQS"
    }

    object ModuleRes {
        private var _res: Resources? = null
        val res: Resources get() = _res!!
        val isReady: Boolean get() = _res != null

        fun init(context: Context) {
            if (_res != null) return
            _res = try {
                context.packageManager.getResourcesForApplication("com.lunaris.frostedglass")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: ModuleRes init failed: ${e.message}")
                null
            }
        }
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: [LOADED] Package: ${param.packageName}")

        if (param.packageName != "com.android.systemui") return

        XposedBridge.log("$TAG: *** SystemUI detected! Applying hooks... ***")

        try {
            TileBlurHook.hookTiles(param.classLoader)
            XposedBridge.log("$TAG: Tile hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Tile hook error: ${e.message}")
        }

        try {
            TileBlurHook.hookPanel(param.classLoader)
            XposedBridge.log("$TAG: Panel hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Panel hook error: ${e.message}")
        }

        try {
            TileBlurHook.hookPowerMenu(param.classLoader)
            XposedBridge.log("$TAG: Power menu hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Power menu hook error: ${e.message}")
        }

        try {
            TileBlurHook.hookLockscreen(param.classLoader)
            XposedBridge.log("$TAG: Lockscreen hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen hook error: ${e.message}")
        }

        try {
            TileBlurHook.hookDialogs(param.classLoader)
            XposedBridge.log("$TAG: Dialog hooks done")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Dialog hook error: ${e.message}")
        }

        XposedBridge.log("$TAG: *** All hooks applied! ***")
    }
}
