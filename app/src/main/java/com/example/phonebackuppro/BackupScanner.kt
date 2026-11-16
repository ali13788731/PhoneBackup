package com.example.phonebackuppro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.JsonReader
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.file.Files

/** A restorable file that the deep search found somewhere on the phone. */
data class FoundBackup(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val isVcf: Boolean,
    /** Where it came from: Telegram, Downloads, ... */
    val source: String,
    /** Folder shown to the user. */
    val folder: String,
    val contacts: Int,
    val calls: Int,
    val callPeople: Int,
    val sms: Int,
    val smsPeople: Int,
    val createdAt: Long,
    val device: String
)

data class ScanProgress(val folders: Int, val files: Int, val path: String)

/**
 * Walks the whole shared storage (internal memory + SD cards, Telegram folders included) and
 * recognises this app's backup files by looking INSIDE them, not only at their names.
 */
object BackupScanner {
    const val APP_SOURCE = "پشتیبان‌های برنامه"

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun roots(ctx: Context): List<File> {
        val out = LinkedHashSet<File>()
        out.add(Environment.getExternalStorageDirectory())
        for (f in ctx.getExternalFilesDirs(null)) {
            if (f == null) continue
            val p = f.absolutePath
            val i = p.indexOf("/Android/")
            if (i > 0) out.add(File(p.substring(0, i)))
        }
        // /storage/emulated/0, /sdcard, /storage/self/primary ... are the same place: keep one
        val unique = LinkedHashMap<String, File>()
        for (f in out) {
            if (!f.exists() || !f.canRead()) continue
            val key = try { f.canonicalPath } catch (e: Exception) { f.absolutePath }
            unique.putIfAbsent(key, f)
        }
        return unique.values.toList()
    }

    private fun skipDir(f: File): Boolean {
        val n = f.name
        if (n == ".thumbnails" || n == ".cache" || n.startsWith(".Trash", ignoreCase = true)) return true
        val p = f.absolutePath
        // Android keeps other apps' private folders locked: nothing to find there
        return p.endsWith("/Android/data") || p.endsWith("/Android/obb")
    }

    private fun isCandidate(f: File): Boolean {
        val n = f.name.lowercase()
        // Android keeps half-written (.pending-) and recycle-bin (.trashed-) copies next to real files
        if (n.startsWith(".pending-") || n.startsWith(".trashed-")) return false
        val ext = n.substringAfterLast('.', "")
        return (ext == "json" || ext == "vcf" || n.contains("phonebackup")) && f.length() > 20L
    }

    /**
     * Where a file lives, judged ONLY by the folders it sits in (never by its own name, so a file that
     * merely has "telegram" in its name is not reported as coming from Telegram).
     */
    private fun sourceOf(f: File): String {
        val dirs = (f.parent ?: "").lowercase().split('/').filter { it.isNotEmpty() }
        return when {
            dirs.any { it == "phonebackuppro" } -> APP_SOURCE
            dirs.any { it.contains("telegram") } -> "تلگرام"
            dirs.any { it.contains("whatsapp") } -> "واتساپ"
            dirs.any { it == "bluetooth" } -> "بلوتوث"
            dirs.any { it == "download" || it == "downloads" } -> "دانلودها"
            dirs.any { it == "documents" } -> "اسناد"
            else -> "حافظه"
        }
    }

    private fun realPath(f: File): String = try { f.canonicalPath } catch (e: Exception) { f.absolutePath }

    private fun prettyFolder(f: File): String {
        val p = (try { f.canonicalFile.parent } catch (e: Exception) { null }) ?: f.parent ?: ""
        val internal = try { Environment.getExternalStorageDirectory().canonicalPath }
            catch (e: Exception) { Environment.getExternalStorageDirectory().absolutePath }
        return if (p == internal) "حافظه‌ی داخلی"
        else if (p.startsWith("$internal/")) p.removePrefix("$internal/")
        else p
    }

    /** Streams over the whole tree. [onFound] is called as soon as a backup is recognised. */
    suspend fun scan(ctx: Context, onProgress: (ScanProgress) -> Unit, onFound: (FoundBackup) -> Unit) {
        val queue = ArrayDeque<File>()
        roots(ctx).forEach { queue.addLast(it) }
        val seen = HashSet<String>()      // real (canonical) paths already reported
        val seenDirs = HashSet<String>()  // real folders already walked (bind mounts / aliases)
        var folders = 0
        var files = 0
        var last = 0L
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.removeFirst()
            if (!seenDirs.add(realPath(dir))) continue
            folders++
            val list = try { dir.listFiles() } catch (e: Exception) { null }
            if (list != null) {
                for (f in list) {
                    if (f.isDirectory) {
                        if (skipDir(f)) continue
                        val link = try { Files.isSymbolicLink(f.toPath()) } catch (e: Exception) { false }
                        if (link) continue
                        queue.addLast(f)
                    } else {
                        files++
                        if (isCandidate(f)) {
                            val key = realPath(f)
                            if (key !in seen) {
                                val found = inspect(f)
                                if (found != null && seen.add(key)) onFound(found)
                            }
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            if (now - last > 120) {
                last = now
                onProgress(ScanProgress(folders, files, dir.absolutePath))
            }
        }
        onProgress(ScanProgress(folders, files, ""))
    }

    private fun inspect(f: File): FoundBackup? {
        try {
            val head = ByteArray(2048)
            val n = FileInputStream(f).use { it.read(head) }
            if (n <= 0) return null
            val text = String(head, 0, n, Charsets.UTF_8)
            return if (Vcf.looksLikeVcf(text)) summarizeVcf(f)
            else if (text.contains("PhoneBackupPro")) summarizeJson(f)
            else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun make(
        f: File, vcf: Boolean, contacts: Int, calls: Int, callPeople: Int, sms: Int, smsPeople: Int,
        createdAt: Long, device: String
    ) = FoundBackup(
        realPath(f), f.name, f.length(), f.lastModified(), vcf, sourceOf(f), prettyFolder(f),
        contacts, calls, callPeople, sms, smsPeople, createdAt, device
    )

    private fun summarizeVcf(f: File): FoundBackup? {
        var cards = 0
        BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 1 shl 16).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (line.startsWith("BEGIN:VCARD", ignoreCase = true)) cards++
            }
        }
        if (cards == 0) return null
        return make(f, true, cards, 0, 0, 0, 0, 0L, "")
    }

    private fun summarizeJson(f: File): FoundBackup? {
        var format = ""
        var created = 0L
        var device = ""
        var contacts = 0
        var calls = 0
        var sms = 0
        val callPeople = HashSet<String>()
        val smsPeople = HashSet<String>()
        JsonReader(BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 1 shl 16)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "format" -> format = r.nextString()
                    "createdAt" -> created = r.nextLong()
                    "device" -> device = r.nextString()
                    "contacts" -> {
                        r.beginArray()
                        while (r.hasNext()) { r.skipValue(); contacts++ }
                        r.endArray()
                    }
                    "calls" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginObject()
                            while (r.hasNext()) {
                                if (r.nextName() == "number") callPeople.add(personKey(r.nextString())) else r.skipValue()
                            }
                            r.endObject()
                            calls++
                        }
                        r.endArray()
                    }
                    "sms" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginObject()
                            while (r.hasNext()) {
                                if (r.nextName() == "address") smsPeople.add(personKey(r.nextString())) else r.skipValue()
                            }
                            r.endObject()
                            sms++
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        if (format != Codec.FORMAT) return null
        if (contacts + calls + sms == 0) return null
        return make(f, false, contacts, calls, callPeople.size, sms, smsPeople.size, created, device)
    }
}
