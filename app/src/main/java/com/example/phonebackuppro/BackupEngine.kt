package com.example.phonebackuppro

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** (fraction 0..1, human readable stage). Fraction < 0 means "unknown / indeterminate". */
typealias Report = (Float, String) -> Unit

data class BackupFile(val uri: Uri, val name: String, val size: Long, val date: Long)
data class SavedFile(val location: String, val uri: Uri?)

object BackupEngine {

    // ---------------------------------------------------------------------------------------
    //  Reading from the phone
    // ---------------------------------------------------------------------------------------

    fun readAll(ctx: Context, report: Report = { _, _ -> }): BackupData {
        val contacts = readContacts(ctx, report, 0f, 0.25f)
        val calls = readCalls(ctx, report, 0.25f, 0.15f)
        val sms = readSms(ctx, report, 0.40f, 0.60f)
        report(1f, "تمام شد")
        return BackupData(contacts, sms, calls)
    }

    private inline fun Cursor.forEachStep(
        base: Float, span: Float, stage: String, report: Report, body: (Cursor) -> Unit
    ) {
        val total = count.coerceAtLeast(1)
        var i = 0
        while (moveToNext()) {
            body(this)
            i++
            if (i % 250 == 0) report(base + span * i / total, stage)
        }
        report(base + span, stage)
    }

    fun readContacts(
        ctx: Context, report: Report = { _, _ -> }, base: Float = 0f, span: Float = 1f
    ): List<ContactItem> {
        val cr = ctx.contentResolver
        val names = linkedMapOf<Long, String>()
        val dates = HashMap<Long, Long>()
        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        val stage = "در حال خواندن مخاطبین…"

        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            ), null, null, ContactsContract.Contacts.DISPLAY_NAME
        )?.use { c ->
            c.forEachStep(base, span * 0.3f, stage, report) {
                val id = it.getLong(0)
                names[id] = it.getString(1) ?: ""
                dates[id] = it.getLong(2)
            }
        }
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null
        )?.use { c ->
            c.forEachStep(base + span * 0.3f, span * 0.45f, stage, report) {
                val id = it.getLong(0)
                val n = it.getString(1)
                if (n != null && id in names) {
                    val list = phones.getOrPut(id) { mutableListOf() }
                    if (list.none { p -> normNumber(p) == normNumber(n) }) list.add(n)
                }
            }
        }
        cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ), null, null, null
        )?.use { c ->
            c.forEachStep(base + span * 0.75f, span * 0.25f, stage, report) {
                val id = it.getLong(0)
                val e = it.getString(1)
                if (e != null && id in names) {
                    val list = emails.getOrPut(id) { mutableListOf() }
                    if (e !in list) list.add(e)
                }
            }
        }
        return names.map { (id, name) ->
            ContactItem(id, name, phones[id].orEmpty(), emails[id].orEmpty(), dates[id] ?: 0L)
        }
    }

    fun readSms(
        ctx: Context, report: Report = { _, _ -> }, base: Float = 0f, span: Float = 1f
    ): List<SmsItem> {
        val out = ArrayList<SmsItem>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                Telephony.Sms.TYPE, Telephony.Sms.READ
            ), null, null, Telephony.Sms.DATE
        )?.use { c ->
            c.forEachStep(base, span, "در حال خواندن پیامک‌ها…", report) {
                out.add(
                    SmsItem(
                        it.getString(0) ?: "", it.getString(1) ?: "",
                        it.getLong(2), it.getInt(3), it.getInt(4)
                    )
                )
            }
        }
        return out
    }

    fun readCalls(
        ctx: Context, report: Report = { _, _ -> }, base: Float = 0f, span: Float = 1f
    ): List<CallItem> {
        val out = ArrayList<CallItem>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE,
                CallLog.Calls.DURATION, CallLog.Calls.TYPE
            ), null, null, CallLog.Calls.DATE
        )?.use { c ->
            c.forEachStep(base, span, "در حال خواندن تماس‌ها…", report) {
                out.add(
                    CallItem(
                        it.getString(0) ?: "", it.getString(1) ?: "",
                        it.getLong(2), it.getLong(3), it.getInt(4)
                    )
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------
    //  Writing / saving
    // ---------------------------------------------------------------------------------------

    fun fileName(label: String, vcf: Boolean = false): String =
        "PhoneBackupPro_${label}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            if (vcf) ".vcf" else ".json"

    /**
     * Writes [d] into [out]. Progress is reported per record; [range] is the part of the whole
     * progress bar this write occupies (so reading + writing can share one bar).
     */
    fun write(
        out: OutputStream, d: BackupData, sections: Set<Section>, vcf: Boolean,
        report: Report, from: Float = 0f, to: Float = 1f
    ) {
        val total = (sections.sumOf { d.count(it) }).coerceAtLeast(1)
        var done = 0
        val tick = {
            done++
            if (done % 100 == 0) report(from + (to - from) * done / total, "در حال نوشتن فایل…")
        }
        if (vcf) Codec.writeVcf(out, d.contacts, tick) else Codec.writeJson(out, d, sections, tick)
        report(to, "در حال نوشتن فایل…")
    }

    /** Free-plan destination: Downloads/PhoneBackupPro. */
    fun saveToDownloads(
        ctx: Context, name: String, mime: String, writer: (OutputStream) -> Unit
    ): SavedFile {
        val rel = Environment.DIRECTORY_DOWNLOADS + "/" + Config.BACKUP_FOLDER
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val cr = ctx.contentResolver
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("cannot create file")
        try {
            (cr.openOutputStream(uri) ?: error("cannot open file")).use(writer)
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            cr.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { cr.delete(uri, null, null) }
            throw e
        }
        return SavedFile("$rel/$name", uri)
    }

    /** Paid-plan destination: any place the user picked in the system file picker. */
    fun saveToUri(ctx: Context, uri: Uri, writer: (OutputStream) -> Unit): SavedFile {
        (ctx.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open file")).use(writer)
        return SavedFile("مکانی که خودتان انتخاب کردید", uri)
    }

    // ---------------------------------------------------------------------------------------
    //  Backups saved by this app in Downloads/PhoneBackupPro
    // ---------------------------------------------------------------------------------------

    fun listBackups(ctx: Context): List<BackupFile> {
        val out = ArrayList<BackupFile>()
        val rel = Environment.DIRECTORY_DOWNLOADS + "/" + Config.BACKUP_FOLDER
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED
            ),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$rel%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.startsWith("PhoneBackupPro_")) continue
                out.add(
                    BackupFile(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)),
                        name, c.getLong(2), c.getLong(3) * 1000L
                    )
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------
    //  Importing
    // ---------------------------------------------------------------------------------------

    fun isDefaultSmsApp(ctx: Context) = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    /** Same record = same moment, same person, same direction. Used to recognise what is already on the phone. */
    private fun callKey(k: CallItem) = "${k.date}|${personKey(k.number)}|${k.type}"
    private fun smsKey(m: SmsItem) = "${m.date}|${personKey(m.address)}|${m.type}"

    /**
     * Restores [d]. With [skipDuplicates] every record that is already on the phone is skipped and
     * only the really new ones are written, so importing the same (or a newer, bigger) file again
     * adds only the difference. The returned [ImportResult.reports] says, person by person, what
     * was added and what was a duplicate.
     */
    fun import(
        ctx: Context, d: BackupData, sections: Set<Section>, skipDuplicates: Boolean, report: Report
    ): ImportResult {
        val cr = ctx.contentResolver
        val total = sections.sumOf { d.count(it) }.coerceAtLeast(1)
        var done = 0
        fun step(n: Int, stage: String) {
            done += n
            report((done.toFloat() / total).coerceAtMost(1f), stage)
        }

        var cAdd = 0; var cSkip = 0; var cFail = 0
        var kAdd = 0; var kSkip = 0; var kFail = 0
        var sAdd = 0; var sSkip = 0; var sFail = 0
        var failed = 0
        val reports = ArrayList<SectionReport>()

        // ---- contacts -------------------------------------------------------------------
        if (Section.CONTACTS in sections && d.contacts.isNotEmpty()) {
            val stage = "در حال بازیابی مخاطبین…"
            val tally = Tally()
            val have = readContacts(ctx)
            val existing = HashSet<String>()
            for (h in have) for (ph in h.phones) {
                val n = normNumber(ph)
                if (n.isNotEmpty()) existing.add(n)
            }
            val existingNames = HashSet<String>()
            for (h in have) existingNames.add(h.name)
            val ops = ArrayList<ContentProviderOperation>()
            val pendingKeys = ArrayList<String>()
            fun flush() {
                if (ops.isEmpty()) return
                try {
                    cr.applyBatch(ContactsContract.AUTHORITY, ops)
                    cAdd += pendingKeys.size
                } catch (e: Exception) {
                    cFail += pendingKeys.size
                    pendingKeys.forEach { tally.fail(it) }
                }
                ops.clear(); pendingKeys.clear()
            }
            for ((idx, c) in d.contacts.withIndex()) {
                val key = "c$idx"
                val title = c.name.ifBlank { c.phones.firstOrNull() ?: "بدون نام" }
                val sub = c.phones.firstOrNull() ?: c.emails.firstOrNull() ?: ""
                val norms = c.phones.map(::normNumber)
                // numbers of this contact that are not on the phone yet
                val fresh = c.phones.filterIndexed { i, _ -> norms[i].isEmpty() || norms[i] !in existing }
                val dup = if (c.phones.isNotEmpty()) fresh.isEmpty()
                else c.name.isNotBlank() && c.name in existingNames
                if (skipDuplicates && dup) {
                    cSkip++; tally.skip(key, title, sub); step(1, stage); continue
                }
                val use = if (skipDuplicates) fresh else c.phones
                val subText = if (use.size < c.phones.size) "فقط ${use.size.fa()} شماره‌ی جدید اضافه شد" else sub

                val raw = ops.size
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
                        .build()
                )
                use.forEach { ph ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, ph)
                            .withValue(
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            ).build()
                    )
                }
                c.emails.forEach { e ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                            )
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, e)
                            .withValue(
                                ContactsContract.CommonDataKinds.Email.TYPE,
                                ContactsContract.CommonDataKinds.Email.TYPE_HOME
                            ).build()
                    )
                }
                pendingKeys.add(key)
                tally.done(key, title, subText)
                for (n in norms) if (n.isNotEmpty()) existing.add(n)
                if (c.name.isNotBlank()) existingNames.add(c.name)
                step(1, stage)
                if (ops.size >= 300) flush()
            }
            flush()
            failed += cFail
            reports += SectionReport(
                Section.CONTACTS, d.contacts.size, cAdd, cSkip, cFail, d.contacts.size, items = tally.items()
            )
        }

        // ---- calls ----------------------------------------------------------------------
        if (Section.CALLS in sections && d.calls.isNotEmpty()) {
            val stage = "در حال بازیابی تماس‌ها…"
            val tally = Tally()
            val groups = People.groups(d, Section.CALLS).associateBy { it.key }
            // how many records of each kind the phone already holds (a multiset)
            val have = HashMap<String, Int>()
            for (k in readCalls(ctx)) { val key = callKey(k); have[key] = (have[key] ?: 0) + 1 }
            val batch = ArrayList<ContentValues>(500)
            val pending = ArrayList<String>()
            fun flush() {
                if (batch.isEmpty()) return
                try {
                    cr.bulkInsert(CallLog.Calls.CONTENT_URI, batch.toTypedArray())
                    kAdd += batch.size
                } catch (e: Exception) {
                    kFail += batch.size
                    pending.forEach { tally.fail(it) }
                }
                batch.clear(); pending.clear()
            }
            for (k in d.calls) {
                val pk = personKey(k.number)
                val g = groups[pk]
                val title = g?.title ?: k.number.ifBlank { "ناشناس" }
                val sub = g?.sub ?: k.number
                if (skipDuplicates) {
                    val key = callKey(k)
                    val left = have[key] ?: 0
                    if (left > 0) {
                        have[key] = left - 1
                        kSkip++; tally.skip(pk, title, sub); step(1, stage); continue
                    }
                }
                batch.add(ContentValues().apply {
                    put(CallLog.Calls.NUMBER, k.number)
                    put(CallLog.Calls.CACHED_NAME, k.name)
                    put(CallLog.Calls.DATE, k.date)
                    put(CallLog.Calls.DURATION, k.duration)
                    put(CallLog.Calls.TYPE, k.type)
                    put(CallLog.Calls.NEW, 0)
                    put(CallLog.Calls.IS_READ, 1)
                })
                pending.add(pk)
                tally.done(pk, title, sub)
                step(1, stage)
                if (batch.size >= 500) flush()
            }
            flush()
            failed += kFail
            reports += SectionReport(
                Section.CALLS, d.calls.size, kAdd, kSkip, kFail, groups.size, items = tally.items()
            )
        }

        // ---- sms: only possible while this app holds the default-SMS role -------------------
        var needRole = false
        if (Section.SMS in sections && d.sms.isNotEmpty()) {
            val groups = People.groups(d, Section.SMS).associateBy { it.key }
            if (!isDefaultSmsApp(ctx)) {
                needRole = true
                step(d.sms.size, "پیامک‌ها: برنامه پیش‌فرض پیامک نیست")
                reports += SectionReport(
                    Section.SMS, d.sms.size, 0, 0, 0, groups.size,
                    note = "وارد نشد: این برنامه در آن لحظه «پیش‌فرض پیامک» نبود."
                )
            } else {
                val stage = "در حال بازیابی پیامک‌ها…"
                val tally = Tally()
                val have = HashMap<String, Int>()
                for (m in readSms(ctx)) { val key = smsKey(m); have[key] = (have[key] ?: 0) + 1 }
                val batch = ArrayList<ContentValues>(300)
                val pending = ArrayList<String>()
                fun flush() {
                    if (batch.isEmpty()) return
                    try {
                        cr.bulkInsert(Telephony.Sms.CONTENT_URI, batch.toTypedArray())
                        sAdd += batch.size
                    } catch (e: Exception) {
                        sFail += batch.size
                        pending.forEach { tally.fail(it) }
                    }
                    batch.clear(); pending.clear()
                }
                for (m in d.sms) {
                    val pk = personKey(m.address)
                    val g = groups[pk]
                    val title = g?.title ?: m.address.ifBlank { "ناشناس" }
                    val sub = g?.sub ?: m.address
                    if (skipDuplicates) {
                        val key = smsKey(m)
                        val left = have[key] ?: 0
                        if (left > 0) {
                            have[key] = left - 1
                            sSkip++; tally.skip(pk, title, sub); step(1, stage); continue
                        }
                    }
                    batch.add(ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, m.address)
                        put(Telephony.Sms.BODY, m.body)
                        put(Telephony.Sms.DATE, m.date)
                        put(Telephony.Sms.DATE_SENT, m.date)
                        put(Telephony.Sms.TYPE, m.type)
                        put(Telephony.Sms.READ, m.read)
                        put(Telephony.Sms.SEEN, 1)
                    })
                    pending.add(pk)
                    tally.done(pk, title, sub)
                    step(1, stage)
                    if (batch.size >= 300) flush()
                }
                flush()
                failed += sFail
                reports += SectionReport(
                    Section.SMS, d.sms.size, sAdd, sSkip, sFail, groups.size, items = tally.items()
                )
            }
        }
        report(1f, "تمام شد")
        return ImportResult(
            cAdd, cSkip, kAdd, kSkip, sAdd, sSkip, failed, needRole, sections, reports
        )
    }
}

/** Counts the bytes that pass through, so the report can show the size of the saved file. */
class CountingOutputStream(private val inner: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) { inner.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { inner.write(b, off, len); count += len }
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}
