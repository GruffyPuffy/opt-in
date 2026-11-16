package se.optin

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

internal data class Country(val region: String, val label: String, val callingCode: Int)

internal object CallRules {
    private const val PREFS = "opt_in_rules"
    private const val HOME_REGION = "home_region"
    private const val ALLOWED_COUNTRIES = "allowed_countries"
    private const val ALLOWED_NUMBERS = "allowed_numbers"
    private const val BLOCKED = "blocked_calls"
    private const val CALL_EVENTS = "call_events"
    private const val SHOW_ALL_CALLS = "show_all_calls"
    private const val MAX_CALL_EVENTS = 200
    private val phoneUtil = PhoneNumberUtil.getInstance()

    val countries: List<Country> by lazy {
        phoneUtil.supportedRegions
            .map { region ->
                val name = Locale("", region).getDisplayCountry(Locale.getDefault())
                val callingCode = phoneUtil.getCountryCodeForRegion(region)
                Country(region, "$name (+$callingCode)", callingCode)
            }
            .filter { it.callingCode > 0 }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun homeRegion(context: Context): String? = prefs(context).getString(HOME_REGION, null)

    fun setHomeRegion(context: Context, region: String) {
        prefs(context).edit().putString(HOME_REGION, region).apply()
    }

    fun allowedRegions(context: Context): Set<String> =
        prefs(context).getStringSet(ALLOWED_COUNTRIES, emptySet())?.toSet() ?: emptySet()

    fun addAllowedRegion(context: Context, region: String) {
        val updated = allowedRegions(context).toMutableSet().apply { add(region) }
        prefs(context).edit().putStringSet(ALLOWED_COUNTRIES, updated).apply()
    }

    fun removeAllowedRegion(context: Context, region: String) {
        val updated = allowedRegions(context).toMutableSet().apply { remove(region) }
        prefs(context).edit().putStringSet(ALLOWED_COUNTRIES, updated).apply()
    }

    fun allowedNumbers(context: Context): Set<String> =
        prefs(context).getStringSet(ALLOWED_NUMBERS, emptySet())?.toSet() ?: emptySet()

    fun addAllowedNumber(context: Context, raw: String): Boolean {
        val home = homeRegion(context) ?: return false
        val number = normalize(raw, home) ?: return false
        val updated = allowedNumbers(context).toMutableSet().apply { add(number) }
        prefs(context).edit().putStringSet(ALLOWED_NUMBERS, updated).apply()
        return true
    }

    fun removeAllowedNumber(context: Context, number: String) {
        val updated = allowedNumbers(context).toMutableSet().apply { remove(number) }
        prefs(context).edit().putStringSet(ALLOWED_NUMBERS, updated).apply()
    }

    fun isAllowed(context: Context, raw: String): Boolean {
        val home = homeRegion(context) ?: return true
        val number = normalize(raw, home) ?: return true
        if (number in allowedNumbers(context)) return true

        return try {
            val parsed = phoneUtil.parse(number, "ZZ")
            val region = phoneUtil.getRegionCodeForNumber(parsed)
            region == home || region in allowedRegions(context)
        } catch (_: Exception) {
            // Unparseable and private caller IDs have no country to check.
            true
        }
    }

    fun blockedCalls(context: Context): List<String> =
        prefs(context).getStringSet(BLOCKED, emptySet())?.toList()?.sortedDescending() ?: emptyList()

    data class CallEvent(val timestamp: Long, val number: String, val blocked: Boolean)

    fun callEvents(context: Context): List<CallEvent> {
        val saved = prefs(context).getStringSet(CALL_EVENTS, emptySet()).orEmpty()
        if (saved.isNotEmpty()) {
            return saved.mapNotNull { value ->
                val parts = value.split('|', limit = 3)
                val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val blocked = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: return@mapNotNull null
                val number = parts.getOrNull(2) ?: return@mapNotNull null
                CallEvent(timestamp, number, blocked)
            }.sortedByDescending { it.timestamp }
        }
        // Keep blocked calls saved by older app versions visible after upgrading.
        return blockedCalls(context).mapNotNull { value ->
            val parts = value.split('|', limit = 2)
            CallEvent(parts.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null,
                parts.getOrNull(1) ?: return@mapNotNull null, true)
        }.sortedByDescending { it.timestamp }
    }

    fun showAllCalls(context: Context): Boolean =
        prefs(context).getBoolean(SHOW_ALL_CALLS, false)

    fun setShowAllCalls(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(SHOW_ALL_CALLS, show).apply()
    }

    fun recordCall(context: Context, raw: String, blocked: Boolean) {
        val home = homeRegion(context) ?: return
        val number = normalize(raw, home) ?: return
        val events = callEvents(context).take(MAX_CALL_EVENTS - 1).map {
            "${it.timestamp}|${it.blocked}|${it.number}"
        }.toMutableSet()
        events.add("${System.currentTimeMillis()}|$blocked|$number")
        prefs(context).edit().putStringSet(CALL_EVENTS, events).apply()
    }

    fun recordBlocked(context: Context, raw: String) {
        recordCall(context, raw, true)
    }

    fun clearBlocked(context: Context) {
        prefs(context).edit().remove(BLOCKED).remove(CALL_EVENTS).apply()
    }

    private fun normalize(raw: String, homeRegion: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val international = when {
            trimmed.startsWith("+") -> trimmed
            trimmed.startsWith("00") -> "+${trimmed.drop(2)}"
            else -> trimmed
        }
        return try {
            val defaultRegion = if (international.startsWith("+")) "ZZ" else homeRegion
            val parsed = phoneUtil.parse(international, defaultRegion)
            if (!phoneUtil.isPossibleNumber(parsed)) null
            else phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
