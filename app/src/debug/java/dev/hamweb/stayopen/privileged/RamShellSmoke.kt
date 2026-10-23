package dev.hamweb.stayopen.privileged

import android.content.Context
import android.os.Looper
import org.json.JSONObject

/** Debug APK only: runs the exact privileged implementation under adb shell in emulator CI. */
object RamShellSmoke {
    @JvmStatic fun main(args: Array<String>) {
        try {
            @Suppress("DEPRECATION")
            if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
            val activityThread = Class.forName("android.app.ActivityThread")
            val thread = activityThread.getMethod("systemMain").invoke(null)
            val system = activityThread.getMethod("getSystemContext").invoke(thread) as Context
            val context = system.createPackageContext("dev.hamweb.stayopen.debug", Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            val service = RamUserService(context)
            val fixture = "dev.hamweb.stayopen.ramfixture"
            val result = when (args.firstOrNull()) {
                "scan" -> service.scan(arrayOf(fixture), emptyArray())
                "protected" -> service.clear(arrayOf(fixture), arrayOf(fixture))
                "clear" -> service.clear(arrayOf(fixture), emptyArray())
                else -> error("Unknown test case")
            }
            check(JSONObject(result).getInt("uid") == 2000) { "Must test shell, not root" }
            println("STAYOPEN_RESULT=$result")
            kotlin.system.exitProcess(0)
        } catch (e: Throwable) {
            e.printStackTrace(); kotlin.system.exitProcess(1)
        }
    }
}
