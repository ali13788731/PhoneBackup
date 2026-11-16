package com.example.phonebackuppro

import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val PremiumFeatures = listOf(
    "پشتیبان کامل: مخاطبین، تماس‌ها و پیامک‌ها با هم",
    "انتخاب دلخواه مخاطبین، تماس‌ها و پیامک‌ها",
    "بازیابی دلخواه: انتخاب افراد و موارد از داخل فایل",
    "جستجو و فیلتر بسیار پیشرفته",
    "جزئیات تماس‌ها و متن پیامک‌ها",
    "ذخیره‌ی فایل در هر مسیر دلخواه",
    "اشتراک‌گذاری فایل پشتیبان",
    "حمایت از ادامه‌ی توسعه‌ی برنامه 💜"
)

@Composable
fun PaywallDialog(billing: BillingManager, plan: PlanManager, onDismiss: () -> Unit) {
    val p = pal
    LaunchedEffect(plan.isPaid) { if (plan.isPaid) onDismiss() }
    val pulse by rememberInfiniteTransition(label = "crown").animateFloat(
        0.94f, 1.08f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "crownS"
    )
    AppDialog(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(78.dp).scale(pulse).clip(CircleShape).background(p.brush),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("پریمیوم", style = Ty.title, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "پشتیبان‌گیری از هر بخش به‌تنهایی همیشه رایگان است. پریمیوم این امکانات را باز می‌کند:",
                style = Ty.small, color = p.textDim, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            PremiumFeatures.forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(p.success.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Check, null, tint = p.success, modifier = Modifier.size(14.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = Ty.body, color = p.text)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                plan.price,
                style = Ty.big, color = p.text
            )
            Text(
                if (Config.USE_SUBSCRIPTION) "اشتراک • پرداخت امن از طریق کافه‌بازار" else "یک‌بار پرداخت • از طریق کافه‌بازار",
                style = Ty.small, color = p.textDim
            )
            Spacer(Modifier.height(16.dp))
            GradientButton(
                if (billing.working) "در حال اتصال به کافه‌بازار…" else "خرید پریمیوم",
                { billing.buy() }, Modifier.fillMaxWidth(), enabled = !billing.working, icon = Icons.Rounded.Star
            )
            Spacer(Modifier.height(10.dp))
            SoftButton("قبلاً خریده‌ام (بازیابی خرید)", { billing.restore() }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("فعلاً نه", style = Ty.body, color = p.textDim)
            }
        }
    }
}

/** Asks where to save: the free default folder, or (premium) any place the user wants. */
@Composable
fun SaveTargetDialog(
    isPaid: Boolean,
    onDownloads: () -> Unit,
    onCustom: () -> Unit,
    onLocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = pal
    AppDialog(onDismiss) {
        Column(Modifier.padding(22.dp)) {
            Text("ذخیره در کجا؟", style = Ty.h2, color = p.text)
            Spacer(Modifier.height(14.dp))
            TargetOption(
                Icons.Rounded.Folder, p.info,
                "پوشه‌ی دانلود", "Download/${Config.BACKUP_FOLDER} • رایگان", false, onDownloads
            )
            Spacer(Modifier.height(10.dp))
            TargetOption(
                Icons.Rounded.Storage, p.g1,
                "انتخاب مکان دلخواه", "هر پوشه یا حافظه‌ای که بخواهید",
                !isPaid, if (isPaid) onCustom else onLocked
            )
        }
    }
}

@Composable
private fun TargetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color,
    title: String, sub: String, locked: Boolean, onClick: () -> Unit
) {
    val p = pal
    AppCard(Modifier.fillMaxWidth().alpha(if (locked) 0.5f else 1f), corner = 18.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.bodyM, color = p.text)
                Text(sub, style = Ty.small, color = p.textDim)
            }
            if (locked) Icon(Icons.Rounded.Lock, null, tint = p.warn, modifier = Modifier.size(20.dp))
        }
    }
}

/** Shown after a backup file was opened: what is inside, and what to restore. */
@Composable
fun ImportDialog(
    parsed: ParsedBackup,
    isPaid: Boolean,
    onConfirm: (Set<Section>) -> Unit,
    onCustom: () -> Unit,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit
) {
    val p = pal
    var chosen by remember { mutableStateOf(parsed.present) }
    AppDialog(onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
            Text("بازیابی از فایل پشتیبان", style = Ty.h2, color = p.text)
            if (parsed.createdAt > 0) {
                Text("ساخته‌شده در ${formatDateTime(parsed.createdAt)}", style = Ty.small, color = p.textDim)
            }
            Spacer(Modifier.height(4.dp))
            Text("محتوای این فایل:", style = Ty.small, color = p.textDim)
            Spacer(Modifier.height(10.dp))
            Section.values().filter { it in parsed.present }.forEach { s ->
                val on = s in chosen
                val n = parsed.data.count(s)
                val people = parsed.data.people(s)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FancyCheck(on, { chosen = if (on) chosen - s else chosen + s })
                    Spacer(Modifier.width(12.dp))
                    IconBadge(s.icon(), s.tint(), 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = Ty.bodyM, color = p.text)
                        Text(
                            when (s) {
                                Section.CONTACTS -> "${n.fa()} مخاطب"
                                Section.CALLS -> "${n.fa()} تماس از ${people.fa()} نفر"
                                Section.SMS -> "${n.fa()} پیامک از ${people.fa()} نفر"
                            },
                            style = Ty.small, color = p.textDim
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "موارد تکراری (که از قبل در گوشی هست) وارد نمی‌شوند؛ فقط موارد جدید اضافه می‌شوند و در پایان گزارش کامل می‌بینید.",
                style = Ty.small, color = p.textDim
            )
            if (Section.SMS in parsed.present) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای بازیابی پیامک، اندروید از شما می‌خواهد این برنامه را موقتاً «برنامه‌ی پیش‌فرض پیامک» کنید. " +
                        "بعد از پایان کار دوباره برنامه‌ی قبلی را انتخاب کنید.",
                    style = Ty.small, color = p.warn
                )
            }
            Spacer(Modifier.height(16.dp))
            GradientButton(
                "بازیابی همه‌ی موارد انتخاب‌شده", { onConfirm(chosen) },
                Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty(), icon = Icons.Rounded.Restore
            )
            Spacer(Modifier.height(8.dp))
            SoftButton(
                "انتخاب دلخواه افراد و موارد",
                { if (isPaid) onCustom() else onUpgrade() },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune, locked = !isPaid
            )
            Spacer(Modifier.height(8.dp))
            SoftButton("انصراف", onCancel, Modifier.fillMaxWidth())
        }
    }
}
