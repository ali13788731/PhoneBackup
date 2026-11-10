package com.example.phonebackuppro

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val PERMS = arrayOf(
    Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG
)

private fun hasPerms(ctx: Context) =
    PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        billing = BillingManager(this, vm.plan)
        if (!BuildConfig.DEMO_PREMIUM) billing.start()
        Notifier.channels(this)
        Reminders.schedule(this)
        setContent {
            PhoneBackupTheme(vm.settings) { AppRoot(vm, billing) }
        }
    }

    override fun onDestroy() {
        billing.stop()
        super.onDestroy()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home),
    Contacts("مخاطبین", Icons.Rounded.Contacts),
    Calls("تماس‌ها", Icons.Rounded.Call),
    Sms("پیامک‌ها", Icons.Rounded.Sms),
    Menu("منو", Icons.Rounded.Menu) // reached from the top bar, not from the bottom nav
}

private val Tab.section: Section?
    get() = when (this) {
        Tab.Home, Tab.Menu -> null
        Tab.Contacts -> Section.CONTACTS
        Tab.Calls -> Section.CALLS
        Tab.Sms -> Section.SMS
    }

private fun Section.tab() = when (this) {
    Section.CONTACTS -> Tab.Contacts
    Section.CALLS -> Tab.Calls
    Section.SMS -> Tab.Sms
}

private const val SUB_NONE = 0
private const val SUB_SETTINGS = 1
private const val SUB_PLAN = 3
private const val SUB_ABOUT = 4
private const val SUB_SCAN = 5
private const val SUB_HISTORY = 6

@Composable
private fun AppRoot(vm: MainViewModel, billing: BillingManager) {
    val ctx = LocalContext.current
    val anim = vm.settings.animations

    var splashDone by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasPerms(ctx)) }
    var denied by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var sub by rememberSaveable { mutableIntStateOf(SUB_NONE) }
    var showPaywall by remember { mutableStateOf(false) }
    var saveFor by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingReq by remember { mutableStateOf<ExportRequest?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPerms(ctx) }
    LaunchedEffect(Unit) { vm.checkUpdate() }
    LaunchedEffect(granted) {
        if (granted) {
            vm.load(); vm.refreshBackups()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(vm.plan.isPaid) {
        if (vm.plan.isPaid) NotificationManagerCompat.from(ctx).cancel(Notifier.ID_PROMO)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPerms(ctx); denied = !granted
    }
    val onSaved = { uri: Uri? ->
        val req = pendingReq
        pendingReq = null
        if (uri != null && req != null) vm.export(req, Target.Custom(uri))
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), onSaved)
    val saveVcf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard"), onSaved)
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.openBackupFile(uri)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.runImport()
    }

    fun pickRestoreFile() = openLauncher.launch(arrayOf("application/json", "text/x-vcard", "text/vcard", "*/*"))

    /** The sections are staged: ask for the SMS role first when messages are going to be written. */
    fun startRestore() {
        if (vm.needsSmsRole()) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else vm.runImport()
    }

    BackHandler(enabled = splashDone && granted && (sub != SUB_NONE || tab != Tab.Home)) {
        if (sub != SUB_NONE) sub = SUB_NONE else tab = Tab.Home
    }
    BackHandler(enabled = vm.picking) { vm.closePicker() }

    AppBackground {
        AnimatedContent(
            targetState = splashDone,
            transitionSpec = {
                if (anim) fadeIn(tween(700)) togetherWith fadeOut(tween(500))
                else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "root"
        ) { done ->
            if (!done) {
                SplashScreen(animated = anim) { splashDone = true }
            } else if (vm.updateRequired) {
                ForceUpdateScreen(onUpdate = { UpdateManager.openStore(ctx) })
            } else if (granted && vm.picking && vm.pendingImport != null) {
                RestorePicker(
                    parsed = vm.pendingImport!!,
                    onConfirm = { data, sections ->
                        vm.confirmCustom(data, sections)
                        startRestore()
                    },
                    onClose = { vm.closePicker() }
                )
            } else if (!granted) {
                PermissionScreen(
                    denied = denied,
                    onRequest = { permLauncher.launch(PERMS) },
                    onOpenSettings = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        )
                    }
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).statusBarsPadding()) {
                    AppTopBar(
                        menuOpen = tab == Tab.Menu,
                        paid = vm.plan.isPaid,
                        onMenu = { tab = if (tab == Tab.Menu) Tab.Home else Tab.Menu },
                        // free -> plan / upgrade page, pro -> subscription history
                        onBadge = { sub = if (vm.plan.isPaid) SUB_HISTORY else SUB_PLAN }
                    )
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = tab.ordinal,
                            transitionSpec = {
                                if (anim) (fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 24 }) togetherWith fadeOut(tween(160))
                                else EnterTransition.None togetherWith ExitTransition.None
                            },
                            label = "screens"
                        ) { key ->
                            when (key) {
                                Tab.Home.ordinal -> HomeScreen(
                                    vm,
                                    onFullBackup = {
                                        if (vm.plan.isPaid) saveFor = ExportRequest(Section.values().toSet(), null, refresh = true)
                                        else showPaywall = true
                                    },
                                    onRestore = { sub = SUB_SCAN },
                                    onOpen = { tab = it.tab() },
                                    onUpdate = { UpdateManager.openStore(ctx) }
                                )
                                Tab.Contacts.ordinal, Tab.Calls.ordinal, Tab.Sms.ordinal -> {
                                    val section = Tab.values()[key].section!!
                                    ListScreen(
                                        vm, section,
                                        onExport = { s ->
                                            saveFor = ExportRequest(
                                                setOf(s), if (vm.plan.canSelect) vm.selectedKeys(s) else null
                                            )
                                        },
                                        onUpgrade = { showPaywall = true }
                                    )
                                }
                                Tab.Menu.ordinal -> MenuScreen(
                                    vm,
                                    onPlan = { sub = SUB_PLAN },
                                    onRestore = { sub = SUB_SCAN },
                                    onSettings = { sub = SUB_SETTINGS },
                                    onAbout = { sub = SUB_ABOUT }
                                )
                                else -> Unit
                            }
                        }
                    }
                    }
                    BottomNav(tab, { tab = it })
                }
                // Sub pages (plan, history, settings, restore...) sit ON TOP of the untouched main screen,
                // so nothing underneath re-lays out or jumps while they fade in.
                AnimatedContent(
                    targetState = sub,
                    transitionSpec = {
                        if (anim) fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                        else EnterTransition.None togetherWith ExitTransition.None
                    },
                    label = "sub",
                    modifier = Modifier.fillMaxSize()
                ) { key ->
                    if (key == SUB_NONE) Spacer(Modifier.fillMaxSize())
                    else Box(
                        Modifier.fillMaxSize().background(pal.bg).pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    ) {
                        when (key) {
                                    SUB_SCAN -> ScanScreen(
                                        vm,
                                        onBack = { sub = SUB_NONE },
                                        onPickManual = { pickRestoreFile() },
                                        onShare = { shareFile(ctx, it) },
                                        onLockedShare = { showPaywall = true }
                                    )
                                    SUB_SETTINGS -> SettingsScreen(vm, onBack = { sub = SUB_NONE })
                                    SUB_PLAN -> PlanScreen(
                                        vm, billing,
                                        onBack = { sub = SUB_NONE },
                                        onUpgrade = { showPaywall = true }
                                    )
                                    SUB_HISTORY -> HistoryScreen(
                                        vm,
                                        onBack = { sub = SUB_NONE },
                                        onPlan = { sub = SUB_PLAN }
                                    )
                                    SUB_ABOUT -> AboutScreen(
                                        onBack = { sub = SUB_NONE },
                                        onAppSettings = {
                                            ctx.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                            )
                                        }
                                    )
                            else -> Unit
                        }
                    }
                }
                }
            }
        }
    }

    // ---- overlays -----------------------------------------------------------------------------

    saveFor?.let { req ->
        SaveTargetDialog(
            isPaid = vm.plan.isPaid,
            onDownloads = { saveFor = null; vm.export(req, Target.Downloads) },
            onCustom = {
                saveFor = null
                pendingReq = req
                val name = vm.fileNameFor(req)
                if (vm.isVcf(req)) saveVcf.launch(name) else saveJson.launch(name)
            },
            onLocked = { saveFor = null; showPaywall = true },
            onDismiss = { saveFor = null }
        )
    }

    if (vm.importDialogVisible) {
        ImportDialog(
            parsed = vm.pendingImport!!,
            isPaid = vm.plan.isPaid,
            onConfirm = { sections ->
                vm.stageImport(sections)
                startRestore()
            },
            onCustom = { vm.openPicker() },
            onUpgrade = { showPaywall = true },
            onCancel = { vm.cancelImport() }
        )
    }

    JobOverlay(
        job = vm.job,
        canShare = vm.plan.isPaid,
        onDismiss = { vm.dismissJob() },
        onShare = { shareFile(ctx, it) },
        onLockedShare = { showPaywall = true },
        onSmsSettings = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    )

    if (showPaywall) PaywallDialog(billing, vm.plan) { showPaywall = false }
    vm.message?.let { MessageDialog(it) { vm.message = null } }
    billing.message?.let { MessageDialog(it) { billing.message = null } }
}

private fun shareFile(ctx: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "اشتراک‌گذاری پشتیبان").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(chooser) }
}

/** Docked bottom navigation; the selected item grows to show its label. */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.stroke, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .padding(bottom = navBottom()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().filter { it != Tab.Menu }.forEach { t ->
            val on = t == current
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) p.brush else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                    .bounceClick { onSelect(t) }
                    .animateContentSize(tween(260))
                    .padding(horizontal = if (on) 14.dp else 11.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(t.icon, null, tint = if (on) Color.White else p.textDim, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    visible = on,
                    enter = expandHorizontally(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkHorizontally(tween(200)) + fadeOut(tween(120))
                ) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, style = Ty.smallM, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/**
 * Top bar of the main screens: the menu button at the start (right in RTL) and the app logo on
 * a rounded background that sits flush against the left edge of the screen.
 */
@Composable
private fun AppTopBar(menuOpen: Boolean, paid: Boolean, onMenu: () -> Unit, onBadge: () -> Unit) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (menuOpen) p.brush else androidx.compose.ui.graphics.SolidColor(p.cardHigh))
                .bounceClick(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (menuOpen) Icons.Rounded.Close else Icons.Rounded.Menu, null,
                tint = if (menuOpen) Color.White else p.text, modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Phone Backup", style = Ty.h2, color = p.text, maxLines = 1)
            Text("طراحی شده توسط ${Config.DEVELOPER}", style = Ty.small, color = p.textDim, maxLines = 1)
        }
        // End side of an RTL row = the physical left edge: no gap, flat edge against the screen.
        val capsule = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        Box(
            Modifier
                .clip(capsule)
                .background(p.card)
                .border(1.dp, p.stroke, capsule)
                .bounceClick(onClick = onBadge)
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LogoTile(40.dp)
                Spacer(Modifier.height(3.dp))
                // plan label: FREE (grey) or PRO (gradient)
                Text(
                    if (paid) "PRO" else "FREE",
                    style = Ty.smallM.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = if (paid) Color.White else p.textDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (paid) p.brush else androidx.compose.ui.graphics.SolidColor(p.cardHigh))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }
}
