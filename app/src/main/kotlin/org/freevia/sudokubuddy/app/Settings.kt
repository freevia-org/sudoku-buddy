package org.freevia.sudokubuddy.app

import android.content.Context
import androidx.core.content.edit
import org.freevia.sudokubuddy.solver.RouteStyle

/** Preferences that survive restarts. Deliberately few. */
data class Settings(
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val autoCapture: Boolean = false,
    val autoShareWhenUncertain: Boolean = false,
    /** Explicit consent for training use of newly read reports that are auto-shared. */
    val trainAutoSharedReports: Boolean = false,
    /** What the tutor's route should be good at. See [RouteStyle]. */
    val routeStyle: RouteStyle = RouteStyle.SHORT_CHAINS,
) {
    companion object {
        private const val FILE = "sudokubuddy.settings"
        private const val KEY_HINT_STYLE = "hintStyle"
        private const val KEY_AUTO_CAPTURE = "autoCapture"
        private const val KEY_AUTO_SHARE = "autoShareWhenUncertain"
        private const val KEY_TRAIN_AUTO_SHARED = "trainAutoSharedReports"
        private const val KEY_ROUTE_STYLE = "routeStyle"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return Settings(
                hintStyle = runCatching {
                    HintStyle.valueOf(prefs.getString(KEY_HINT_STYLE, null) ?: HintStyle.EXPLAIN.name)
                }.getOrDefault(HintStyle.EXPLAIN),
                autoCapture = prefs.getBoolean(KEY_AUTO_CAPTURE, false),
                autoShareWhenUncertain = prefs.getBoolean(KEY_AUTO_SHARE, false),
                trainAutoSharedReports = prefs.getBoolean(KEY_TRAIN_AUTO_SHARED, false),
                routeStyle = runCatching {
                    RouteStyle.valueOf(
                        prefs.getString(KEY_ROUTE_STYLE, null) ?: RouteStyle.SHORT_CHAINS.name
                    )
                }.getOrDefault(RouteStyle.SHORT_CHAINS),
            )
        }

        fun save(context: Context, settings: Settings) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                putString(KEY_HINT_STYLE, settings.hintStyle.name)
                putBoolean(KEY_AUTO_CAPTURE, settings.autoCapture)
                putBoolean(KEY_AUTO_SHARE, settings.autoShareWhenUncertain)
                putBoolean(KEY_TRAIN_AUTO_SHARED, settings.trainAutoSharedReports)
                putString(KEY_ROUTE_STYLE, settings.routeStyle.name)
            }
        }
    }
}
