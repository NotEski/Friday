package me.dgol.friday.intent

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Local-first intent engine with robust fallbacks for Clock (alarms/timers).
 */
object LocalIntentEngine {

    // ======================= Public API =======================

    fun handle(context: Context, input: String): String? {
        val text = input.trim().lowercase(Locale.getDefault())
        val action = parse(text) ?: return null
        return try {
            execute(context, action)
        } catch (t: Throwable) {
            when (action) {
                is Action.SetAlarm   -> "I couldn't set the alarm (your Clock app may not support it)."
                is Action.SetTimer   -> "I couldn't start the timer."
                is Action.OpenApp    -> "I couldn't find the app “${action.appQuery}”."
                is Action.WebSearch  -> "I couldn't start a web search."
                is Action.TellTime   -> "I couldn't get the current time."
            }
        }
    }

    // ======================= Model =======================

    private sealed class Action {
        data class SetAlarm(val hour24: Int, val minute: Int, val label: String?) : Action()
        data class SetTimer(val seconds: Int, val label: String?) : Action()
        data class OpenApp(val appQuery: String) : Action()
        data class WebSearch(val query: String) : Action()
        object TellTime : Action()
    }

    // ======================= Parsing =======================

    private fun parse(text: String): Action? {
        if (text.matches(Regex("^(what'?s|whats|what is) (the )?time( now)?\\??$"))) {
            return Action.TellTime
        }

        val alarmRe = Regex(
            """\b(?:set (?:an )?alarm(?: for)?|alarm(?: for)?|wake me (?:up )?at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?(?:\s+(?:called|named|label(?:ed)?|for)\s+(.+))?""",
            RegexOption.IGNORE_CASE
        )
        alarmRe.find(text)?.let { m ->
            val hRaw = m.groupValues[1].toIntOrNull() ?: return@let null
            val min  = m.groupValues[2].toIntOrNull() ?: 0
            val ap   = m.groupValues[3]
            val label = m.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
            val hour24 = to24Hour(hRaw, ap)
            if (hour24 in 0..23 && min in 0..59) {
                return Action.SetAlarm(hour24, min, label)
            }
        }

        val timerRe = Regex(
            """\b(?:set (?:a )?timer(?: for)?|start (?:a )?timer|countdown)\s*(?:(\d+)\s*hour[s]?)?\s*(?:(\d+)\s*minute[s]?)?\s*(?:(\d+)\s*second[s]?)?""",
            RegexOption.IGNORE_CASE
        )
        timerRe.find(text)?.let { m ->
            val h  = m.groupValues[1].toIntOrNull() ?: 0
            val mn = m.groupValues[2].toIntOrNull() ?: 0
            val s  = m.groupValues[3].toIntOrNull() ?: 0
            val total = h * 3600 + mn * 60 + s
            if (total > 0) return Action.SetTimer(total, label = null)
        }

        val openRe = Regex("""\b(?:open|launch|start)\s+([a-z0-9 .+\-_/&]+)$""", RegexOption.IGNORE_CASE)
        openRe.find(text)?.let { m ->
            val q = m.groupValues[1].trim().trimEnd('.', '!', '?')
            if (q.isNotBlank()) return Action.OpenApp(q)
        }

        val searchRe = Regex("""\b(?:search for|google|web ?search)\s+(.+)$""", RegexOption.IGNORE_CASE)
        searchRe.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return Action.WebSearch(q)
        }

        return null
    }

    private fun to24Hour(hourRaw: Int, ampm: String?): Int {
        var h = hourRaw.coerceIn(0, 23)
        return when (ampm?.lowercase(Locale.getDefault())) {
            null, "" -> h
            "am" -> if (h == 12) 0 else h
            "pm" -> if (h in 1..11) h + 12 else 12
            else -> h
        }
    }

    // ======================= Execution =======================

    private fun execute(context: Context, action: Action): String {
        return when (action) {
            is Action.SetAlarm -> setAlarm(context, action)
            is Action.SetTimer -> setTimer(context, action)
            is Action.OpenApp  -> openApp(context, action.appQuery)
            is Action.WebSearch -> {
                val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, action.query)
                }
                if (intentResolvable(context, webIntent)) startSafely(context, webIntent)
                else startSafely(context, Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))))
                "Searching the web for “${action.query}”"
            }
            is Action.TellTime -> {
                val now = Calendar.getInstance()
                val fmt = DateFormat.getTimeFormat(context)
                "It’s ${fmt.format(now.time)}."
            }
        }
    }

    // ----- Alarm/Timer with robust fallbacks -----

    private fun setAlarm(context: Context, a: Action.SetAlarm): String {
        // Try official action w/ confirm UI
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, a.hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            a.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        if (intentResolvable(context, intent)) {
            startSafely(context, intent)
            val label = a.label?.let { " “$it”" } ?: ""
            return "Setting an alarm for %02d:%02d%s".format(a.hour24, a.minute, label)
        }

        // Fallback: open alarms screen in Clock
        if (openClockUI(context, ClockTab.Alarms)) {
            return "Opening Clock to set an alarm"
        }

        // Final fallback: tell the user
        throw ActivityNotFoundException("No Clock app found for alarms")
    }

    private fun setTimer(context: Context, t: Action.SetTimer): String {
        // Try to auto-start timer (if supported)
        val auto = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, t.seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, t.label ?: "Friday timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true) // auto-start if allowed by the Clock app
        }
        if (intentResolvable(context, auto)) {
            return try {
                startSafely(context, auto)
                "Starting a ${humanDuration(t.seconds)} timer"
            } catch (_: Throwable) {
                // Some clocks reject SKIP_UI=true. Try again with UI.
                val withUi = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, t.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, t.label ?: "Friday timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                if (intentResolvable(context, withUi)) {
                    startSafely(context, withUi)
                    "Opening the Clock to start a ${humanDuration(t.seconds)} timer"
                } else {
                    if (openClockUI(context, ClockTab.Timers)) {
                        "Opening Clock to start a timer"
                    } else {
                        throw ActivityNotFoundException("No Clock app found for timers")
                    }
                }
            }
        }

        // If ACTION_SET_TIMER is missing, open Clock UI
        if (openClockUI(context, ClockTab.Timers)) {
            return "Opening Clock to start a timer"
        }
        throw ActivityNotFoundException("No Clock app found for timers")
    }

    // Try to open a specific Clock tab if possible; otherwise open Clock main.
    private enum class ClockTab { Alarms, Timers }

    private fun openClockUI(context: Context, tab: ClockTab): Boolean {
        // 1) Preferred: Standard intents
        val std = when (tab) {
            ClockTab.Alarms -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            ClockTab.Timers -> Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, 1)         // forces timer UI on many devices
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
        }
        if (intentResolvable(context, std)) {
            return runCatching { startSafely(context, std) }.isSuccess
        }

        // 2) Known packages: Google, AOSP, Samsung (best-effort)
        val candidates = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
        val pm = context.packageManager
        for (pkg in candidates) {
            pm.getLaunchIntentForPackage(pkg)?.let { launch ->
                return runCatching { startSafely(context, launch) }.isSuccess
            }
        }
        return false
    }

    // ----- App launching -----

    private fun openApp(context: Context, queryRaw: String): String {
        // Explicit app aliases first (settings/clock/calculator/etc.)
        explicitAppLaunch(context, queryRaw)?.let { (intent, label) ->
            startSafely(context, intent); return "Opening $label"
        }
        // Otherwise fuzzy-match launcher labels
        val best = findLaunchIntent(context, queryRaw)
        if (best == null || best.score < MIN_LAUNCH_SCORE) {
            throw ActivityNotFoundException("No app matches $queryRaw")
        }
        context.startActivity(best.intent)
        return "Opening ${best.label}"
    }

    private const val MIN_LAUNCH_SCORE = 50

    private data class LaunchMatch(val intent: Intent, val label: String, val score: Int)

    private fun findLaunchIntent(context: Context, query: String): LaunchMatch? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(launcher, 0)
        if (matches.isEmpty()) return null

        val q = normalize(query)
        var best: LaunchMatch? = null

        for (ri in matches) {
            val label = ri.loadLabel(pm)?.toString() ?: continue
            val pkg = ri.activityInfo?.packageName ?: continue
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue

            val lNorm = normalize(label)
            val pNorm = normalize(pkg)

            val score = appNameScore(q, lNorm, pNorm)
            if (best == null || score > best.score) best = LaunchMatch(launch, label, score)
        }
        return best
    }

    private fun appNameScore(q: String, label: String, pkg: String): Int = when {
        label == q -> 100
        label.startsWith(q) -> 90
        label.contains(q) -> 75
        pkg.endsWith(".$q") -> 70
        pkg.contains(".$q.") -> 65
        pkg.contains(q) -> 60
        else -> 0
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.getDefault()).replace(Regex("""[^a-z0-9]+"""), " ").trim()

    // Known aliases -> packages (first found wins)
    private fun explicitAppLaunch(context: Context, raw: String): Pair<Intent, String>? {
        val pm = context.packageManager
        val alias = normalize(raw)
        val candidates: List<Pair<String, String>> = when (alias) {
            "settings", "system settings" -> return Intent(Settings.ACTION_SETTINGS) to "Settings"
            "wifi settings", "wi fi settings" -> return Intent(Settings.ACTION_WIFI_SETTINGS) to "Wi-Fi settings"
            "bluetooth settings" -> return Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "Bluetooth settings"
            "location settings", "gps settings" -> return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) to "Location settings"
            "clock", "alarms" -> return Intent(AlarmClock.ACTION_SHOW_ALARMS) to "Clock"
            "timer" -> return Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, 1); putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            } to "Timer"

            "calculator", "calc" -> listOf("com.google.android.calculator" to "Calculator")
            "maps", "google maps", "map" -> listOf("com.google.android.apps.maps" to "Maps")
            "youtube" -> listOf("com.google.android.youtube" to "YouTube")
            "spotify" -> listOf("com.spotify.music" to "Spotify")
            "camera" -> listOf(
                "com.google.android.GoogleCamera" to "Camera",
                "com.android.camera" to "Camera",
                "com.sec.android.app.camera" to "Camera"
            )
            "messages", "sms", "text messages" -> listOf(
                "com.google.android.apps.messaging" to "Messages",
                "com.samsung.android.messaging" to "Messages"
            )
            "phone", "dialer", "dial" -> listOf(
                "com.google.android.dialer" to "Phone",
                "com.samsung.android.dialer" to "Phone"
            )
            "contacts" -> listOf(
                "com.google.android.contacts" to "Contacts",
                "com.samsung.android.app.contacts" to "Contacts"
            )
            else -> emptyList()
        }
        for ((pkg, label) in candidates) {
            pm.getLaunchIntentForPackage(pkg)?.let { intent ->
                return intent to label
            }
        }
        return null
    }

    // ======================= Utilities =======================

    private fun intentResolvable(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null

    private fun startSafely(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm: PackageManager = context.packageManager
        if (intent.resolveActivity(pm) == null) throw ActivityNotFoundException()
        context.startActivity(intent)
    }

    private fun humanDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = ArrayList<String>(3)
        if (h > 0) parts += "$h hour" + if (h != 1) "s" else ""
        if (m > 0) parts += "$m minute" + if (m != 1) "s" else ""
        if (s > 0 || parts.isEmpty()) parts += "$s second" + if (s != 1) "s" else ""
        return parts.joinToString(" ")
    }
}
