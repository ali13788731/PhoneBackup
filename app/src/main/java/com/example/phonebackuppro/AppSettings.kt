package com.example.phonebackuppro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User settings, persisted in SharedPreferences and observable from Compose. */
class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode by mutableIntStateOf(p.getInt("theme", 0))
        private set
    /** index into [Accents] */
    var accent by mutableIntStateOf(p.getInt("accent", 0))
        private set
    var animations by mutableStateOf(p.getBoolean("animations", true))
        private set
    var haptics by mutableStateOf(p.getBoolean("haptics", true))
        private set
    /** Contacts export format: false = JSON (restorable by this app), true = VCF (any phone). */
    var contactsVcf by mutableStateOf(p.getBoolean("vcf", false))
        private set
    var skipDuplicates by mutableStateOf(p.getBoolean("skipDup", true))
        private set
    var selectAllByDefault by mutableStateOf(p.getBoolean("selectAll", true))
        private set
    var reminders by mutableStateOf(p.getBoolean("reminders", true))
        private set
    var lastBackup by mutableStateOf(p.getLong("lastBackup", 0L))
        private set
    fun updateThemeMode(v: Int) { themeMode = v; p.edit().putInt("theme", v).apply() }
    fun updateAccent(v: Int) { accent = v; p.edit().putInt("accent", v).apply() }
    fun updateAnimations(v: Boolean) { animations = v; p.edit().putBoolean("animations", v).apply() }
    fun updateHaptics(v: Boolean) { haptics = v; p.edit().putBoolean("haptics", v).apply() }
    fun updateContactsVcf(v: Boolean) { contactsVcf = v; p.edit().putBoolean("vcf", v).apply() }
    fun updateSkipDuplicates(v: Boolean) { skipDuplicates = v; p.edit().putBoolean("skipDup", v).apply() }
    fun updateSelectAllByDefault(v: Boolean) { selectAllByDefault = v; p.edit().putBoolean("selectAll", v).apply() }
    fun updateReminders(v: Boolean) { reminders = v; p.edit().putBoolean("reminders", v).apply() }
    fun markBackupNow() {
        lastBackup = System.currentTimeMillis()
        p.edit().putLong("lastBackup", lastBackup).apply()
    }
}
