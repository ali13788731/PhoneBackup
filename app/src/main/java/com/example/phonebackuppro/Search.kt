package com.example.phonebackuppro

/** Everything the advanced search / filter dialog can set (premium). Default = no filter. */
data class Adv(
    /** 0 name+number, 1 name only, 2 number only, 3 message text (SMS) / e-mail (contacts) */
    val scope: Int = 0,
    /** 0 default, 1 newest, 2 oldest, 3 الف تا ی, 4 ی تا الف, 5 A-Z, 6 Z-A, 7 most items, 8 fewest items */
    val sort: Int = 0,
    /** 0 all, 1 saved contacts only, 2 unknown numbers only */
    val source: Int = 0,
    /** 0 any, 1 last 7 days, 2 last 30, 3 last 90, 4 last year, 5 older than a year */
    val datePreset: Int = 0,
    val minCount: Int = 0,
    /** call types (1 incoming, 2 outgoing, 3 missed, 5 rejected) */
    val callTypes: Set<Int> = emptySet(),
    val minDurationSec: Long = 0L,
    /** 0 any, 1 has received, 2 has sent */
    val smsDir: Int = 0,
    val unreadOnly: Boolean = false,
    val hasEmail: Boolean = false,
    val multiNumber: Boolean = false
) {
    val activeCount: Int
        get() = listOf(
            scope != 0, sort != 0, source != 0, datePreset != 0, minCount > 0, callTypes.isNotEmpty(),
            minDurationSec > 0, smsDir != 0, unreadOnly, hasEmail, multiNumber
        ).count { it }
}

private const val DAY = 86_400_000L

/** A–Z sort: names that start with an English letter come first, everything else after them. */
private fun latinRank(t: String): Int = if (t.trim().firstOrNull()?.let { it in 'a'..'z' || it in 'A'..'Z' } == true) 0 else 1

/**
 * Filters + searches + sorts the rows of one section.
 * [hist] is the free "all / has history / zero history" chip (0, 1, 2).
 */
fun searchRows(
    rows: List<ItemRow>,
    section: Section,
    query: String,
    adv: Adv,
    hist: Int,
    smsOf: (ItemRow) -> List<SmsItem>,
    contactOf: (ItemRow) -> ContactItem?
): List<ItemRow> {
    val q = query.trim().toLatinDigits().lowercase()
    val qd = q.filter { it.isDigit() }
    val now = System.currentTimeMillis()
    val from = when (adv.datePreset) {
        1 -> now - 7 * DAY
        2 -> now - 30 * DAY
        3 -> now - 90 * DAY
        4 -> now - 365 * DAY
        else -> 0L
    }
    val older = now - 365 * DAY

    fun textOk(r: ItemRow): Boolean {
        if (q.isEmpty()) return true
        val byName = r.title.lowercase().contains(q)
        val byNumber = qd.length >= 2 &&
            (r.norms.any { it.contains(qd) } || r.subtitle.toLatinDigits().filter { c -> c.isDigit() }.contains(qd))
        return when (adv.scope) {
            1 -> byName
            2 -> byNumber
            3 -> when (section) {
                Section.SMS -> smsOf(r).any { it.body.lowercase().contains(q) }
                Section.CONTACTS -> contactOf(r)?.emails?.any { it.lowercase().contains(q) } == true
                else -> false
            }
            else -> byName || byNumber || r.subtitle.lowercase().contains(q)
        }
    }

    fun ok(r: ItemRow): Boolean {
        if (section != Section.CONTACTS) {
            if (hist == 1 && r.count == 0) return false
            if (hist == 2 && r.count > 0) return false
        }
        if (adv.source == 1 && r.isUnknown) return false
        if (adv.source == 2 && !r.isUnknown) return false
        if (adv.datePreset in 1..4 && r.lastDate < from) return false
        if (adv.datePreset == 5 && !(r.lastDate in 1 until older)) return false
        if (adv.minCount > 0 && r.count < adv.minCount) return false
        when (section) {
            Section.CALLS -> {
                if (adv.callTypes.isNotEmpty() && adv.callTypes.none { (r.flags and (1 shl it)) != 0 }) return false
                if (adv.minDurationSec > 0 && r.maxValue < adv.minDurationSec) return false
            }
            Section.SMS -> {
                if (adv.smsDir == 1 && (r.flags and 1) == 0) return false
                if (adv.smsDir == 2 && (r.flags and 2) == 0) return false
                if (adv.unreadOnly && (r.flags and 4) == 0) return false
            }
            Section.CONTACTS -> {
                if (adv.hasEmail && (r.flags and 1) == 0) return false
                if (adv.multiNumber && (r.flags and 2) == 0) return false
            }
        }
        return textOk(r)
    }

    val out = rows.filter { ok(it) }
    return when (adv.sort) {
        1 -> out.sortedByDescending { it.lastDate }
        2 -> out.sortedBy { if (it.lastDate == 0L) Long.MAX_VALUE else it.lastDate }
        3 -> out.sortedWith(compareBy<ItemRow>({ faGroup(it.title) }, { faSortKey(it.title) }))
        4 -> out.sortedWith(compareBy<ItemRow> { faGroup(it.title) }.thenByDescending { faSortKey(it.title) })
        5 -> out.sortedWith(compareBy<ItemRow>({ latinRank(it.title) }, { it.title.lowercase() }))
        6 -> out.sortedWith(compareByDescending<ItemRow> { latinRank(it.title) == 0 }.thenByDescending { it.title.lowercase() })
        7 -> out.sortedByDescending { it.count }
        8 -> out.sortedBy { it.count }
        else -> out
    }
}
