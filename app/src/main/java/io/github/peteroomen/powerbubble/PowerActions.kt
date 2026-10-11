package io.github.peteroomen.powerbubble

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

enum class ShizukuState { NOT_RUNNING, NEEDS_PERMISSION, READY }

/**
 * Restart and shut down. Ordinary apps can't do either, so these go through Shizuku, which
 * runs with ADB shell privileges (the shell user holds android.permission.REBOOT).
 */
object PowerActions {
    private const val TAG = "PowerActions"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun shizukuState(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.NEEDS_PERMISSION
        }
    } catch (t: Throwable) {
        ShizukuState.NOT_RUNNING
    }

    /** Returns false if Shizuku isn't available, so the caller can fall back to the system power menu. */
    fun reboot(): Boolean = callPowerManager("reboot")

    fun shutdown(): Boolean = callPowerManager("shutdown")

    // IPowerManager.reboot(boolean confirm, String reason, boolean wait) and
    // IPowerManager.shutdown(boolean confirm, String reason, boolean wait)
    private fun callPowerManager(method: String): Boolean {
        if (shizukuState() != ShizukuState.READY) return false
        return try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/os/IPowerManager")
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.POWER_SERVICE))
            val powerManager = Class.forName("android.os.IPowerManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)!!
            powerManager.javaClass
                .getMethod(method, Boolean::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(powerManager, false, null, false)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "IPowerManager.$method via Shizuku failed", t)
            false
        }
    }
}
