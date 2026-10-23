package dev.hamweb.stayopen.data

import android.os.Build
import dev.hamweb.stayopen.domain.OneUi

data class DeviceInfo(
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val androidVersion: String = Build.VERSION.RELEASE,
    val sdk: Int = Build.VERSION.SDK_INT,
) {
    val isSamsung get() = manufacturer.equals("samsung", ignoreCase = true)
    val friendlyModel: String get() = when {
        !isSamsung -> "$manufacturer $model"
        model.startsWith("SM-G985", true) || model.startsWith("SM-G986", true) -> "Samsung Galaxy S20+"
        model.startsWith("SM-A175", true) || model.startsWith("SM-A176", true) -> "Samsung Galaxy A17"
        model.startsWith("SM-A145", true) || model.startsWith("SM-A146", true) -> "Samsung Galaxy A14"
        else -> "Samsung $model"
    }

    fun modernMenus(oneUi: OneUi): Boolean = when (oneUi) {
        OneUi.AUTO -> sdk >= 34
        OneUi.V5 -> false
        else -> true
    }

    fun sleepingPath(oneUi: OneUi): String = if (modernMenus(oneUi))
        "Settings → Battery → Background usage limits"
    else "Settings → Battery and device care → Battery → Background usage limits"

    fun ramPlusPath(oneUi: OneUi): String = if (modernMenus(oneUi))
        "Settings → Device care → Memory → RAM Plus"
    else "Settings → Battery and device care → Memory → RAM Plus"

    val modelAdvice: String get() = when {
        friendlyModel.endsWith("S20+") -> "For the S20+, start with background usage limits on its installed software. RAM capacity varies by variant; the dashboard uses Android's actual reported memory."
        friendlyModel.endsWith("A17") -> "For the A17, review only the few apps you need most. RAM and One UI features vary by region and hardware variant; no RAM size is assumed."
        friendlyModel.endsWith("A14") -> "For the A14, heavy games and browser tabs may compete for memory. Prioritise a short list of apps and compare your own readings before and after changes."
        else -> "Available menus vary by region, firmware and carrier. If a path is missing, search Android Settings for the setting's name."
    }
}
