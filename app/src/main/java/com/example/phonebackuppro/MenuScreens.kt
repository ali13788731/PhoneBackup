package com.example.phonebackuppro

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
//  Menu (the "منو" tab): every secondary screen is reached from here
// ---------------------------------------------------------------------------------------------

@Composable
fun MenuScreen(
    vm: MainViewModel,
    onPlan: () -> Unit,
    onRestore: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    val p = pal
    val paid = vm.plan.isPaid
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text("منو", style = Ty.title, color = p.text)
        Spacer(Modifier.height(12.dp))

        Reveal(0) {
            AppCard(Modifier.fillMaxWidth(), corner = 24.dp, onClick = onPlan) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(if (paid) p.brush else androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.cardHigh, p.cardHigh))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Star, null, tint = if (paid) Color.White else p.textDim, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (paid) "اشتراک پریمیوم فعال" else "نسخه‌ی رایگان", style = Ty.bodyM, color = p.text)
                        Text(
                            if (paid) "جزئیات خرید و امکانات" else "ارتقا فقط ${Config.PRICE_TOMAN.fa()} تومان",
                            style = Ty.small, color = p.textDim
                        )
                    }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = p.textDim, modifier = Modifier.size(22.dp))
                }
            }
        }

        MenuGroup("پشتیبان‌ها")
        Reveal(1) {
            MenuCard {
                MenuRow(
                    Icons.Rounded.Restore, p.success, "بازیابی از فایل",
                    "پشتیبان‌های برنامه و فایل‌های پیداشده در گوشی" +
                        (if (vm.backups.isNotEmpty()) " • ${vm.backups.size.fa()} پشتیبان برنامه" else ""),
                    onRestore
                )
            }
        }

        MenuGroup("برنامه")
        Reveal(2) {
            MenuCard {
                MenuRow(Icons.Rounded.Star, p.warn, "پلن و اشتراک", "وضعیت، جزئیات خرید و امکانات", onPlan)
                MenuDivider()
                MenuRow(Icons.Rounded.Settings, p.textDim, "تنظیمات", "ظاهر و رفتار برنامه", onSettings)
                MenuDivider()
                MenuRow(Icons.Rounded.Info, p.g1, "درباره‌ی ما", "نسخه، سازنده و حریم خصوصی", onAbout)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuGroup(text: String) {
    Text(text, style = Ty.smallM, color = pal.textDim, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 6.dp))
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), corner = 24.dp) { Column { content() } }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(pal.stroke))
}

@Composable
private fun MenuRow(
    icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit, locked: Boolean = false
) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ty.bodyM, color = p.text)
            Text(sub, style = Ty.small, color = p.textDim)
        }
        if (locked) Icon(Icons.Rounded.Lock, null, tint = p.warn, modifier = Modifier.size(20.dp))
        else Icon(Icons.Rounded.ChevronLeft, null, tint = p.textDim, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------------------------
//  Plan & subscription
// ---------------------------------------------------------------------------------------------

private val FreeFeatures = listOf(
    "پشتیبان‌گیری از همه‌ی مخاطبین" to true,
    "پشتیبان‌گیری از همه‌ی تماس‌ها" to true,
    "پشتیبان‌گیری از همه‌ی پیامک‌ها" to true,
    "بازیابی فایل پشتیبان (جستجوی عمیق خودکار)" to true,
    "پشتیبان کامل (هر سه با هم)" to false,
    "انتخاب دلخواه موارد" to false,
    "بازیابی دلخواه (انتخاب افراد)" to false,
    "جستجو و فیلتر پیشرفته" to false,
    "جزئیات تماس‌ها و متن پیامک‌ها" to false,
    "ذخیره در هر مسیر دلخواه" to false,
    "اشتراک‌گذاری فایل پشتیبان" to false
)

@Composable
fun PlanScreen(vm: MainViewModel, billing: BillingManager, onBack: () -> Unit, onUpgrade: () -> Unit) {
    val p = pal
    val paid = vm.plan.isPaid
    val info = vm.plan.info
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("پلن و اشتراک", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Reveal(0) {
                AppCard(Modifier.fillMaxWidth(), corner = 26.dp) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(p.brush),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(if (paid) "پریمیوم فعال است" else "نسخه‌ی رایگان", style = Ty.title, color = p.text)
                        Text(
                            if (paid) "همه‌ی امکانات برنامه برای شما باز است. ممنون از حمایتتان 💜"
                            else "با ${Config.PRICE_TOMAN.fa()} تومان همه‌ی امکانات باز می‌شود.",
                            style = Ty.small, color = p.textDim, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        if (!paid) GradientButton("خرید پریمیوم · ${Config.PRICE_TOMAN.fa()} تومان", onUpgrade, Modifier.fillMaxWidth(), icon = Icons.Rounded.Star)
                        else SoftButton(
                            if (billing.working) "در حال بررسی…" else "بررسی دوباره از کافه‌بازار",
                            { billing.restore() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Restore
                        )
                        if (!paid) {
                            Spacer(Modifier.height(8.dp))
                            SoftButton("قبلاً خریده‌ام (بازیابی خرید)", { billing.restore() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Restore)
                        }
                    }
                }
            }

            if (paid) {
                Reveal(1) {
                    AppCard(Modifier.fillMaxWidth(), corner = 24.dp) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("جزئیات خرید", style = Ty.h2, color = p.text, modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow("نوع", if (Config.USE_SUBSCRIPTION) "اشتراک" else "خرید یک‌باره")
                            DetailRow("محصول", info.productId.ifBlank { Config.PREMIUM_SKU }, latin = true)
                            DetailRow("مبلغ", "${Config.PRICE_TOMAN.fa()} تومان")
                            DetailRow("تاریخ خرید", if (info.purchaseTime > 0) formatDateTime(info.purchaseTime) else "—")
                            DetailRow("شماره سفارش", info.orderId.ifBlank { "—" }, latin = true)
                            DetailRow("وضعیت", "فعال ✅")
                            DetailRow(
                                "آخرین بررسی از بازار",
                                if (info.verifiedAt > 0) formatDateTime(info.verifiedAt) else "—", last = true
                            )
                        }
                    }
                }
                Text(
                    "این اطلاعات از کافه‌بازار خوانده می‌شود. برای مدیریت یا لغو اشتراک، برنامه‌ی کافه‌بازار ← حساب کاربری ← اشتراک‌ها را باز کنید.",
                    style = Ty.small, color = p.textDim, modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            Reveal(2) {
                AppCard(Modifier.fillMaxWidth(), corner = 24.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مقایسه‌ی امکانات", style = Ty.h2, color = p.text)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Spacer(Modifier.weight(1f))
                            Text("رایگان", style = Ty.smallM, color = p.textDim, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                            Text("پریمیوم", style = Ty.smallM, color = p.warn, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                        }
                        FreeFeatures.forEach { (name, free) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = Ty.small, color = p.text, modifier = Modifier.weight(1f))
                                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Mark(free) }
                                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Mark(true) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(navBottom() + 24.dp))
        }
    }
}

@Composable
private fun Mark(on: Boolean) {
    val p = pal
    if (on) Icon(Icons.Rounded.Check, null, tint = p.success, modifier = Modifier.size(20.dp))
    else Icon(Icons.Rounded.Close, null, tint = p.textDim.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
}

@Composable
private fun DetailRow(label: String, value: String, latin: Boolean = false, last: Boolean = false) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Ty.small, color = p.textDim)
        Spacer(Modifier.width(12.dp))
        Text(
            value, style = Ty.bodyM, color = p.text, modifier = Modifier.weight(1f),
            textAlign = TextAlign.End, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
}

// ---------------------------------------------------------------------------------------------
//  About
// ---------------------------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit, onAppSettings: () -> Unit) {
    val p = pal
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("درباره‌ی ما", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            LogoTile(96.dp)
            Text("Phone Backup", style = Ty.title, color = p.text)
            Text(
                "نسخه ${BuildConfig.VERSION_NAME.toFaDigits()}",
                style = Ty.small, color = p.textDim
            )
            Text(
                "پشتیبان‌گیری و بازیابی ساده، سریع و زیبا از مخاطبین، تماس‌ها و پیامک‌های گوشی.",
                style = Ty.body, color = p.text, textAlign = TextAlign.Center
            )
            AppCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Star, p.g1)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("طراحی و ساخت", style = Ty.small, color = p.textDim)
                        Text(Config.DEVELOPER, style = Ty.h2, color = p.text)
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Security, null, tint = p.success, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("حریم خصوصی", style = Ty.bodyM, color = p.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "مخاطبین، تماس‌ها و پیامک‌ها فقط در فایلی که خودتان می‌سازید ذخیره می‌شوند. " +
                                "برنامه فقط برای بررسی نسخه‌ی جدید یک فایل کوچک می‌خواند و هیچ اطلاعاتی از شما ارسال نمی‌کند.",
                            style = Ty.small, color = p.textDim
                        )
                    }
                }
            }
            SoftButton("مدیریت دسترسی‌های برنامه", onAppSettings, Modifier.fillMaxWidth(), icon = Icons.Rounded.Info)
            Spacer(Modifier.height(navBottom() + 24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Subscription history (opened from the "PRO" label of the top bar)
// ---------------------------------------------------------------------------------------------

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onPlan: () -> Unit) {
    val p = pal
    val info = vm.plan.info
    val list = vm.plan.history
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("تاریخچه‌ی اشتراک", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Reveal(0) {
                AppCard(Modifier.fillMaxWidth(), corner = 26.dp) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(p.brush),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("اشتراک پریمیوم فعال", style = Ty.h2, color = p.text)
                            Text(
                                if (info.verifiedAt > 0) "آخرین بررسی از بازار: ${formatDateTime(info.verifiedAt)}" else "—",
                                style = Ty.small, color = p.textDim
                            )
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                Text(
                    "هنوز رکوردی از خرید ذخیره نشده است. بعد از «بررسی دوباره از کافه‌بازار» در صفحه‌ی پلن، خرید شما اینجا ثبت می‌شود.",
                    style = Ty.body, color = p.textDim, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
            list.forEachIndexed { i, e ->
                Reveal(i + 1) {
                    AppCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.Receipt, if (i == 0) p.success else p.textDim, 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (e.time > 0) formatDateTime(e.time) else "—",
                                    style = Ty.bodyM, color = p.text, modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (i == 0) "فعال" else "قبلی", style = Ty.smallM,
                                    color = if (i == 0) p.success else p.textDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background((if (i == 0) p.success else p.textDim).copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
                            DetailRow("محصول", e.productId.ifBlank { Config.PREMIUM_SKU }, latin = true)
                            DetailRow("شماره سفارش", e.orderId.ifBlank { "—" }, latin = true)
                            DetailRow("مبلغ", "${Config.PRICE_TOMAN.fa()} تومان", last = true)
                        }
                    }
                }
            }
            SoftButton("جزئیات پلن و امکانات", onPlan, Modifier.fillMaxWidth(), icon = Icons.Rounded.Star)
            Text(
                "این اطلاعات از کافه‌بازار خوانده می‌شود. برای لغو یا مدیریت اشتراک، برنامه‌ی کافه‌بازار ← حساب کاربری ← اشتراک‌ها را باز کنید.",
                style = Ty.small, color = p.textDim, modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(navBottom() + 24.dp))
        }
    }
}
