package com.example.phonebackuppro

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max

/** What to export. [keys] = selected row keys of the single section, null = everything. */
class ExportRequest(
    val sections: Set<Section>,
    val keys: Set<String>? = null,
    val refresh: Boolean = false
)

sealed interface Target {
    data object Downloads : Target
    data class Custom(val uri: Uri) : Target
}

val Section.title: String
    get() = when (this) {
        Section.CONTACTS -> "مخاطبین"
        Section.CALLS -> "تماس‌ها"
        Section.SMS -> "پیامک‌ها"
    }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app)
    val plan = PlanManager(app)

    // ---- data ---------------------------------------------------------------------------------
    var data by mutableStateOf<BackupData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var contactRows by mutableStateOf<List<ItemRow>>(emptyList())
        private set
    var callRows by mutableStateOf<List<ItemRow>>(emptyList())
        private set
    var smsRows by mutableStateOf<List<ItemRow>>(emptyList())
        private set
    private var callIndex: Map<String, List<CallItem>> = emptyMap()
    private var smsIndex: Map<String, List<SmsItem>> = emptyMap()

    private var contactMap: Map<String, ContactItem> = emptyMap()

    /** Search text, advanced filters and the free "history" chip, kept per section while the app is open. */
    val queries = mutableStateMapOf<Section, String>()
    val advs = mutableStateMapOf<Section, Adv>()
    val hist = mutableStateMapOf<Section, Int>()

    /** Update state (see [UpdateManager]). */
    var updateRequired by mutableStateOf(UpdateManager.forced(app))
        private set
    var updateAvailable by mutableStateOf(UpdateManager.available(app))
        private set

    fun checkUpdate() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) { UpdateManager.check(ctx) }
            updateRequired = UpdateManager.forced(ctx)
            updateAvailable = UpdateManager.available(ctx)
        }
    }

    /** Selected row keys per section (only used by the premium plan). */
    val selection = mutableStateMapOf<Section, Set<String>>()

    // ---- UI state -----------------------------------------------------------------------------
    var job by mutableStateOf<JobState?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var backups by mutableStateOf<List<BackupFile>>(emptyList())
        private set
    /** A backup file that was opened and waits for the user to choose what to restore. */
    var pendingImport by mutableStateOf<ParsedBackup?>(null)
        private set
    private var stagedSections: Set<Section>? = null
    /** Premium "custom restore" screen (choose people / items) is open. */
    var picking by mutableStateOf(false)
        private set
    val importDialogVisible get() = pendingImport != null && stagedSections == null && !picking

    // ---- deep search for backup files ------------------------------------------------------
    var found by mutableStateOf<List<FoundBackup>>(emptyList())
        private set
    var scanRunning by mutableStateOf(false)
        private set
    var scanDone by mutableStateOf(false)
        private set
    var scanStopped by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf(ScanProgress(0, 0, ""))
        private set
    private var scanJob: Job? = null

    private var scanGen = 0

    fun startScan() {
        if (scanRunning) return
        val gen = ++scanGen
        found = emptyList()
        scanDone = false
        scanStopped = false
        scanRunning = true
        scanProgress = ScanProgress(0, 0, "")
        val unique = LinkedHashMap<String, FoundBackup>()   // real path -> backup (touched only under lock)
        scanJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BackupScanner.scan(
                        getApplication(),
                        { if (gen == scanGen) scanProgress = it },
                        { fb ->
                            if (gen == scanGen) {
                                val snapshot = synchronized(unique) {
                                    val key = runCatching { File(fb.path).canonicalPath }.getOrDefault(fb.path)
                                    val same = unique.values.any {
                                        it.name == fb.name && it.size == fb.size && it.modified == fb.modified && it.folder == fb.folder
                                    }
                                    if (!same && unique.putIfAbsent(key, fb) == null) unique.values.toList() else null
                                }
                                if (snapshot != null) found = snapshot
                            }
                        }
                    )
                }
                if (gen == scanGen) scanDone = true
            } catch (e: CancellationException) {
                if (gen == scanGen) scanDone = true
                throw e
            } catch (e: Exception) {
                if (gen == scanGen) { scanDone = true; message = "جستجو با خطا روبه‌رو شد: ${e.message}" }
            } finally {
                if (gen == scanGen) scanRunning = false
            }
        }
    }

    fun stopScan() {
        scanStopped = true
        scanJob?.cancel()
    }

    fun openFound(f: FoundBackup) {
        if (!File(f.path).exists()) { message = "این فایل دیگر وجود ندارد."; found = found - f; return }
        openBackupFile(Uri.fromFile(File(f.path)))
    }

    /** MediaStore uri of one of this app's own backups (needed to share it safely). */
    fun shareUriOf(f: FoundBackup): Uri? =
        backups.firstOrNull { it.name == f.name && it.size == f.size }?.uri ?: backups.firstOrNull { it.name == f.name }?.uri

    fun deleteFound(f: FoundBackup) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(f.path).delete() }.getOrDefault(false) ||
                    runCatching {
                        backups.firstOrNull { it.name == f.name }
                            ?.let { getApplication<Application>().contentResolver.delete(it.uri, null, null) > 0 }
                    }.getOrNull() == true
            }
            if (ok) found = found - f else message = "حذف انجام نشد. این فایل را با فایل‌منیجر پاک کنید."
            refreshBackups()
        }
    }

    fun rowsOf(s: Section): List<ItemRow> = when (s) {
        Section.CONTACTS -> contactRows
        Section.CALLS -> callRows
        Section.SMS -> smsRows
    }

    // ---- loading ------------------------------------------------------------------------------

    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { BackupEngine.readAll(getApplication()) }
                applyData(d, keepSelection = data != null)
            } catch (e: Exception) {
                message = "خواندن اطلاعات گوشی ناموفق بود: ${e.message}"
            }
            loading = false
        }
    }

    private suspend fun applyData(d: BackupData, keepSelection: Boolean) {
        val ci = withContext(Dispatchers.Default) { Rows.callIndex(d) }
        val si = withContext(Dispatchers.Default) { Rows.smsIndex(d) }
        val cr = withContext(Dispatchers.Default) { Rows.contacts(d) }
        val kr = withContext(Dispatchers.Default) { Rows.calls(d, ci) }
        val sr = withContext(Dispatchers.Default) { Rows.sms(d, si) }
        callIndex = ci; smsIndex = si
        contactMap = d.contacts.associateBy { "c${it.id}" }
        data = d
        contactRows = cr; callRows = kr; smsRows = sr
        for (s in Section.values()) {
            val keys = rowsOf(s).map { it.key }.toSet()
            val old = selection[s]
            selection[s] = when {
                keepSelection && old != null -> old.intersect(keys)
                settings.selectAllByDefault -> keys
                else -> emptySet()
            }
        }
    }

    fun callsOf(row: ItemRow): List<CallItem> =
        row.norms.flatMap { callIndex[it].orEmpty() }.sortedByDescending { it.date }

    fun smsOf(row: ItemRow): List<SmsItem> =
        row.norms.flatMap { smsIndex[it].orEmpty() }.sortedByDescending { it.date }

    fun contactOf(row: ItemRow): ContactItem? = contactMap[row.key]

    // ---- selection ----------------------------------------------------------------------------

    /** Free users always export everything of a section; only premium users can narrow it down. */
    fun selectedKeys(s: Section): Set<String> =
        if (plan.canSelect) selection[s].orEmpty() else rowsOf(s).map { it.key }.toSet()

    fun setSelection(s: Section, keys: Set<String>) { selection[s] = keys }

    fun toggle(s: Section, key: String) {
        val cur = selection[s].orEmpty()
        selection[s] = if (key in cur) cur - key else cur + key
    }

    /** How many records an export of the current selection contains. */
    fun exportCount(s: Section): Int {
        val keys = selectedKeys(s)
        return rowsOf(s).sumOf { if (it.key in keys) (if (s == Section.CONTACTS) 1 else it.count) else 0 }
    }

    // ---- exporting ----------------------------------------------------------------------------

    private fun throttled(title: String): Report {
        var lastP = -2f
        var lastS = ""
        return { f, s ->
            if (s != lastS || abs(f - lastP) >= 0.01f || f >= 1f) {
                lastP = f; lastS = s
                job = JobState.Running(title, s, f)
            }
        }
    }

    private fun subset(req: ExportRequest): BackupData {
        val d = data ?: return BackupData()
        val keys = req.keys

        /** null = take all of the section, otherwise the union of the numbers of the chosen rows. */
        fun norms(s: Section): Set<String>? {
            if (keys == null) return null
            val all = rowsOf(s)
            if (all.all { it.key in keys }) return null
            val out = HashSet<String>()
            all.forEach { if (it.key in keys) out.addAll(it.norms) }
            return out
        }

        val contacts = if (Section.CONTACTS in req.sections) {
            if (keys == null || contactRows.all { it.key in keys }) d.contacts
            else d.contacts.filter { "c${it.id}" in keys }
        } else emptyList()
        val calls = if (Section.CALLS in req.sections) {
            val n = norms(Section.CALLS)
            if (n == null) d.calls else d.calls.filter { normNumber(it.number) in n }
        } else emptyList()
        val sms = if (Section.SMS in req.sections) {
            val n = norms(Section.SMS)
            if (n == null) d.sms else d.sms.filter { normNumber(it.address) in n }
        } else emptyList()
        return BackupData(contacts, sms, calls)
    }

    private fun label(sections: Set<Section>) = when {
        sections.size >= 3 -> "full"
        Section.CONTACTS in sections -> "contacts"
        Section.CALLS in sections -> "calls"
        else -> "messages"
    }

    fun isVcf(req: ExportRequest) = req.sections == setOf(Section.CONTACTS) && settings.contactsVcf

    /** Suggested file name for the system "save as" picker. */
    fun fileNameFor(req: ExportRequest) = BackupEngine.fileName(label(req.sections), isVcf(req))

    fun export(req: ExportRequest, target: Target) {
        val ctx = getApplication<Application>()
        val title = "در حال ساخت پشتیبان"
        job = JobState.Running(title, "آماده‌سازی…", -1f)
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            try {
                val report = throttled(title)
                var from = 0f
                val d: BackupData
                if (req.refresh) {
                    d = withContext(Dispatchers.IO) {
                        BackupEngine.readAll(ctx) { f, s -> report(f * 0.5f, s) }
                    }
                    from = 0.5f
                } else {
                    d = withContext(Dispatchers.Default) { subset(req) }
                }
                val vcf = isVcf(req)
                val name = BackupEngine.fileName(label(req.sections), vcf)
                val mime = if (vcf) "text/x-vcard" else "application/json"
                var bytes = 0L
                val saved = withContext(Dispatchers.IO) {
                    val writer = { out: OutputStream ->
                        val counting = CountingOutputStream(out)
                        BackupEngine.write(counting, d, req.sections, vcf, report, from, 1f)
                        bytes = counting.count
                    }
                    when (target) {
                        Target.Downloads -> BackupEngine.saveToDownloads(ctx, name, mime, writer)
                        is Target.Custom -> BackupEngine.saveToUri(ctx, target.uri, writer)
                    }
                }
                job = JobState.Running(title, "تمام شد", 1f)
                delay(max(700L, 1500L - (SystemClock.elapsedRealtime() - t0)))
                settings.markBackupNow()
                val reports = Section.values().filter { it in req.sections }.map { sec ->
                    val groups = People.groups(d, sec)
                    SectionReport(
                        sec, d.count(sec), d.count(sec), 0, 0, groups.size,
                        items = groups.map { g -> DetailItem(g.title, g.sub, g.count, 0) }
                    )
                }
                job = JobState.Done(
                    "پشتیبان آماده است", true, emptyList(), saved.uri,
                    report = ResultReport(false, reports, saved.location, name, bytes)
                )
                refreshBackups()
                if (req.refresh) applyData(d, keepSelection = true)
            } catch (e: Exception) {
                job = JobState.Done("ساخت پشتیبان ناموفق بود", false, listOf(e.message ?: "خطای نامشخص"))
            }
        }
    }

    // ---- importing ----------------------------------------------------------------------------

    fun openBackupFile(uri: Uri) {
        val ctx = getApplication<Application>()
        job = JobState.Running("در حال خواندن فایل", "بررسی فایل پشتیبان…", -1f)
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    if (Vcf.looksLikeVcf(text)) Vcf.read(text) else Codec.read(text)
                }
                job = null
                if (parsed.present.isEmpty()) message = "این فایل داده‌ای برای بازیابی ندارد."
                else { stagedSections = null; pendingImport = parsed }
            } catch (e: Exception) {
                job = null
                message = "فایل پشتیبان معتبر نیست.\n(فقط فایل‌های ساخته‌شده با همین برنامه و فایل مخاطبین VCF قابل بازیابی هستند.)"
            }
        }
    }

    fun cancelImport() { pendingImport = null; stagedSections = null; picking = false }

    fun openPicker() { picking = true }
    fun closePicker() { picking = false }

    /** Premium: restore only the chosen part of the file. */
    fun confirmCustom(data: BackupData, sections: Set<Section>) {
        val old = pendingImport ?: return
        pendingImport = ParsedBackup(data, sections, old.createdAt)
        stagedSections = sections
        picking = false
    }

    /** Called when the user confirmed which parts to restore. */
    fun stageImport(sections: Set<Section>) { stagedSections = sections }

    fun needsSmsRole(): Boolean {
        val p = pendingImport ?: return false
        val s = stagedSections ?: return false
        return Section.SMS in s && p.data.sms.isNotEmpty() && !BackupEngine.isDefaultSmsApp(getApplication())
    }

    fun runImport() {
        val parsed = pendingImport ?: return
        val sections = stagedSections ?: return
        pendingImport = null
        stagedSections = null
        val ctx = getApplication<Application>()
        val title = "در حال بازیابی اطلاعات"
        job = JobState.Running(title, "آماده‌سازی…", 0f)
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            try {
                val report = throttled(title)
                val r = withContext(Dispatchers.IO) {
                    BackupEngine.import(ctx, parsed.data, sections, settings.skipDuplicates, report)
                }
                job = JobState.Running(title, "تمام شد", 1f)
                delay(max(700L, 1500L - (SystemClock.elapsedRealtime() - t0)))
                val smsBack = Section.SMS in sections && BackupEngine.isDefaultSmsApp(ctx)
                val notes = ArrayList<String>()
                if (smsBack) notes += "برای دریافت پیامک، برنامه‌ی پیامک همیشگی‌تان را دوباره پیش‌فرض کنید."
                job = JobState.Done(
                    "بازیابی انجام شد", r.failed == 0, emptyList(), null, smsBack,
                    ResultReport(true, r.reports, footnotes = notes)
                )
                load()
            } catch (e: Exception) {
                job = JobState.Done("بازیابی ناموفق بود", false, listOf(e.message ?: "خطای نامشخص"))
            }
        }
    }

    fun dismissJob() { job = null }

    // ---- saved backups ------------------------------------------------------------------------

    fun refreshBackups() {
        viewModelScope.launch {
            backups = try {
                withContext(Dispatchers.IO) { BackupEngine.listBackups(getApplication()) }
            } catch (e: Exception) { emptyList() }
        }
    }

    fun deleteBackup(f: BackupFile) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { getApplication<Application>().contentResolver.delete(f.uri, null, null) > 0 }
                catch (e: Exception) { false }
            }
            if (!ok) message = "حذف انجام نشد. این فایل را با فایل‌منیجر پاک کنید."
            refreshBackups()
        }
    }
}
