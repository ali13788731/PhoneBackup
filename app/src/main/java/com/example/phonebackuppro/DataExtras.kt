package com.example.phonebackuppro

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Identifies "one person" in calls / messages: the normalised number (or the raw text when it has no digits). */
fun personKey(number: String): String = normNumber(number).ifEmpty { number.trim() }

/** One person (or one contact) with how many records belong to him / her. */
data class PersonGroup(val key: String, val title: String, val sub: String, val count: Int)

object People {

    fun groups(d: BackupData, s: Section): List<PersonGroup> = when (s) {
        Section.CONTACTS -> d.contacts.mapIndexed { i, c ->
            PersonGroup(
                "c$i",
                c.name.ifBlank { c.phones.firstOrNull() ?: "بدون نام" },
                c.phones.firstOrNull() ?: c.emails.firstOrNull() ?: "",
                1
            )
        }
        Section.CALLS -> byNumber(d, d.calls, { it.number }, { it.name })
        Section.SMS -> byNumber(d, d.sms, { it.address }, { "" })
    }

    private fun <T> byNumber(
        d: BackupData, list: List<T>, numberOf: (T) -> String, nameOf: (T) -> String
    ): List<PersonGroup> {
        val names = HashMap<String, String>()
        for (c in d.contacts) {
            if (c.name.isBlank()) continue
            for (p in c.phones) names.putIfAbsent(personKey(p), c.name)
        }
        val counts = LinkedHashMap<String, Int>()
        val numbers = HashMap<String, String>()
        val cached = HashMap<String, String>()
        for (item in list) {
            val n = numberOf(item)
            val k = personKey(n)
            counts[k] = (counts[k] ?: 0) + 1
            numbers.putIfAbsent(k, n)
            val nm = nameOf(item)
            if (nm.isNotBlank()) cached.putIfAbsent(k, nm)
        }
        return counts.map { (k, c) ->
            val num = numbers[k].orEmpty()
            PersonGroup(k, names[k] ?: cached[k] ?: num.ifBlank { "ناشناس" }, num, c)
        }
    }
}

/** How many different people the section of this data belongs to. */
fun BackupData.people(s: Section): Int = when (s) {
    Section.CONTACTS -> contacts.size
    Section.CALLS -> calls.mapTo(HashSet<String>()) { personKey(it.number) }.size
    Section.SMS -> sms.mapTo(HashSet<String>()) { personKey(it.address) }.size
}

// ---------------------------------------------------------------------------------------------
//  vCard (.vcf) reader: lets contact files from any phone / Telegram be restored too
// ---------------------------------------------------------------------------------------------

object Vcf {

    fun looksLikeVcf(text: String): Boolean =
        text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("BEGIN:VCARD", ignoreCase = true)

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                sb.append(if (n == 'n' || n == 'N') '\n' else n)
                i += 2
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }

    private fun decodeQp(value: String, charset: Charset): String {
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '=' && i + 2 < value.length) {
                val h = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (h != null) { out.write(h); i += 3; continue }
            }
            out.write(ch.code and 0xFF)
            i++
        }
        return String(out.toByteArray(), charset)
    }

    private fun charsetOf(params: String): Charset {
        val m = Regex("CHARSET=([A-Z0-9_\\-]+)").find(params)
        return try { if (m != null) Charset.forName(m.groupValues[1]) else Charsets.UTF_8 } catch (e: Exception) { Charsets.UTF_8 }
    }

    fun read(text: String): ParsedBackup {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // 1) unfold lines that continue on the next line (start with space / tab)
        val unfolded = ArrayList<String>(raw.size)
        for (l in raw) {
            if (l.isNotEmpty() && (l[0] == ' ' || l[0] == '\t') && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded[unfolded.lastIndex] + l.substring(1)
            } else unfolded.add(l)
        }
        // 2) quoted-printable soft line breaks
        val lines = ArrayList<String>(unfolded.size)
        var i = 0
        while (i < unfolded.size) {
            var l = unfolded[i]
            if (l.contains("QUOTED-PRINTABLE", ignoreCase = true)) {
                while (l.endsWith("=") && i + 1 < unfolded.size) { i++; l = l.dropLast(1) + unfolded[i] }
            }
            lines.add(l); i++
        }

        val rev = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val out = ArrayList<ContactItem>()
        var inCard = false
        var fn = ""
        var nParts: List<String> = emptyList()
        var phones = ArrayList<String>()
        var emails = ArrayList<String>()
        var date = 0L

        for (line in lines) {
            val t = line.trim()
            if (t.equals("BEGIN:VCARD", ignoreCase = true)) {
                inCard = true; fn = ""; nParts = emptyList(); phones = ArrayList(); emails = ArrayList(); date = 0L
                continue
            }
            if (t.equals("END:VCARD", ignoreCase = true)) {
                if (inCard) {
                    val fromN = nParts.map { unescape(it).trim() }.filter { it.isNotEmpty() }
                        .let { if (it.size >= 2) listOf(it[1], it[0]) + it.drop(2) else it }
                        .joinToString(" ")
                    val name = fn.ifBlank { fromN }
                    if (name.isNotBlank() || phones.isNotEmpty() || emails.isNotEmpty()) {
                        out.add(ContactItem((out.size + 1).toLong(), name, phones.toList(), emails.toList(), date))
                    }
                }
                inCard = false
                continue
            }
            if (!inCard) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val parts = head.split(';')
            val prop = parts[0].substringAfterLast('.').uppercase()
            val params = parts.drop(1).joinToString(";").uppercase()
            val qp = params.contains("QUOTED-PRINTABLE")
            if (qp) value = decodeQp(value, charsetOf(params))
            when (prop) {
                "FN" -> fn = unescape(value).trim()
                "N" -> nParts = value.split(';')
                "TEL" -> {
                    val v = unescape(value).trim()
                    if (v.isNotEmpty() && phones.none { normNumber(it) == normNumber(v) && normNumber(v).isNotEmpty() }) phones.add(v)
                }
                "EMAIL" -> {
                    val v = unescape(value).trim()
                    if (v.isNotEmpty() && v !in emails) emails.add(v)
                }
                "REV" -> date = try { rev.parse(value.trim())?.time ?: 0L } catch (e: Exception) { 0L }
            }
        }
        return ParsedBackup(
            BackupData(contacts = out),
            if (out.isEmpty()) emptySet() else setOf(Section.CONTACTS),
            0L
        )
    }
}
