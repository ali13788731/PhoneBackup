package com.example.phonebackuppro

import java.util.Calendar
import java.util.Locale

private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** Latin digits -> Persian digits. */
fun String.toFaDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) sb.append(if (ch in '0'..'9') FA_DIGITS[ch - '0'] else ch)
    return sb.toString()
}

/** Persian / Arabic-Indic digits -> Latin digits (for search boxes). */
fun String.toLatinDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        sb.append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** 12345 -> ۱۲٬۳۴۵ */
fun Int.fa(): String = String.format(Locale.US, "%,d", this).replace(",", "٬").toFaDigits()

/** Gregorian -> Jalali (validated against jdatetime for 2000..2040). Returns y, m, d. */
fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31; jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30
    }
    return intArrayOf(jy, jm, jd)
}

/** ۱۴۰۵/۰۸/۱۷, or "—" when the date is unknown. */
fun formatDate(ms: Long): String {
    if (ms <= 0L) return "—"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val j = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]).toFaDigits()
}

/** ۱۴۰۵/۰۸/۱۷  ۱۸:۵۸ */
fun formatDateTime(ms: Long): String {
    if (ms <= 0L) return "—"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val time = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)).toFaDigits()
    return "${formatDate(ms)}  $time"
}

/** Call duration in seconds -> ۰۲:۳۴ */
fun formatDuration(sec: Long): String {
    if (sec <= 0) return "۰۰:۰۰"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val t = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
    return t.toFaDigits()
}

fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val t = if (kb < 1024) String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    else String.format(Locale.US, "%.1f MB", kb / 1024.0)
    return t.toFaDigits().replace("KB", "کیلوبایت").replace("MB", "مگابایت")
}

// ---- Persian alphabetical order (الف ... ی) ------------------------------------------------------

private const val FA_ORDER = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"

/** Arabic look-alikes and hamza forms folded into the Persian letter they sort as. */
private fun foldFa(c: Char): Char = when (c) {
    'آ', 'أ', 'إ', 'ٱ' -> 'ا'
    'ي', 'ئ', 'ى' -> 'ی'
    'ك' -> 'ک'
    'ة', 'ۀ' -> 'ه'
    'ؤ' -> 'و'
    else -> c
}

/**
 * Sort key: Persian letters first (in alphabet order), then English letters, then digits/symbols.
 * Leading spaces/punctuation are ignored; ZWNJ and diacritics are dropped.
 */
fun faSortKey(title: String): String {
    val sb = StringBuilder()
    for (raw in title.trim()) {
        val c = foldFa(raw)
        val i = FA_ORDER.indexOf(c)
        when {
            i >= 0 -> sb.append('\u0100' + i)
            c in 'a'..'z' -> sb.append('\u0200' + (c - 'a'))
            c in 'A'..'Z' -> sb.append('\u0200' + (c - 'A'))
            c.isDigit() -> sb.append('\u0300' + (c.digitToIntOrNull() ?: 0))
            c == '\u200c' || c == ' ' -> if (sb.isNotEmpty()) sb.append('\u00ff')
            c in '\u064b'..'\u065f' -> {}   // diacritics
            else -> if (sb.isNotEmpty()) sb.append('\u0400')
        }
    }
    return sb.toString()
}

/** 0 = starts with a Persian letter, 1 = English letter, 2 = anything else. */
fun faGroup(title: String): Int {
    val c = title.trim().firstOrNull { it.isLetterOrDigit() }?.let(::foldFa) ?: return 2
    return when {
        FA_ORDER.indexOf(c) >= 0 -> 0
        c in 'a'..'z' || c in 'A'..'Z' -> 1
        else -> 2
    }
}
