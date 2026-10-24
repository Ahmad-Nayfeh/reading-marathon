package com.readingmarathon.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.readingmarathon.app.MainActivity
import com.readingmarathon.app.MarathonApp
import com.readingmarathon.app.R
import com.readingmarathon.core.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * The daily reminder carries quick-log buttons, so a day can be logged without opening the app.
 * The reading timer shows as an ongoing notification with a stop button.
 */
object Reminder {
    private const val CHANNEL = "daily_reading"
    private const val TIMER_CHANNEL = "reading_timer"
    private const val WORK = "daily_reading_reminder"
    private const val REMINDER_ID = 1
    private const val TIMER_ID = 2
    const val ACTION_ADD = "com.readingmarathon.app.ADD_MINUTES"
    const val ACTION_STOP_TIMER = "com.readingmarathon.app.STOP_TIMER"
    const val EXTRA_MINUTES = "minutes"
    val QUICK = listOf(15, 30, 60)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(TIMER_CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_LOW))
    }

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, minutes: Int = 0) = PendingIntent.getBroadcast(
        context, action.hashCode() + minutes,
        Intent(context, LogReceiver::class.java).setAction(action).putExtra(EXTRA_MINUTES, minutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun showReminder(context: Context, todayMinutes: Int?, book: String? = null) {
        if (!canNotify(context)) return
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(if (todayMinutes != null && todayMinutes > 0) "قرأت اليوم ${Fmt.minutes(todayMinutes)}" else "هل قرأت اليوم؟")
            .setContentText(book?.let { "سجّل لـ«$it» من هنا مباشرة:" } ?: "سجّل من هنا مباشرة:")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
        QUICK.forEach { m -> b.addAction(0, "+$m د", broadcast(context, ACTION_ADD, m)) }
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, b.build()) }
    }

    private fun showLogged(context: Context, title: String, total: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("سُجّل في «$title»: ${Fmt.minutes(total)} اليوم")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, n) }
    }

    /** Ongoing notification while the timer runs, with the elapsed time counting up. */
    fun showTimer(context: Context, startedAt: Long, title: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("تقرأ «$title»")
            .setContentText("اضغط «إيقاف وتسجيل» حين تنتهي.")
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .addAction(0, "إيقاف وتسجيل", broadcast(context, ACTION_STOP_TIMER))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(TIMER_ID, n) }
    }

    fun hideTimer(context: Context) {
        NotificationManagerCompat.from(context).cancel(TIMER_ID)
    }

    /** Minutes the timer counted, rounded to the nearest minute. */
    fun timerMinutes(startedAt: Long, now: Long = System.currentTimeMillis()): Int =
        ((now - startedAt + 30_000) / 60_000).toInt().coerceAtLeast(0)

    internal fun added(context: Context, title: String, total: Int) = showLogged(context, title, total)
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MarathonApp
        val today = app.backend.reads.value.filter { it.date == LocalDate.now() }.sumOf { it.minutes }.takeIf { app.backend.reads.value.isNotEmpty() }
        val book = com.readingmarathon.core.Shelf.reading(app.backend.shelf.value).firstOrNull()?.title
        Reminder.showReminder(applicationContext, today, book)
        return Result.success()
    }
}

/** Handles the notification buttons: log minutes, or stop the timer and log what it counted. */
class LogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MarathonApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                var bookId: String? = null
                val minutes = when (intent.action) {
                    Reminder.ACTION_ADD -> intent.getIntExtra(Reminder.EXTRA_MINUTES, 0)
                    Reminder.ACTION_STOP_TIMER -> {
                        val s = app.prefs.current()
                        bookId = s.timerBookId
                        app.prefs.setTimer(null, null)
                        Reminder.hideTimer(context)
                        s.timerStartedAt?.let { Reminder.timerMinutes(it) } ?: 0
                    }
                    else -> 0
                }
                // Started from the notification, the process may still be restoring the sign-in.
                withTimeoutOrNull(5_000) { app.backend.restoring.first { !it } }
                if (minutes > 0 && app.backend.account.value != null) {
                    // The reminder buttons log for the book read most recently.
                    val result = withTimeoutOrNull(8_000) { app.backend.addRead(LocalDate.now(), bookId, minutes) }
                    if (result != null) Reminder.added(context, result.first.title, result.second)
                }
            } catch (e: Exception) {
                android.util.Log.w("Marathon", "quick log", e)
            } finally {
                pending.finish()
            }
        }
    }
}
