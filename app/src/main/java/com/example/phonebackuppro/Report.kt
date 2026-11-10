package com.example.phonebackuppro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
//  Report model: what an export / import really did, person by person
// ---------------------------------------------------------------------------------------------

/** One person (or contact) line of a report. For an export only [done] is used (= records exported). */
data class DetailItem(val title: String, val sub: String, val done: Int, val skipped: Int, val failed: Int = 0)

data class SectionReport(
    val section: Section,
    /** Records in the file / exported. */
    val total: Int,
    /** Added (import) or exported (export). */
    val done: Int,
    val skipped: Int,
    val failed: Int,
    /** Different people / contacts. */
    val people: Int,
    val note: String = "",
    val items: List<DetailItem> = emptyList()
)

data class ResultReport(
    val isImport: Boolean,
    val sections: List<SectionReport>,
    val location: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val footnotes: List<String> = emptyList()
)

/** Collects, per person, how many records were added / skipped as duplicates / failed. */
class Tally {
    private class E(val title: String, val sub: String) {
        var done = 0
        var skipped = 0
        var failed = 0
    }

    private val m = LinkedHashMap<String, E>()
    private fun e(k: String, t: String, s: String) = m.getOrPut(k) { E(t, s) }

    fun done(k: String, t: String, s: String) { e(k, t, s).done++ }
    fun skip(k: String, t: String, s: String) { e(k, t, s).skipped++ }

    /** A batch that was counted as added could not be written after all. */
    fun fail(k: String) {
        val x = m[k] ?: return
        if (x.done > 0) { x.done--; x.failed++ }
    }

    fun items(): List<DetailItem> = m.values.map { DetailItem(it.title, it.sub, it.done, it.skipped, it.failed) }
}

fun Section.icon(): ImageVector = when (this) {
    Section.CONTACTS -> Icons.Rounded.Contacts
    Section.CALLS -> Icons.Rounded.Call
    Section.SMS -> Icons.Rounded.Sms
}

@Composable
fun Section.tint(): Color = when (this) {
    Section.CONTACTS -> pal.g1
    Section.CALLS -> pal.info
    Section.SMS -> pal.success
}

private fun Section.unitWord() = when (this) {
    Section.CONTACTS -> "مخاطب"
    Section.CALLS -> "تماس"
    Section.SMS -> "پیامک"
}

/** Keeps latin paths readable inside right-to-left text. */
fun String.ltr(): String = "\u202A$this\u202C"

// ---------------------------------------------------------------------------------------------
//  UI
// ---------------------------------------------------------------------------------------------

@Composable
fun ReportBody(r: ResultReport, modifier: Modifier = Modifier) {
    val p = pal
    val done = r.sections.sumOf { it.done }
    val skipped = r.sections.sumOf { it.skipped }
    val failed = r.sections.sumOf { it.failed }
    val people = r.sections.sumOf { it.people }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.isImport) {
                StatTile(done.fa(), "اضافه شد", p.success, Modifier.weight(1f))
                StatTile(skipped.fa(), "تکراری (رد شد)", p.warn, Modifier.weight(1f))
                if (failed > 0) StatTile(failed.fa(), "ناموفق", p.danger, Modifier.weight(1f))
            } else {
                StatTile(done.fa(), "مورد در خروجی", p.success, Modifier.weight(1f))
                StatTile(people.fa(), "نفر / مخاطب", p.info, Modifier.weight(1f))
            }
        }
        if (!r.isImport && (r.location != null || r.fileName != null)) FileCard(r)
        r.sections.forEach { SectionReportCard(r.isImport, it) }
        r.footnotes.forEach {
            Text(it, style = Ty.small, color = p.warn, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = Ty.h2, color = color, maxLines = 1)
        Text(label, style = Ty.small, color = pal.textDim, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FileCard(r: ResultReport) {
    val p = pal
    AppCard(Modifier.fillMaxWidth(), corner = 18.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Folder, p.info, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("فایل ذخیره‌شده", style = Ty.small, color = p.textDim)
                r.fileName?.let { Text(it.ltr(), style = Ty.smallM, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                r.location?.let { Text(it.ltr(), style = Ty.small, color = p.textDim, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                if (r.fileSize > 0) Text("حجم: ${formatSize(r.fileSize)}", style = Ty.small, color = p.textDim)
            }
        }
    }
}

@Composable
private fun SectionReportCard(isImport: Boolean, s: SectionReport) {
    val p = pal
    var open by remember { mutableStateOf(true) }
    var shown by remember { mutableIntStateOf(10) }
    val sorted = remember(s, isImport) {
        if (isImport) s.items.sortedWith(
            compareByDescending<DetailItem> { it.done > 0 }
                .thenByDescending { it.failed > 0 }
                .thenByDescending { it.done }
                .thenBy { it.title.lowercase() }
        ) else s.items.sortedByDescending { it.done }
    }
    val unit = s.section.unitWord()
    AppCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().bounceClick { open = !open }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(s.section.icon(), s.section.tint(), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.section.title, style = Ty.bodyM, color = p.text)
                    val base = if (isImport) "در فایل: ${s.total.fa()} $unit" else "${s.total.fa()} $unit"
                    Text(
                        if (s.section == Section.CONTACTS) base else "$base از ${s.people.fa()} نفر",
                        style = Ty.small, color = p.textDim
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore, null, tint = p.textDim,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (open) 180f else 0f }
                )
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isImport) {
                        Pill("${s.done.fa()} اضافه شد", p.success)
                        Pill("${s.skipped.fa()} تکراری", p.warn)
                        if (s.failed > 0) Pill("${s.failed.fa()} ناموفق", p.danger)
                    } else {
                        Pill("${s.done.fa()} $unit در خروجی", p.success)
                    }
                }
                if (s.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.note, style = Ty.small, color = p.warn)
                }
                AnimatedVisibility(
                    visible = open && sorted.isNotEmpty(),
                    enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(140))
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
                        Spacer(Modifier.height(4.dp))
                        sorted.take(shown).forEach { DetailRow(isImport, s.section, it) }
                        if (sorted.size > shown) {
                            Spacer(Modifier.height(6.dp))
                            SoftButton(
                                "نمایش بیشتر (${(sorted.size - shown).fa()})",
                                { shown += 25 }, Modifier.fillMaxWidth(), compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(isImport: Boolean, section: Section, it: DetailItem) {
    val p = pal
    val unit = section.unitWord()
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(it.title, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(it.title, style = Ty.bodyM, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (it.sub.isNotBlank() && it.sub != it.title) {
                Text(it.sub.toFaDigits(), style = Ty.small, color = p.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (isImport) {
                if (section == Section.CONTACTS) {
                    if (it.done > 0) Pill("اضافه شد", p.success)
                    if (it.skipped > 0) Pill("تکراری", p.warn)
                    if (it.failed > 0) Pill("ناموفق", p.danger)
                } else {
                    if (it.done > 0) Pill("${it.done.fa()} جدید", p.success)
                    if (it.skipped > 0) Pill("${it.skipped.fa()} تکراری", p.warn)
                    if (it.failed > 0) Pill("${it.failed.fa()} ناموفق", p.danger)
                }
            } else if (section != Section.CONTACTS) {
                Pill("${it.done.fa()} $unit", p.info)
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, size: Dp = 3.dp) {
    Text(
        text, style = Ty.smallM, color = color, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = size)
    )
}
