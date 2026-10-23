package dev.hamweb.stayopen.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.storage.StorageManager

/** Public Android intents only; every entry falls back when an OEM omits a screen. */
class SettingsNavigator(private val context: Context) {
    private fun open(vararg intents: Intent): String? {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            } catch (_: android.content.ActivityNotFoundException) {
                // Try the next public settings screen.
            } catch (_: SecurityException) {
                // Some OEM settings activities are not exported.
            }
        }
        return "This settings page isn't available. Open Android Settings and search for it."
    }
    fun app(packageName: String) = open(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun apps() = open(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun battery() = open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun powerSaving() = open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun storage() = open(Intent(StorageManager.ACTION_MANAGE_STORAGE),
        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun settings() = open(Intent(Settings.ACTION_SETTINGS))
    fun developer() = open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun shizuku(): String? {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: return "Install Shizuku from shizuku.rikka.app/download, then open it and start its service."
        return open(intent)
    }
    fun web(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun notifications() = open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_SETTINGS))
    fun launchApp(packageName: String): String? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "This app does not have an available launcher screen."
        return open(intent)
    }
    fun usageAccess() = open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
        Uri.parse("package:${context.packageName}")), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
}
