package com.example.phonebackuppro

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen header: title on the right, round back button on the left (arrow points left). */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = Ty.title, color = pal.text, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.ArrowBack, onBack)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = Ty.smallM, color = pal.textDim, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
}

// ---------------------------------------------------------------------------------------------
//  Settings
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val p = pal
    val s = vm.settings
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("تنظیمات", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GroupTitle("ظاهر")
            Reveal(1) {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("حالت نمایش", style = Ty.bodyM, color = p.text)
                        Spacer(Modifier.height(10.dp))
                        Segmented(
                            listOf("خودکار" to Icons.Rounded.BrightnessMedium, "روشن" to Icons.Rounded.LightMode, "تیره" to Icons.Rounded.DarkMode),
                            s.themeMode, s::updateThemeMode
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, null, tint = p.textDim, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("رنگ برنامه", style = Ty.bodyM, color = p.text)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Accents.forEachIndexed { i, a ->
                                AccentDot(a, i == s.accent) { s.updateAccent(i) }
                            }
                        }
                    }
                }
            }

            GroupTitle("رفتار برنامه")
            Reveal(2) {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SwitchRow(Icons.Rounded.PlayArrow, "انیمیشن‌ها", "افکت‌های حرکتی و لوگوی ورود", s.animations, s::updateAnimations)
                        SwitchRow(Icons.Rounded.Vibration, "لرزش لمسی", "یک ضربه‌ی ریز هنگام لمس دکمه‌ها", s.haptics, s::updateHaptics)
                        SwitchRow(Icons.Rounded.DoneAll, "انتخاب همه به‌صورت پیش‌فرض", "برای پلن پریمیوم", s.selectAllByDefault, s::updateSelectAllByDefault)
                        SwitchRow(Icons.Rounded.Notifications, "یادآوری اشتراک", "هر چند ساعت یک‌بار، اگر پریمیوم ندارید و جزئیاتش را می‌بینید", s.reminders, s::updateReminders)
                        SwitchRow(Icons.Rounded.Check, "رد کردن موارد تکراری", "هنگام بازیابی، موارد موجود دوباره اضافه نمی‌شوند", s.skipDuplicates, s::updateSkipDuplicates)
                    }
                }
            }

            GroupTitle("خروجی")
            Reveal(3) {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("فرمت خروجی مخاطبین", style = Ty.bodyM, color = p.text)
                        Text(
                            "JSON با همین برنامه بازیابی می‌شود؛ VCF را هر گوشی و برنامه‌ای می‌خواند.",
                            style = Ty.small, color = p.textDim
                        )
                        Spacer(Modifier.height(10.dp))
                        Segmented(
                            listOf("JSON" to Icons.Rounded.Folder, "VCF" to Icons.Rounded.Share),
                            if (s.contactsVcf) 1 else 0, { s.updateContactsVcf(it == 1) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(navBottom() + 28.dp))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ty.bodyM, color = p.text)
            Text(sub, style = Ty.small, color = p.textDim)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.g1, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun Segmented(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.cardHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) p.g1 else Color.Transparent)
                    .bounceClick { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (on) Color.White else p.textDim, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = Ty.smallM, color = if (on) Color.White else p.text)
            }
        }
    }
}

@Composable
private fun AccentDot(a: Accent, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(0.6f, 400f), label = "ring")
    Box(
        Modifier
            .size(44.dp)
            .border(ring, if (selected) pal.text else Color.Transparent, CircleShape)
            .padding(ring + 2.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(a.c1, a.c2)))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}
