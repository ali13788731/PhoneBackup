package com.example.phonebackuppro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = Ty.smallM, color = pal.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    content()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> SelectChip(l, i == selected) { onSelect(i) } }
    }
}

/** Advanced search / filter (premium). Works on the list of the given [section]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterDialog(section: Section, initial: Adv, onApply: (Adv) -> Unit, onDismiss: () -> Unit) {
    val p = pal
    var a by remember { mutableStateOf(initial) }
    val isContacts = section == Section.CONTACTS
    val isCalls = section == Section.CALLS
    val isSms = section == Section.SMS

    AppDialog(onDismiss) {
        Column(Modifier.padding(22.dp)) {
            Text("فیلتر پیشرفته · ${section.title}", style = Ty.h2, color = p.text)
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {

                Group("جستجو در") {
                    val labels = buildList {
                        add("نام و شماره"); add("فقط نام"); add("فقط شماره")
                        if (isSms) add("متن پیامک")
                        if (isContacts) add("ایمیل")
                    }
                    Chips(labels, a.scope.coerceAtMost(labels.lastIndex)) { a = a.copy(scope = it) }
                }

                Group("مرتب‌سازی") {
                    Chips(
                        listOf("پیش‌فرض", "جدیدترین", "قدیمی‌ترین", "الف تا ی", "ی تا الف", "A تا Z", "Z تا A", "بیشترین", "کمترین"),
                        a.sort
                    ) { a = a.copy(sort = it) }
                }

                if (!isContacts) {
                    Group("منبع") {
                        Chips(listOf("همه", "مخاطبین ذخیره‌شده", "شماره‌های ناشناس"), a.source) { a = a.copy(source = it) }
                    }
                }

                Group(if (isContacts) "آخرین ویرایش" else "آخرین فعالیت") {
                    Chips(
                        listOf("همه‌ی زمان‌ها", "۷ روز اخیر", "۳۰ روز اخیر", "۳ ماه اخیر", "یک سال اخیر", "قدیمی‌تر از یک سال"),
                        a.datePreset
                    ) { a = a.copy(datePreset = it) }
                }

                if (!isContacts) {
                    Group(if (isCalls) "حداقل تعداد تماس" else "حداقل تعداد پیامک") {
                        val steps = listOf(0, 1, 5, 20, 100)
                        Chips(steps.map { if (it == 0) "همه" else "≥ ${it.fa()}" }, steps.indexOf(a.minCount).coerceAtLeast(0)) {
                            a = a.copy(minCount = steps[it])
                        }
                    }
                }

                if (isCalls) {
                    Group("نوع تماس (چندتایی)") {
                        val types = listOf(1 to "ورودی", 2 to "خروجی", 3 to "بی‌پاسخ", 5 to "ردشده")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            types.forEach { (t, l) ->
                                val on = t in a.callTypes
                                SelectChip(l, on) { a = a.copy(callTypes = if (on) a.callTypes - t else a.callTypes + t) }
                            }
                        }
                    }
                    Group("حداقل مدت یک تماس") {
                        val steps = listOf(0L, 60L, 300L, 1800L)
                        Chips(listOf("همه", "≥ ۱ دقیقه", "≥ ۵ دقیقه", "≥ ۳۰ دقیقه"), steps.indexOf(a.minDurationSec).coerceAtLeast(0)) {
                            a = a.copy(minDurationSec = steps[it])
                        }
                    }
                }

                if (isSms) {
                    Group("جهت پیامک") {
                        Chips(listOf("هر دو", "دارای دریافتی", "دارای ارسالی"), a.smsDir) { a = a.copy(smsDir = it) }
                    }
                    Group("وضعیت") {
                        Chips(listOf("همه", "فقط خوانده‌نشده"), if (a.unreadOnly) 1 else 0) { a = a.copy(unreadOnly = it == 1) }
                    }
                }

                if (isContacts) {
                    Group("ویژگی مخاطب") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("دارای ایمیل", a.hasEmail) { a = a.copy(hasEmail = !a.hasEmail) }
                            SelectChip("بیش از یک شماره", a.multiNumber) { a = a.copy(multiNumber = !a.multiNumber) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("پاک کردن همه", { a = Adv() }, Modifier.weight(1f))
                GradientButton("اعمال فیلتر", { onApply(a) }, Modifier.weight(1.3f))
            }
        }
    }
}
