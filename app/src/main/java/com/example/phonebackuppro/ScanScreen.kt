package com.example.phonebackuppro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * "Restore from file": searches the whole phone (Telegram downloads included) for backup files,
 * shows what each one holds and lets the user restore any of them with one tap.
 */
@Composable
fun ScanScreen(
    vm: MainViewModel, onBack: () -> Unit, onPickManual: () -> Unit,
    onShare: (Uri) -> Unit, onLockedShare: () -> Unit
) {
    val p = pal
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(BackupScanner.hasAccess(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = BackupScanner.hasAccess(ctx) }
    LaunchedEffect(access) {
        if (access && !vm.scanRunning && !vm.scanDone) vm.startScan()
    }

    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = BackupScanner.hasAccess(ctx)
    }
    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val pkg = Uri.parse("package:${ctx.packageName}")
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
            } catch (e: Exception) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else legacy.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val sorted = remember(vm.found) {
        vm.found.distinctBy { it.path }
            .distinctBy { listOf(it.name, it.size, it.modified, it.folder) }
            .sortedByDescending { it.modified }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("بازیابی از فایل", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = navBottom() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                Reveal(0) {
                    if (!access) AccessCard { requestAccess() } else StatusCard(vm, sorted.size)
                }
            }
            if (access) {
                items(sorted, key = { it.path }) { f ->
                    FoundCard(
                        f, vm.plan.isPaid,
                        onRestore = { vm.openFound(f) },
                        onShare = { vm.shareUriOf(f)?.let(onShare) ?: run { vm.message = "این فایل را نمی‌شود مستقیم ارسال کرد. آن را با فایل‌منیجر یا برنامه‌ی مبدأ ارسال کنید." } },
                        onLockedShare = onLockedShare,
                        onDelete = { vm.deleteFound(f) }
                    )
                }
                if (vm.scanDone && sorted.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "هیچ فایل بازیابی‌ای در حافظه پیدا نشد. اگر فایل را جای دیگری دارید، آن را دستی انتخاب کنید.",
                            style = Ty.body, color = p.textDim, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
            item(key = "manual") {
                SoftButton("انتخاب دستی فایل از حافظه", onPickManual, Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen)
            }
        }
    }
}

@Composable
private fun AccessCard(onRequest: () -> Unit) {
    val p = pal
    AppCard(Modifier.fillMaxWidth(), corner = 26.dp) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(Icons.Rounded.Security, p.g1, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text("جستجوی عمیق در کل گوشی", style = Ty.h2, color = p.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "برنامه کل حافظه را (از جمله پوشه‌ی تلگرام و دانلودها) می‌گردد و فایل‌های بازیابی را خودکار پیدا می‌کند. " +
                    "برای این کار باید دسترسی «مدیریت همه‌ی فایل‌ها» را بدهید. فقط فایل‌های پشتیبان خوانده می‌شوند و هیچ اطلاعاتی ارسال نمی‌شود.",
                style = Ty.small, color = p.textDim, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            GradientButton("اعطای دسترسی و شروع جستجو", onRequest, Modifier.fillMaxWidth(), icon = Icons.Rounded.Search)
        }
    }
}

@Composable
private fun StatusCard(vm: MainViewModel, foundCount: Int) {
    val p = pal
    val pr = vm.scanProgress
    AppCard(Modifier.fillMaxWidth(), corner = 26.dp) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (vm.scanRunning) {
                RingProgress(-1f, Modifier.size(96.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = p.g1, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("در حال جستجوی عمیق در کل گوشی…", style = Ty.h2, color = p.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pr.path.isBlank()) " " else pr.path.ltr(),
                    style = Ty.small, color = p.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            } else {
                if (foundCount > 0) ResultMark(true, Modifier.size(84.dp))
                else IconBadge(Icons.Rounded.Search, p.textDim, 64.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vm.scanStopped) "جستجو متوقف شد" else "جستجو تمام شد",
                    style = Ty.h2, color = p.text
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanStat(pr.folders.fa(), "پوشه بررسی شد", p.info, Modifier.weight(1f))
                ScanStat(pr.files.fa(), "فایل بررسی شد", p.g1, Modifier.weight(1f))
                ScanStat(foundCount.fa(), "پشتیبان پیدا شد", p.success, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (vm.scanRunning) {
                SoftButton("توقف جستجو", { vm.stopScan() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Close)
            } else {
                SoftButton("جستجوی دوباره", { vm.startScan() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
            }
        }
    }
}

@Composable
private fun ScanStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = Ty.h2, color = color, maxLines = 1)
        Text(label, style = Ty.small, color = pal.textDim, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FoundCard(
    f: FoundBackup, paid: Boolean,
    onRestore: () -> Unit, onShare: () -> Unit, onLockedShare: () -> Unit, onDelete: () -> Unit
) {
    val p = pal
    AppCard(Modifier.fillMaxWidth(), corner = 22.dp, onClick = onRestore) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (f.isVcf) Icons.Rounded.Contacts else Icons.Rounded.Description,
                    if (f.source == "تلگرام") p.info else p.g1, 46.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name.ltr(), style = Ty.bodyM, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("در آدرس: ${f.folder.ltr()}", style = Ty.small, color = p.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    f.source, style = Ty.smallM, color = p.g1, maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.g1.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
            Spacer(Modifier.height(8.dp))
            if (f.contacts > 0) StatLine(Icons.Rounded.Contacts, p.g1, "${f.contacts.fa()} مخاطب")
            if (f.calls > 0) StatLine(Icons.Rounded.Call, p.info, "${f.calls.fa()} تاریخچه‌ی تماس از ${f.callPeople.fa()} نفر")
            if (f.sms > 0) StatLine(Icons.Rounded.Sms, p.success, "${f.sms.fa()} پیامک از ${f.smsPeople.fa()} نفر")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatSize(f.size)} • " +
                        if (f.createdAt > 0) "ساخته‌شده ${formatDateTime(f.createdAt)}" else "تغییر ${formatDateTime(f.modified)}",
                    style = Ty.small, color = p.textDim, modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(p.brush).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Restore, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("بازیابی", style = Ty.smallM, color = Color.White, maxLines = 1)
                }
            }
            if (f.source == BackupScanner.APP_SOURCE) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(
                        "ارسال", if (paid) onShare else onLockedShare, Modifier.weight(1f),
                        icon = if (paid) Icons.Rounded.Share else Icons.Rounded.Lock, compact = true
                    )
                    SoftButton("حذف", onDelete, Modifier.weight(1f), icon = Icons.Rounded.Delete, tint = p.danger, compact = true)
                }
            }
        }
    }
}

@Composable
private fun StatLine(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = Ty.bodyM, color = pal.text)
    }
}
