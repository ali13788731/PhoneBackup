package com.example.phonebackuppro

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Section.unit() = when (this) {
    Section.CONTACTS -> "مخاطب"
    Section.CALLS -> "تماس"
    Section.SMS -> "پیامک"
}

/** One screen for all three sections: contacts, calls and messages are separate lists. */
@Composable
fun ListScreen(
    vm: MainViewModel,
    section: Section,
    onExport: (Section) -> Unit,
    onUpgrade: () -> Unit
) {
    val p = pal
    val rows = vm.rowsOf(section)
    val paid = vm.plan.isPaid
    var expanded by remember(section) { mutableStateOf<String?>(null) }
    var opened by remember(section) { mutableStateOf(false) }
    var showAdv by remember { mutableStateOf(false) }
    // search + advanced filters are premium: a free user always sees the plain list
    val query = if (paid) vm.queries[section].orEmpty() else ""
    val adv = if (paid) vm.advs[section] ?: Adv() else Adv()
    val hist = 0   // the old "all / with history / zero" chips were removed

    val visible = remember(rows, query, adv, hist, vm.data) {
        searchRows(rows, section, query, adv, hist, { vm.smsOf(it) }, { vm.contactOf(it) })
    }
    // free plan: the FIRST row shows its full details (so people see what premium gives), the rest are locked
    val previewKey = if (paid) null else visible.firstOrNull { section == Section.CONTACTS || it.count > 0 }?.key
    LaunchedEffect(previewKey) {
        if (!paid && !opened && previewKey != null) { expanded = previewKey; opened = true }
    }
    val selected = vm.selectedKeys(section)
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.key in selected }
    val exportCount = vm.exportCount(section)

    if (showAdv) {
        AdvancedFilterDialog(
            section, adv,
            onApply = { vm.advs[section] = it; showAdv = false },
            onDismiss = { showAdv = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title, style = Ty.title, color = p.text, modifier = Modifier.weight(1f))
                    Text(
                        "${visible.size.fa()} از ${rows.size.fa()}",
                        style = Ty.smallM, color = p.textDim
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (paid) SearchField(query, { vm.queries[section] = it })
                        else LockedSearch(onUpgrade)
                    }
                    Spacer(Modifier.width(10.dp))
                    FilterButton(adv.activeCount, !paid) { if (paid) showAdv = true else onUpgrade() }
                }
                if (paid && (adv.activeCount > 0 || query.isNotEmpty())) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (adv.activeCount > 0) "${adv.activeCount.fa()} فیلتر فعال" else "جستجو فعال است",
                            style = Ty.smallM, color = p.g1, modifier = Modifier.weight(1f)
                        )
                        Text(
                            "پاک کردن همه", style = Ty.smallM, color = p.danger,
                            modifier = Modifier.bounceClick { vm.advs[section] = Adv(); vm.queries[section] = "" }
                        )
                    }
                }
                if (!paid) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.warn.copy(alpha = 0.12f))
                            .bounceClick(onClick = onUpgrade)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = p.warn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "نسخه‌ی رایگان: خروجی همه‌ی موارد + جزئیات مورد اول. بقیه‌ی امکانات کم‌رنگ، ویژه‌ی پریمیوم است.",
                            style = Ty.small, color = p.text
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FancyCheck(
                        checked = if (paid) allVisibleSelected else true,
                        locked = !paid,
                        onClick = {
                            if (!paid) onUpgrade()
                            else {
                                val keys = visible.map { it.key }.toSet()
                                vm.setSelection(section, if (allVisibleSelected) selected - keys else selected + keys)
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("انتخاب همه", style = Ty.bodyM, color = p.text, modifier = Modifier.weight(1f))
                    Text("${(if (paid) selected.size else rows.size).fa()} انتخاب‌شده", style = Ty.small, color = p.textDim)
                }
                Spacer(Modifier.height(6.dp))
            }

            if (rows.isEmpty()) {
                Box(Modifier.weight(1f)) { EmptyState(if (vm.loading) "در حال خواندن…" else "موردی پیدا نشد") }
            } else if (visible.isEmpty()) {
                Box(Modifier.weight(1f)) { EmptyState("نتیجه‌ای برای این جستجو یا فیلتر نیست") }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.key }) { row ->
                        PersonItem(
                            vm, section, row,
                            selected = row.key in selected, locked = !paid, paid = paid, onUpgrade = onUpgrade,
                            expanded = expanded == row.key,
                            preview = row.key == previewKey,
                            onToggleSelect = { if (paid) vm.toggle(section, row.key) else onUpgrade() },
                            onExpand = { expanded = if (expanded == row.key) null else row.key }
                        )
                    }
                }
            }

            // export bar: a plain, flat strip (no corner radius, no own background) so it never
            // fights with the rounded top edge of the bottom navigation underneath it
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    GradientButton(
                        text = if (paid) "خروجی ${exportCount.fa()} ${section.unit()}" else "خروجی همه (${exportCount.fa()} ${section.unit()})",
                        onClick = { onExport(section) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Backup,
                        enabled = exportCount > 0
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedSearch(onUpgrade: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.stroke, shape)
            .bounceClick(onClick = onUpgrade)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("جستجو · پریمیوم", style = Ty.body, color = p.textDim, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Lock, null, tint = p.warn, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun FilterButton(active: Int, locked: Boolean, onClick: () -> Unit) {
    val p = pal
    Box(Modifier.size(46.dp).alpha(if (locked) 0.5f else 1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active > 0) p.g1 else p.card)
                .border(1.dp, p.stroke, RoundedCornerShape(16.dp))
                .bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Tune, null, tint = if (active > 0) androidx.compose.ui.graphics.Color.White else p.text, modifier = Modifier.size(22.dp))
        }
        if (locked) {
            Icon(
                Icons.Rounded.Lock, null, tint = p.warn,
                modifier = Modifier.align(Alignment.BottomStart).size(15.dp)
            )
        } else if (active > 0) {
            Box(
                Modifier.align(Alignment.TopStart).size(18.dp).clip(CircleShape).background(p.danger),
                contentAlignment = Alignment.Center
            ) { Text(active.fa(), style = Ty.small.copy(fontSize = 10.sp), color = androidx.compose.ui.graphics.Color.White) }
        }
    }
}

@Composable
private fun LockedDetails(section: Section, onUpgrade: () -> Unit) {
    val p = pal
    DetailDivider()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Lock, null, tint = p.warn, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            when (section) {
                Section.CONTACTS -> "شماره‌ها، ایمیل‌ها و تاریخ ویرایش هر مخاطب ویژه‌ی پریمیوم است. جزئیات مخاطب اول رایگان نمایش داده شده."
                Section.CALLS -> "جزئیات تاریخچه‌ی تماس (تاریخ، نوع و مدت هر تماس) ویژه‌ی پریمیوم است. تماس‌های مخاطب اول رایگان نمایش داده شده."
                Section.SMS -> "متن و جزئیات پیامک‌ها ویژه‌ی پریمیوم است. پیامک‌های مخاطب اول رایگان نمایش داده شده."
            },
            style = Ty.small, color = p.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        GradientButton("باز کردن با پریمیوم", onUpgrade, icon = Icons.Rounded.Star)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Ty.body, color = pal.textDim)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.stroke, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("جستجوی نام یا شماره…", style = Ty.body, color = p.textDim)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = Ty.body.copy(color = p.text),
                cursorBrush = SolidColor(p.g1),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, null, tint = p.textDim,
                modifier = Modifier.size(20.dp).bounceClick { onChange("") }
            )
        }
    }
}

@Composable
private fun PersonItem(
    vm: MainViewModel, section: Section, row: ItemRow,
    selected: Boolean, locked: Boolean, paid: Boolean, onUpgrade: () -> Unit, expanded: Boolean,
    onToggleSelect: () -> Unit, onExpand: () -> Unit, preview: Boolean = false
) {
    val p = pal
    AppCard(Modifier.fillMaxWidth(), corner = 20.dp, onClick = onExpand) {
        Column {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FancyCheck(selected || locked, onToggleSelect, locked = locked)
                Spacer(Modifier.width(10.dp))
                Avatar(row.title)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = Ty.bodyM, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (section) {
                        Section.CONTACTS -> {
                            Text(
                                row.subtitle.toFaDigits() + if (row.count > 1) "  +${(row.count - 1).fa()}" else "",
                                style = Ty.small, color = p.textDim, maxLines = 1
                            )
                            Text("تاریخ: ${formatDate(row.lastDate)}", style = Ty.small, color = p.textDim)
                        }
                        Section.CALLS -> {
                            Text(row.subtitle.toFaDigits(), style = Ty.small, color = p.textDim, maxLines = 1)
                            Text(
                                if (row.count == 0) "بدون سابقه‌ی تماس" else "آخرین: ${formatDateTime(row.lastDate)}",
                                style = Ty.small, color = p.textDim
                            )
                        }
                        Section.SMS -> {
                            Text(
                                if (row.count == 0) row.subtitle.toFaDigits()
                                else if (!paid && !preview) "🔒 متن پیامک‌ها (پریمیوم)"
                                else row.preview.replace("\n", " "),
                                style = Ty.small, color = p.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (row.count == 0) "بدون پیامک" else "آخرین: ${formatDateTime(row.lastDate)}",
                                style = Ty.small, color = p.textDim
                            )
                        }
                    }
                }
                if (section != Section.CONTACTS) CountBadge(row.count)
                Icon(Icons.Rounded.ExpandMore, null, tint = p.textDim, modifier = Modifier.size(22.dp))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    when {
                        !paid && !preview -> LockedDetails(section, onUpgrade)
                        section == Section.CONTACTS -> ContactDetails(vm, row)
                        section == Section.CALLS -> CallDetails(vm, row)
                        else -> SmsDetails(vm, row)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    val p = pal
    val on = count > 0
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) p.g1.copy(alpha = 0.16f) else p.cardHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(count.fa(), style = Ty.smallM, color = if (on) p.g1 else p.textDim) }
}

@Composable
private fun DetailDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.stroke))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ContactDetails(vm: MainViewModel, row: ItemRow) {
    val p = pal
    val c = remember(row.key, vm.data) { vm.contactOf(row) }
    DetailDivider()
    if (c == null) return
    c.phones.forEach {
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Phone, null, tint = p.info, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(it.toFaDigits(), style = Ty.body, color = p.text)
        }
    }
    c.emails.forEach {
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Email, null, tint = p.g1, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(it, style = Ty.body, color = p.text)
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("آخرین ویرایش مخاطب: ${formatDateTime(c.date)}", style = Ty.small, color = p.textDim)
}

private fun callTypeLabel(t: Int) = when (t) {
    1 -> "ورودی"; 2 -> "خروجی"; 3 -> "بی‌پاسخ"; 4 -> "پیام صوتی"; 5 -> "ردشده"; 6 -> "مسدود"; else -> "تماس"
}

@Composable
private fun CallDetails(vm: MainViewModel, row: ItemRow) {
    val p = pal
    val calls = remember(row.key, vm.data) { vm.callsOf(row) }
    DetailDivider()
    if (calls.isEmpty()) {
        Text("هیچ تماسی ثبت نشده است (صفر).", style = Ty.body, color = p.textDim)
        return
    }
    Text(
        "ورودی ${calls.count { it.type == 1 }.fa()} • خروجی ${calls.count { it.type == 2 }.fa()} • " +
            "بی‌پاسخ ${calls.count { it.type == 3 }.fa()}",
        style = Ty.smallM, color = p.textDim
    )
    Spacer(Modifier.height(8.dp))
    calls.take(30).forEach { k ->
        val (icon, tint) = when (k.type) {
            1 -> Icons.Rounded.ArrowDownward to p.success
            2 -> Icons.Rounded.ArrowUpward to p.info
            else -> Icons.Rounded.Close to p.danger
        }
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDateTime(k.date), style = Ty.body, color = p.text)
                Text(callTypeLabel(k.type), style = Ty.small, color = p.textDim)
            }
            Text(formatDuration(k.duration), style = Ty.smallM, color = p.textDim)
        }
    }
    if (calls.size > 30) {
        Text("… و ${(calls.size - 30).fa()} تماس دیگر", style = Ty.small, color = p.textDim)
    }
}

@Composable
private fun SmsDetails(vm: MainViewModel, row: ItemRow) {
    val p = pal
    val list = remember(row.key, vm.data) { vm.smsOf(row) }
    DetailDivider()
    if (list.isEmpty()) {
        Text("هیچ پیامکی ثبت نشده است (صفر).", style = Ty.body, color = p.textDim)
        return
    }
    list.take(12).forEach { m ->
        val sent = m.type == 2
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (sent) p.g1.copy(alpha = 0.16f) else p.cardHigh)
                    .padding(10.dp)
            ) {
                Text(m.body, style = Ty.body, color = p.text, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Text(formatDateTime(m.date), style = Ty.small, color = p.textDim)
            }
        }
    }
    if (list.size > 12) {
        Text("… و ${(list.size - 12).fa()} پیامک دیگر", style = Ty.small, color = p.textDim)
    }
}
