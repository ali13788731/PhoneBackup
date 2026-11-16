package com.example.phonebackuppro

import android.os.Build
import android.util.JsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The three independent kinds of data the app handles. */
enum class Section { CONTACTS, CALLS, SMS }

/** [date] = last-updated time of the contact (milliseconds), 0 when unknown. */
data class ContactItem(
    val id: Long, val name: String, val phones: List<String>, val emails: List<String>,
    val date: Long = 0L
)

data class SmsItem(val address: String, val body: String, val date: Long, val type: Int, val read: Int)
data class CallItem(val number: String, val name: String, val date: Long, val duration: Long, val type: Int)

data class BackupData(
    val contacts: List<ContactItem> = emptyList(),
    val sms: List<SmsItem> = emptyList(),
    val calls: List<CallItem> = emptyList()
) {
    fun count(s: Section) = when (s) {
        Section.CONTACTS -> contacts.size
        Section.CALLS -> calls.size
        Section.SMS -> sms.size
    }
}

data class ImportResult(
    val contactsAdded: Int = 0, val contactsSkipped: Int = 0,
    val callsAdded: Int = 0, val callsSkipped: Int = 0,
    val smsAdded: Int = 0, val smsSkipped: Int = 0,
    val failed: Int = 0,
    val smsNeedDefaultRole: Boolean = false,
    val sections: Set<Section> = emptySet(),
    val reports: List<SectionReport> = emptyList()
)

/** Result of reading a backup file: what is inside and which sections it really contains. */
data class ParsedBackup(val data: BackupData, val present: Set<Section>, val createdAt: Long)

/** Last 9 digits: matches +98912..., 0912... and 912... as the same number. */
fun normNumber(n: String): String = n.filter { it.isDigit() }.takeLast(9)

// ---------------------------------------------------------------------------------------------
//  File format
// ---------------------------------------------------------------------------------------------

object Codec {
    const val FORMAT = "PhoneBackupPro"
    const val VERSION = 2

    private fun strs(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    /** Streams the backup to [out] as JSON. [onItem] is called once per written record. */
    fun writeJson(out: OutputStream, d: BackupData, sections: Set<Section>, onItem: () -> Unit) {
        val w = JsonWriter(BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), 1 shl 16))
        w.beginObject()
        w.name("format").value(FORMAT)
        w.name("version").value(VERSION.toLong())
        w.name("createdAt").value(System.currentTimeMillis())
        w.name("device").value(Build.MODEL ?: "")
        if (Section.CONTACTS in sections) {
            w.name("contacts").beginArray()
            for (c in d.contacts) {
                w.beginObject()
                w.name("id").value(c.id)
                w.name("name").value(c.name)
                w.name("date").value(c.date)
                w.name("phones").beginArray(); c.phones.forEach { w.value(it) }; w.endArray()
                w.name("emails").beginArray(); c.emails.forEach { w.value(it) }; w.endArray()
                w.endObject()
                onItem()
            }
            w.endArray()
        }
        if (Section.CALLS in sections) {
            w.name("calls").beginArray()
            for (c in d.calls) {
                w.beginObject()
                w.name("number").value(c.number)
                w.name("name").value(c.name)
                w.name("date").value(c.date)
                w.name("duration").value(c.duration)
                w.name("type").value(c.type.toLong())
                w.endObject()
                onItem()
            }
            w.endArray()
        }
        if (Section.SMS in sections) {
            w.name("sms").beginArray()
            for (s in d.sms) {
                w.beginObject()
                w.name("address").value(s.address)
                w.name("body").value(s.body)
                w.name("date").value(s.date)
                w.name("type").value(s.type.toLong())
                w.name("read").value(s.read.toLong())
                w.endObject()
                onItem()
            }
            w.endArray()
        }
        w.endObject()
        w.flush()
    }

    /** Contacts as a vCard (.vcf) file: opens in any phone / address-book app. */
    fun writeVcf(out: OutputStream, contacts: List<ContactItem>, onItem: () -> Unit) {
        val w = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), 1 shl 16)
        val rev = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
        for (c in contacts) {
            val name = c.name.ifBlank { c.phones.firstOrNull() ?: "?" }
            w.write("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            w.write("FN:${esc(name)}\r\n")
            w.write("N:${esc(name)};;;;\r\n")
            c.phones.forEach { w.write("TEL;TYPE=CELL:${esc(it)}\r\n") }
            c.emails.forEach { w.write("EMAIL;TYPE=HOME:${esc(it)}\r\n") }
            if (c.date > 0) w.write("REV:${rev.format(Date(c.date))}\r\n")
            w.write("END:VCARD\r\n")
            onItem()
        }
        w.flush()
    }

    /** Throws if the text is not a PhoneBackupPro file. Sections that are not in the file stay empty. */
    fun read(text: String): ParsedBackup {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not a PhoneBackupPro file" }
        val present = mutableSetOf<Section>()

        val contacts = root.optJSONArray("contacts")?.let { a ->
            present += Section.CONTACTS
            List(a.length()) {
                val o = a.getJSONObject(it)
                ContactItem(
                    o.optLong("id"), o.optString("name"),
                    strs(o.optJSONArray("phones")), strs(o.optJSONArray("emails")),
                    o.optLong("date", 0L)
                )
            }
        } ?: emptyList()

        val sms = root.optJSONArray("sms")?.let { a ->
            present += Section.SMS
            List(a.length()) {
                val o = a.getJSONObject(it)
                SmsItem(
                    o.optString("address"), o.optString("body"), o.getLong("date"),
                    o.optInt("type", 1), o.optInt("read", 1)
                )
            }
        } ?: emptyList()

        val calls = root.optJSONArray("calls")?.let { a ->
            present += Section.CALLS
            List(a.length()) {
                val o = a.getJSONObject(it)
                CallItem(
                    o.optString("number"), o.optString("name"), o.getLong("date"),
                    o.optLong("duration"), o.optInt("type", 1)
                )
            }
        } ?: emptyList()

        return ParsedBackup(BackupData(contacts, sms, calls), present, root.optLong("createdAt", 0L))
    }
}

// ---------------------------------------------------------------------------------------------
//  Rows shown in the lists
// ---------------------------------------------------------------------------------------------

/**
 * One line in a list. For the contacts list it is a contact; for calls / messages it is a person
 * (a saved contact, or an unknown number) together with the history that belongs to him / her.
 * A person with [count] == 0 has no history at all ("zero" in the filter).
 */
data class ItemRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val lastDate: Long,
    val norms: Set<String>,
    val preview: String = "",
    val isUnknown: Boolean = false,
    /** Contacts: bit0 = has e-mail, bit1 = several numbers. Calls: bit(type). Messages: bit0 received, bit1 sent, bit2 unread. */
    val flags: Int = 0,
    /** Calls: longest call in seconds. */
    val maxValue: Long = 0L
)

object Rows {

    fun contacts(d: BackupData): List<ItemRow> =
        d.contacts.map { c ->
            ItemRow(
                key = "c${c.id}",
                title = c.name.ifBlank { c.phones.firstOrNull() ?: "بدون نام" },
                subtitle = c.phones.firstOrNull() ?: c.emails.firstOrNull() ?: "",
                count = c.phones.size,
                lastDate = c.date,
                norms = c.phones.map(::normNumber).filter { it.isNotEmpty() }.toSet(),
                flags = (if (c.emails.isNotEmpty()) 1 else 0) or (if (c.phones.size > 1) 2 else 0)
            )
        }.sortedWith(compareBy<ItemRow>({ faGroup(it.title) }, { faSortKey(it.title) }))

    /**
     * Generic builder for calls and messages: every saved contact gets a row (even with zero history)
     * and every number that is not saved gets its own row.
     */
    fun <T> people(
        prefix: String,
        contacts: List<ContactItem>,
        index: Map<String, List<T>>,
        dateOf: (T) -> Long,
        nameOf: (T) -> String,
        numberOf: (T) -> String,
        previewOf: (T) -> String,
        flagsOf: (T) -> Int,
        valueOf: (T) -> Long
    ): List<ItemRow> {
        val out = ArrayList<ItemRow>(contacts.size + 64)
        val covered = HashSet<String>()
        for (c in contacts) {
            val norms = c.phones.map(::normNumber).filter { it.isNotEmpty() }.toSet()
            if (norms.isEmpty()) continue
            covered += norms
            var count = 0
            var last: T? = null
            var lastDate = 0L
            var flags = 0
            var maxV = 0L
            for (n in norms) {
                val l = index[n] ?: continue
                count += l.size
                for (item in l) {
                    flags = flags or flagsOf(item)
                    maxV = maxOf(maxV, valueOf(item))
                    val dt = dateOf(item)
                    if (last == null || dt > lastDate) { last = item; lastDate = dt }
                }
            }
            out += ItemRow(
                key = "$prefix${c.id}",
                title = c.name.ifBlank { c.phones.first() },
                subtitle = c.phones.first(),
                count = count,
                lastDate = lastDate,
                norms = norms,
                preview = last?.let { previewOf(it) } ?: "",
                flags = flags,
                maxValue = maxV
            )
        }
        for ((norm, list) in index) {
            if (norm in covered) continue
            val last = list.maxByOrNull(dateOf) ?: continue
            val number = numberOf(last)
            val name = nameOf(last)
            var flags = 0
            var maxV = 0L
            for (item in list) { flags = flags or flagsOf(item); maxV = maxOf(maxV, valueOf(item)) }
            out += ItemRow(
                key = "${prefix}n$norm",
                title = name.ifBlank { number.ifBlank { "ناشناس" } },
                subtitle = number,
                count = list.size,
                lastDate = dateOf(last),
                norms = setOf(norm),
                preview = previewOf(last),
                isUnknown = true,
                flags = flags,
                maxValue = maxV
            )
        }
        return out.sortedWith(
            compareByDescending<ItemRow> { it.lastDate }.thenBy { faGroup(it.title) }.thenBy { faSortKey(it.title) }
        )
    }

    fun calls(d: BackupData, index: Map<String, List<CallItem>>) =
        people(
            "k", d.contacts, index, { it.date }, { it.name }, { it.number }, { "" },
            { 1 shl it.type.coerceIn(0, 30) }, { it.duration }
        )

    fun sms(d: BackupData, index: Map<String, List<SmsItem>>) =
        people(
            "s", d.contacts, index, { it.date }, { "" }, { it.address }, { it.body },
            {
                (if (it.type == 1) 1 else 0) or (if (it.type == 2) 2 else 0) or
                    (if (it.type == 1 && it.read == 0) 4 else 0)
            },
            { 0L }
        )

    fun callIndex(d: BackupData): Map<String, List<CallItem>> = d.calls.groupBy { normNumber(it.number) }
    fun smsIndex(d: BackupData): Map<String, List<SmsItem>> = d.sms.groupBy { normNumber(it.address) }
}
