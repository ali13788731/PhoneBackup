package com.example.phonebackuppro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Update control. The app downloads one tiny JSON file (see [Config.UPDATE_JSON_URL]) that contains
 * only version numbers. Nothing about the user or the phone is sent.
 */
object UpdateManager {
    class Info(val latest: Int, val min: Int, val name: String, val notes: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)

    fun current(): Int = BuildConfig.VERSION_CODE

    fun stored(ctx: Context): Info {
        val p = prefs(ctx)
        return Info(p.getInt("latest", 0), p.getInt("min", 0), p.getString("name", "") ?: "", p.getString("notes", "") ?: "")
    }

    /** True when the installed version is older than the minimum the developer allows. */
    fun forced(ctx: Context): Boolean = stored(ctx).min > current()

    fun available(ctx: Context): Boolean = stored(ctx).latest > current()

    /** Shows the update notification right away, but only once for each new version the app learns about. */
    fun notifyOnce(ctx: Context) {
        if (!available(ctx) && !forced(ctx)) return
        val v = maxOf(stored(ctx).latest, stored(ctx).min)
        val p = prefs(ctx)
        if (p.getInt("notified", 0) >= v) return
        p.edit().putInt("notified", v).apply()
        Notifier.channels(ctx)
        Notifier.update(ctx)
    }

    /** Blocking network call: run it off the main thread. Returns true when the file was read. */
    fun check(ctx: Context): Boolean {
        if (Config.UPDATE_JSON_URL.isBlank()) return false
        return try {
            val c = URL(Config.UPDATE_JSON_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            try {
                if (c.responseCode != 200) return false
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                prefs(ctx).edit()
                    .putInt("latest", o.optInt("latestVersionCode", 0))
                    .putInt("min", o.optInt("minVersionCode", 0))
                    .putString("name", o.optString("latestVersionName"))
                    .putString("notes", o.optString("notes"))
                    .apply()
                true
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Opens this app's page in Cafe Bazaar (falls back to the website). */
    fun storeIntent(ctx: Context): Intent {
        val bazaar = Intent(Intent.ACTION_VIEW, Uri.parse("bazaar://details?id=${ctx.packageName}"))
            .setPackage("com.farsitel.bazaar")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/${ctx.packageName}"))
        val ok = ctx.packageManager.resolveActivity(bazaar, 0) != null
        return (if (ok) bazaar else web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openStore(ctx: Context) {
        runCatching { ctx.startActivity(storeIntent(ctx)) }
    }
}

object Notifier {
    const val ID_PROMO = 101
    const val ID_UPDATE = 102
    private const val CH_PROMO = "promo"
    private const val CH_UPDATE = "update"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROMO, "پیشنهاد پریمیوم", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATE, "به‌روزرسانی برنامه", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }

    /** "Buy premium" reminder with the full list of what premium adds. */
    fun promo(ctx: Context) {
        val full = "با پریمیوم (فقط ${Config.PRICE_TOMAN.fa()} تومان) این امکانات اضافه می‌شود:\n" +
            "• پشتیبان کامل از مخاطبین، تماس‌ها و پیامک‌ها با هم\n" +
            "• انتخاب دلخواه مخاطبین، تماس‌ها و پیامک‌ها\n" +
            "• جستجو و فیلتر پیشرفته (تاریخ، نوع تماس، متن پیامک، تعداد و …)\n" +
            "• مشاهده‌ی جزئیات تاریخچه‌ی تماس و متن پیامک‌ها\n" +
            "• ذخیره‌ی پشتیبان در هر مسیر دلخواه\n" +
            "• اشتراک‌گذاری فایل پشتیبان و مدیریت پشتیبان‌های قبلی"
        val n = NotificationCompat.Builder(ctx, CH_PROMO)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("پریمیوم Phone Backup فقط ${Config.PRICE_TOMAN.fa()} تومان")
            .setContentText("پشتیبان کامل، انتخاب دلخواه، جستجوی پیشرفته و …")
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        notify(ctx, ID_PROMO, n)
    }

    fun update(ctx: Context) {
        val forced = UpdateManager.forced(ctx)
        val info = UpdateManager.stored(ctx)
        val body = (if (forced) "این نسخه دیگر پشتیبانی نمی‌شود و تا به‌روزرسانی کار نمی‌کند." else "نسخه‌ی جدید آماده‌ی نصب است.") +
            (if (info.name.isNotBlank()) "\nنسخه‌ی ${info.name.toFaDigits()}" else "") +
            (if (info.notes.isNotBlank()) "\n${info.notes}" else "")
        val pi = PendingIntent.getActivity(ctx, 1, UpdateManager.storeIntent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (forced) "به‌روزرسانی الزامی است" else "نسخه‌ی جدید Phone Backup")
            .setContentText(if (forced) "برای ادامه، برنامه را در کافه‌بازار به‌روز کنید." else "برای به‌روزرسانی لمس کنید.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notify(ctx, ID_UPDATE, n)
    }
}

/** Update check + update notification every [Config.UPDATE_CHECK_HOURS] hours (even when the app is closed). */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Notifier.channels(c)
        withContext(Dispatchers.IO) { UpdateManager.check(c) }
        if (UpdateManager.available(c) || UpdateManager.forced(c)) Notifier.update(c)
        return Result.success()
    }
}

/** Runs every few hours in the background (even when the app is closed). */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Notifier.channels(c)
        withContext(Dispatchers.IO) { UpdateManager.check(c) }
        if (UpdateManager.available(c)) Notifier.update(c)
        if (!PlanManager(c).isPaid && AppSettings(c).reminders) Notifier.promo(c)
        return Result.success()
    }
}

object Reminders {
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(Config.REMINDER_HOURS, TimeUnit.HOURS)
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("pbp_reminder", ExistingPeriodicWorkPolicy.KEEP, req)
        val upd = PeriodicWorkRequestBuilder<UpdateWorker>(Config.UPDATE_CHECK_HOURS, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("pbp_update", ExistingPeriodicWorkPolicy.KEEP, upd)
    }
}
