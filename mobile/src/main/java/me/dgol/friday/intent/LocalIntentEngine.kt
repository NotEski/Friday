package me.dgol.friday.assistant.intent

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Local, lightweight intent engine.
 * - Parse user text with regex into a small set of built-in actions.
 * - Execute with standard Android intents (no special perms).
 * - Return a short user-facing message, or null if unhandled (so you can fall back later).
 */
object LocalIntentEngine {

    // --- Public API ---

    /**
     * Try to handle [input] locally. Returns a user-facing reply if handled, else null.
     */
    fun handle(context: Context, input: String): String? {
        val text = input.trim().lowercase(Locale.getDefault())
        val action = parse(text) ?: return null
        return try {
            execute(context, action)
        } catch (t: Throwable) {
            // If something goes wrong, return a concise explanation
            when (action) {
                is Action.SetAlarm -> "I couldn't set the alarm (your Clock app might not support it)."
                is Action.SetTimer -> "I couldn't start the timer."
                is Action.OpenApp  -> "I couldn't find the app “${action.appQuery}”."
                is Action.WebSearch -> "I couldn't start a web search."
                is Action.TellTime -> "I couldn't get the current time."
            }
        }
    }

    // --- Internal model ---

    private sealed class Action {
        data class SetAlarm(val hour24: Int, val minute: Int, val label: String?) : Action()
        data class SetTimer(val seconds: Int, val label: String?) : Action()
        data class OpenApp(val appQuery: String) : Action()
        data class WebSearch(val query: String) : Action()
        object TellTime : Action()
    }

    // --- Parsing ---

    private fun parse(text: String): Action? {
        // time-aware smalltalk
        if (text.matches(Regex("^(what'?s|whats|what is) (the )?time( now)?\\??$"))) {
            return Action.TellTime
        }

        // alarm examples:
        //  "set an alarm for 7 am"
        //  "wake me up at 6:30"
        //  "alarm at 21:05 called gym"
        val alarmRe = Regex(
            pattern = """\b(?:set (?:an )?alarm(?: for)?|alarm(?: for)?|wake me (?:up )?at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?(?:\s+(?:called|named|label(?:ed)?|for)\s+(.+))?""",
            option = RegexOption.IGNORE_CASE
        )
        alarmRe.find(text)?.let { m ->
            val hRaw = m.groupValues[1].toIntOrNull() ?: return@let null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val ampm = m.groupValues[3]
            val label = m.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }

            val hour24 = to24Hour(hRaw, ampm)
            if (hour24 in 0..23 && min in 0..59) {
                return Action.SetAlarm(hour24, min, label)
            }
        }

        // timer examples:
        //  "set a timer for 5 minutes"
        //  "start a 30 second timer"
        //  "countdown 1 hour 20 minutes"
        val timerRe = Regex(
            pattern = """\b(?:set (?:a )?timer(?: for)?|start (?:a )?timer|countdown)\s*(?:(\d+)\s*hour[s]?)?\s*(?:(\d+)\s*minute[s]?)?\s*(?:(\d+)\s*second[s]?)?""",
            option = RegexOption.IGNORE_CASE
        )
        timerRe.find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val sec = m.groupValues[3].toIntOrNull() ?: 0
            val total = h * 3600 + min * 60 + sec
            if (total > 0) return Action.SetTimer(total, label = null)
        }

        // open app examples:
        //  "open youtube", "launch maps", "start spotify"
        val openAppRe = Regex("""\b(?:open|launch|start)\s+([a-z0-9 .+-_&]+)$""", RegexOption.IGNORE_CASE)
        openAppRe.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return Action.OpenApp(q)
        }

        // web search examples:
        //  "search for cats", "google weather tomorrow"
        val searchRe = Regex("""\b(?:search for|google|web ?search)\s+(.+)$""", RegexOption.IGNORE_CASE)
        searchRe.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return Action.WebSearch(q)
        }

        return null
    }

    private fun to24Hour(hourRaw: Int, ampm: String?): Int {
        var h = hourRaw.coerceIn(0, 23)
        val ap = ampm?.lowercase(Locale.getDefault())
        return if (ap == null || ap.isBlank()) {
            // No AM/PM specified: keep as given (let Clock UI confirm).
            h
        } else if (ap == "am") {
            if (h == 12) 0 else h
        } else { // pm
            if (h in 1..11) h + 12 else 12
        }
    }

    // --- Execution ---

    private fun execute(context: Context, action: Action): String {
        return when (action) {
            is Action.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.hour24)
                    putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                    // We avoid EXTRA_SKIP_UI to keep permissions simple.
                }
                startSafely(context, intent)
                val label = action.label?.let { " “$it”" } ?: ""
                "Setting an alarm for %02d:%02d%s".format(action.hour24, action.minute, label)
            }

            is Action.SetTimer -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label ?: "Friday timer")
                }
                startSafely(context, intent)
                "Starting a %s timer".format(humanDuration(action.seconds))
            }

            is Action.OpenApp -> {
                val best = findLaunchIntent(context, action.appQuery)
                    ?: throw ActivityNotFoundException("No app matches ${action.appQuery}")
                context.startActivity(best.intent)
                "Opening ${best.label}"
            }

            is Action.WebSearch -> {
                val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, action.query)
                }
                try {
                    startSafely(context, webIntent)
                } catch (_: Throwable) {
                    // fallback to browser
                    val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))
                    startSafely(context, Intent(Intent.ACTION_VIEW, uri))
                }
                "Searching the web for “${action.query}”"
            }

            is Action.TellTime -> {
                val now = Calendar.getInstance()
                val fmt = DateFormat.getTimeFormat(context)
                "It’s ${fmt.format(now.time)}."
            }
        }
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

    private data class LaunchMatch(val intent: Intent, val label: String, val score: Int)

    private fun findLaunchIntent(context: Context, query: String): LaunchMatch? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(launcher, 0)
        if (matches.isEmpty()) return null

        val q = query.lowercase(Locale.getDefault())
        var best: LaunchMatch? = null

        for (ri in matches) {
            val label = ri.loadLabel(pm)?.toString() ?: continue
            val pkg = ri.activityInfo?.packageName ?: continue
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            val l = label.lowercase(Locale.getDefault())
            val score = appNameScore(q, l, pkg.lowercase(Locale.getDefault()))
            if (best == null || score > best!!.score) {
                best = LaunchMatch(launch, label, score)
            }
        }
        return best
    }

    private fun appNameScore(q: String, label: String, pkg: String): Int {
        // simple fuzzy scoring: exact >> startsWith >> contains >> package hints
        return when {
            label == q -> 100
            label.startsWith(q) -> 90
            label.contains(q) -> 70
            pkg.contains(q.replace(' ', '.')) -> 60
            else -> 0
        }
    }

    private fun startSafely(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
