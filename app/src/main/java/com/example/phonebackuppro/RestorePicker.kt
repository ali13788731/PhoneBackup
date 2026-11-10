package com.example.phonebackuppro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Premium "custom restore": choose exactly which contacts / people (calls, messages) of the file
 * are restored. Starts with everything ticked.
 */
@Composable
fun RestorePicker(
    parsed: ParsedBackup,
    onConfirm: (BackupData, Set<Section>) -> Unit,
    onClose: () -> Unit
) {
    val p = pal
    val data = parsed.data
    val sections = remember(parsed) {
        Section.values().filter { it in parsed.present && data.count(it) > 0 }
    }
    if (sections.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val groups = remember(parsed) {
        sections.associateWith { s -> People.groups(data, s).sortedWith(compareBy<PersonGroup>({ faGroup(it.title) }, { faSortKey(it.title) })) }
    }
    val selected = remember(parsed) {
        mutableStateMapOf<Section, Set<String>>().apply {
            sections.forEach { s -> put(s, groups[s].orEmpty().map { it.key }.toSet()) }
        }
    }
    var tab by remember(parsed) { mutableStateOf(sections.first()) }
    var query by remember { mutableStateOf("") }

    val all = groups[tab].orEmpty()
    val q = query.trim().toLatinDigits().lowercase()
    val shown = remember(all, q) {
        if (q.isEmpty()) all
        else all.filter { it.title.lowercase().contains(q) || it.sub.toLatinDigits().contains(q) }
    }
    val sel = selected[tab].orEmpty()
    val allShownOn = shown.isNotEmpty() && shown.all { it.key in sel }

    val pickedPeople = sections.sumOf { selected[it].orEmpty().size }
    val pickedItems = sections.sumOf { s ->
        val keys = selected[s].orEmpty()
        groups[s].orEmpty().sumOf { if (it.key in keys) it.count else 0 }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("انتخاب دلخواه برای بازیابی", onClose)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { s ->
                    SelectChip(
                        "${s.title} (${selected[s].orEmpty().size.fa()}/${groups[s].orEmpty().size.fa()})",
                        tab == s
                    ) { tab = s; query = "" }
                }
            }
            Spacer(Modifier.height(10.dp))
            SearchField(query) { query = it }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FancyCheck(allShownOn, {
                    val keys = shown.map { it.key }.toSet()
                    selected[tab] = if (allShownOn) sel - keys else sel + keys
                })
                Spacer(Modifier.width(10.dp))
                Text("انتخاب همه", style = Ty.bodyM, color = p.text, modifier = Modifier.weight(1f))
                Text("${sel.size.fa()} انتخاب‌شده", style = Ty.small, color = p.textDim)
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { it.key }) { g ->
                val on = g.key in sel
                val toggle = { selected[tab] = if (on) sel - g.key else sel + g.key }
                AppCard(Modifier.fillMaxWidth(), corner = 18.dp, onClick = { toggle() }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FancyCheck(on, { toggle() })
                        Spacer(Modifier.width(10.dp))
                        Avatar(g.title, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = Ty.bodyM, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.sub.isNotBlank() && g.sub != g.title) {
                                Text(g.sub.toFaDigits(), style = Ty.small, color = p.textDim, maxLines = 1)
                            }
                        }
                        if (tab != Section.CONTACTS) {
                            Text(
                                "${g.count.fa()} ${if (tab == Section.CALLS) "تماس" else "پیامک"}",
                                style = Ty.smallM, color = p.g1
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            GradientButton(
                text = "بازیابی ${pickedItems.fa()} مورد از ${pickedPeople.fa()} نفر",
                onClick = {
                    val contacts = if (Section.CONTACTS in sections) {
                        val keys = selected[Section.CONTACTS].orEmpty()
                        data.contacts.filterIndexed { i, _ -> "c$i" in keys }
                    } else emptyList()
                    val calls = if (Section.CALLS in sections) {
                        val keys = selected[Section.CALLS].orEmpty()
                        data.calls.filter { personKey(it.number) in keys }
                    } else emptyList()
                    val sms = if (Section.SMS in sections) {
                        val keys = selected[Section.SMS].orEmpty()
                        data.sms.filter { personKey(it.address) in keys }
                    } else emptyList()
                    val active = sections.filter { selected[it].orEmpty().isNotEmpty() }.toSet()
                    onConfirm(BackupData(contacts, sms, calls), active)
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Restore,
                enabled = pickedItems > 0
            )
        }
    }
}
