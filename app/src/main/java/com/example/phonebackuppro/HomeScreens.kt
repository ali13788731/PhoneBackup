package com.example.phonebackuppro

import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
//  Permission screen
// ---------------------------------------------------------------------------------------------

@Composable
fun PermissionScreen(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val p = pal
    val pulse by rememberInfiniteTransition(label = "perm").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "permGlow"
    )
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(p.brush),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Security, null, tint = Color.White.copy(alpha = 0.6f + pulse * 0.4f), modifier = Modifier.size(50.dp)) }
        Spacer(Modifier.height(22.dp))
        Reveal(0) { Text("دسترسی‌های لازم", style = Ty.title, color = p.text) }
        Reveal(1) {
            Text(
                "برای پشتیبان‌گیری، برنامه به این سه بخش نیاز دارد:",
                style = Ty.body, color = p.textDim, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(22.dp))
        PermRow(2, Icons.Rounded.Contacts, p.g1, "مخاطبین", "خواندن و بازیابی نام‌ها و شماره‌ها")
        Spacer(Modifier.height(10.dp))
        PermRow(3, Icons.Rounded.Call, p.info, "تاریخچه تماس‌ها", "خواندن و بازیابی تماس‌های ورودی و خروجی")
        Spacer(Modifier.height(10.dp))
        PermRow(4, Icons.Rounded.Sms, p.success, "پیامک‌ها", "خواندن و بازیابی پیامک‌های دریافتی و ارسالی")
        Spacer(Modifier.height(18.dp))
        Reveal(5) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null, tint = p.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "اطلاعات شما هیچ‌جا ارسال نمی‌شود و فقط در فایل پشتیبان خودتان می‌ماند.",
                    style = Ty.small, color = p.textDim
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Reveal(6) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (denied) {
                    Text(
                        "دسترسی داده نشد. بدون آن برنامه نمی‌تواند کار کند. از تنظیمات برنامه، دسترسی‌ها را فعال کنید.",
                        style = Ty.small, color = p.warn, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton("باز کردن تنظیمات برنامه", onOpenSettings, Modifier.fillMaxWidth(), icon = Icons.Rounded.Settings)
                    Spacer(Modifier.height(10.dp))
                    SoftButton("دوباره درخواست بده", onRequest, Modifier.fillMaxWidth())
                } else {
                    GradientButton("اعطای دسترسی", onRequest, Modifier.fillMaxWidth(), icon = Icons.Rounded.Security)
                }
            }
        }
    }
}

@Composable
private fun PermRow(i: Int, icon: ImageVector, tint: Color, title: String, sub: String) {
    Reveal(i) {
        AppCard(Modifier.fillMaxWidth(), corner = 20.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = Ty.bodyM, color = pal.text)
                    Text(sub, style = Ty.small, color = pal.textDim)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Home
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onFullBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpen: (Section) -> Unit,
    onUpdate: () -> Unit
) {
    val p = pal
    val d = vm.data
    val paid = vm.plan.isPaid
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (vm.updateAvailable) stagger(1) {
            AppCard(Modifier.fillMaxWidth(), corner = 20.dp, onClick = onUpdate) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Refresh, p.success)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("نسخه‌ی جدید آماده است", style = Ty.bodyM, color = p.text)
                        Text("برای به‌روزرسانی در کافه‌بازار لمس کنید.", style = Ty.small, color = p.textDim)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = p.textDim, modifier = Modifier.size(22.dp))
                }
            }
        }

        stagger(2) { HeroCard(vm, paid, onFullBackup, onRestore) }

        stagger(3) {
            Text("یا فقط یک بخش را پشتیبان بگیرید", style = Ty.bodyM, color = p.textDim, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }

        stagger(4) { SectionRow(Icons.Rounded.Contacts, p.g1, "مخاطبین", "نام، شماره و ایمیل", d?.contacts?.size, vm.loading) { onOpen(Section.CONTACTS) } }
        stagger(5) { SectionRow(Icons.Rounded.Call, p.info, "تماس‌ها", "تاریخچه‌ی تماس‌ها", d?.calls?.size, vm.loading) { onOpen(Section.CALLS) } }
        stagger(6) { SectionRow(Icons.Rounded.Sms, p.success, "پیامک‌ها", "پیام‌های دریافتی و ارسالی", d?.sms?.size, vm.loading) { onOpen(Section.SMS) } }

    }
}

private fun LazyListScope.stagger(i: Int, content: @Composable () -> Unit) {
    item(key = "home$i") { Reveal(i) { content() } }
}

@Composable
private fun HeroCard(vm: MainViewModel, paid: Boolean, onFullBackup: () -> Unit, onRestore: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(30.dp)
    val drift by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "heroT"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.brush)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.8f, center = Offset(w * (0.08f + 0.06f * drift), h * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.08f), radius = h * 0.55f, center = Offset(w * (0.95f - 0.05f * drift), h * 1.0f))
            }
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Backup, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("پشتیبان کامل", style = Ty.h2, color = Color.White)
                    Text(
                        if (vm.settings.lastBackup > 0) "آخرین پشتیبان: ${formatDateTime(vm.settings.lastBackup)}"
                        else "هنوز پشتیبانی نگرفته‌اید",
                        style = Ty.small, color = Color.White.copy(alpha = 0.85f), maxLines = 1
                    )
                }
                if (!paid) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("پریمیوم", style = Ty.smallM, color = Color.White, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "مخاطبین، تماس‌ها و پیامک‌ها را یک‌جا و با یک لمس در یک فایل ذخیره کنید.",
                style = Ty.body, color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(16.dp))
            WhiteButton(
                "ساخت پشتیبان کامل", if (paid) Icons.Rounded.Backup else Icons.Rounded.Lock,
                onFullBackup, Modifier.fillMaxWidth().alpha(if (paid) 1f else 0.55f), filled = true, tint = p.g1
            )
            Spacer(Modifier.height(10.dp))
            WhiteButton("بازیابی از فایل", Icons.Rounded.Restore, onRestore, Modifier.fillMaxWidth(), filled = false, tint = p.g1)
        }
    }
}

@Composable
private fun WhiteButton(
    text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, filled: Boolean, tint: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .border(1.dp, if (filled) Color.Transparent else Color.White.copy(alpha = 0.5f), shape)
            .bounceClick(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (filled) tint else Color.White
        Icon(icon, null, tint = c, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text, style = Ty.bodyM.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun SectionRow(
    icon: ImageVector, tint: Color, title: String, sub: String,
    count: Int?, loading: Boolean, onClick: () -> Unit
) {
    val p = pal
    AppCard(Modifier.fillMaxWidth(), corner = 22.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.bodyM, color = p.text)
                Text(sub, style = Ty.small, color = p.textDim)
            }
            if (count == null || (loading && count == 0)) {
                Box(Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(8.dp)).background(p.cardHigh).shimmer())
            } else {
                CountText(count, Ty.h2, p.text)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronLeft, null, tint = p.textDim, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, onClick: () -> Unit, size: Dp = 42.dp) {
    val p = pal
    Box(
        Modifier.size(size).clip(CircleShape).background(p.cardHigh).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = p.text, modifier = Modifier.size(size * 0.5f)) }
}

/** Shown instead of the whole app when the installed version is older than the allowed minimum. */
@Composable
fun ForceUpdateScreen(onUpdate: () -> Unit) {
    val p = pal
    val pulse by rememberInfiniteTransition(label = "upd").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "updA"
    )
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(150.dp).clip(RoundedCornerShape(44.dp)).background(p.brush),
            contentAlignment = Alignment.Center
        ) { LogoCanvas(Modifier.size(150.dp), glow = pulse * 0.4f) }
        Spacer(Modifier.height(26.dp))
        Text("به‌روزرسانی الزامی است", style = Ty.title, color = p.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "این نسخه از برنامه دیگر پشتیبانی نمی‌شود. برای ادامه، نسخه‌ی جدید را از کافه‌بازار نصب کنید. اطلاعات شما حفظ می‌شود.",
            style = Ty.body, color = p.textDim, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(26.dp))
        GradientButton("به‌روزرسانی در کافه‌بازار", onUpdate, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
    }
}
