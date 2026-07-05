package com.lunaris.frostedglass

import android.content.res.Resources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XModuleResources
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FrostedGlassQS"
        var modulePath: String = ""
            private set
    }

    object ModuleRes {
        lateinit var resources: Resources
            private set

        fun init(path: String) {
            resources = XModuleResources.createInstance(path, null)
        }
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: [LOADED] Package: ${param.packageName}")

        if (param.packageName != "com.android.systemui") return

        modulePath = param.modulePath
        ModuleRes.init(modulePath)

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
