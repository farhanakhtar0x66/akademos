package com.globalinstitute.akademos.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small on-device settings. Nothing secret is stored here. */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme
    private val _textScale = MutableStateFlow(1.1f)
    val textScale: StateFlow<Float> = _textScale
    private val _appLock = MutableStateFlow(true)
    val appLock: StateFlow<Boolean> = _appLock

    fun init(context: Context) {
        sp = context.getSharedPreferences("akademos_prefs", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        _textScale.value = sp.getFloat("text_scale", 1.1f)
        _appLock.value = sp.getBoolean("app_lock", true)
    }

    fun setTheme(m: ThemeMode) { _theme.value = m; sp.edit().putString("theme", m.name).apply() }
    fun setTextScale(s: Float) { _textScale.value = s; sp.edit().putFloat("text_scale", s).apply() }
    fun setAppLock(on: Boolean) { _appLock.value = on; sp.edit().putBoolean("app_lock", on).apply() }

    var remindAllClasses: Boolean
        get() = sp.getBoolean("remind_all", true)
        set(v) = sp.edit().putBoolean("remind_all", v).apply()

    var remindersOn: Boolean
        get() = sp.getBoolean("reminders_on", true)
        set(v) = sp.edit().putBoolean("reminders_on", v).apply()

    var moneyReminders: Boolean
        get() = sp.getBoolean("money_reminders", true)
        set(v) = sp.edit().putBoolean("money_reminders", v).apply()

    var checkIn: Boolean
        get() = sp.getBoolean("checkin", true)
        set(v) = sp.edit().putBoolean("checkin", v).apply()

    var hasOwnClasses: Boolean
        get() = sp.getBoolean("own_classes", false)
        set(v) = sp.edit().putBoolean("own_classes", v).apply()

    /** "I've paid" messages the administrator was already told about. */
    var seenClaims: Set<String>
        get() = sp.getStringSet("seen_claims", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seen_claims", v).apply()

    var lastRole: String?
        get() = sp.getString("last_role", null)
        set(v) = sp.edit().putString("last_role", v).apply()

    var lastUserId: String?
        get() = sp.getString("last_uid", null)
        set(v) = sp.edit().putString("last_uid", v).apply()

    var scheduleCache: String?
        get() = sp.getString("schedule_cache", null)
        set(v) = sp.edit().putString("schedule_cache", v).apply()

    var scheduledCodes: Set<String>
        get() = sp.getStringSet("scheduled_codes", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("scheduled_codes", v).apply()

    // markers so reminders stay quiet once attendance is done on this phone
    fun markDone(kind: String, batchId: String, date: String) =
        sp.edit().putBoolean("done:$kind:$batchId:$date", true).apply()
    fun isDone(kind: String, batchId: String, date: String) = sp.getBoolean("done:$kind:$batchId:$date", false)

    fun clearUserData() {
        val keep = setOf("theme", "text_scale", "app_lock")
        sp.edit().apply { sp.all.keys.filter { it !in keep }.forEach { remove(it) } }.apply()
    }
}
