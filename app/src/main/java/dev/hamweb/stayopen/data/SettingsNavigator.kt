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
    fun usageAccess() = open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
        Uri.parse("package:${context.packageName}")), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
}
