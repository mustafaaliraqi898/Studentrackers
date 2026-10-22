package com.studenttracker.app.service

import android.Manifest
import android.app.AlarmManager
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
import com.studenttracker.app.MainActivity
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.DailyScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class GlobalScheduleReminderSettings(
    val enabled: Boolean,
    val minutesBefore: Int?,
    val minutesAfterStart: Int = 30
)

object ScheduleReminderManager {

    private const val PREFS_NAME = "daily_schedule_reminder_prefs"
    private const val KEY_GLOBAL_REMINDER_ENABLED = "global_reminder_enabled"
    private const val KEY_GLOBAL_REMINDER_MINUTES = "global_reminder_minutes_before"
    private const val KEY_GLOBAL_REMINDER_AFTER_START_MINUTES = "global_reminder_minutes_after_start"
    private const val KEY_KNOWN_LESSON_IDS = "scheduled_lesson_ids"

    const val CHANNEL_ID = "student_tracker_lesson_reminders"
    private const val CHANNEL_NAME = "تنبيهات الحصص اليومية"
    private const val CHANNEL_DESC = "إشعارات تذكير بمواعيد الحصص المسجلة في الجدول اليومي"

    const val ACTION_LESSON_REMINDER = "com.studenttracker.app.ACTION_LESSON_REMINDER"
    const val EXTRA_LESSON_ID = "extra_lesson_id"
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
    const val EXTRA_CLASS_NAME = "extra_class_name"
    const val EXTRA_SECTION_NAME = "extra_section_name"
    const val EXTRA_LESSON_NUMBER = "extra_lesson_number"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
    const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    private const val REMINDER_BEFORE = "before"
    private const val REMINDER_AFTER_START = "after_start"
    private const val DEFAULT_AFTER_START_MINUTES = 30

    fun parsePositiveMinutes(rawInput: String): Int? {
        val normalized = buildString {
            for (ch in rawInput.trim()) {
                when (ch) {
                    in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                    in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                    else -> append(ch)
                }
            }
        }
        if (normalized.isEmpty() || !normalized.all { it.isDigit() }) return null
        val value = normalized.toIntOrNull() ?: return null
        return if (value > 0) value else null
    }

    fun getSettings(context: Context): GlobalScheduleReminderSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_GLOBAL_REMINDER_ENABLED, false)
        val rawMinutes = if (prefs.contains(KEY_GLOBAL_REMINDER_MINUTES)) {
            prefs.getInt(KEY_GLOBAL_REMINDER_MINUTES, -1)
        } else {
            -1
        }
        val minutes = if (rawMinutes > 0) rawMinutes else null
        val minutesAfterStart = prefs.getInt(
            KEY_GLOBAL_REMINDER_AFTER_START_MINUTES,
            DEFAULT_AFTER_START_MINUTES
        ).takeIf { it > 0 } ?: DEFAULT_AFTER_START_MINUTES
        return GlobalScheduleReminderSettings(
            enabled = enabled && minutes != null,
            minutesBefore = minutes,
            minutesAfterStart = minutesAfterStart
        )
    }

    fun saveSettingsAndSync(
        context: Context,
        enabled: Boolean,
        minutesBefore: Int?,
        repository: TrackerRepository? = null
    ): GlobalScheduleReminderSettings {
        val validMinutes = minutesBefore?.takeIf { it > 0 }
        val effectiveEnabled = enabled && validMinutes != null
        val appCtx = context.applicationContext
        val editor = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putBoolean(KEY_GLOBAL_REMINDER_ENABLED, effectiveEnabled)
        if (validMinutes != null) {
            editor.putInt(KEY_GLOBAL_REMINDER_MINUTES, validMinutes)
        } else {
            editor.remove(KEY_GLOBAL_REMINDER_MINUTES)
        }
        editor.apply()

        val repo = repository ?: TrackerRepository(AppDatabaseHelper.getInstance(appCtx))
        runCatching {
            val db = AppDatabaseHelper.getInstance(appCtx).writableDatabase
            db.execSQL(
                "UPDATE DailySchedule SET ReminderEnabled = ?, ReminderMinutesBefore = ?;",
                arrayOf<Any>(if (effectiveEnabled) 1 else 0, validMinutes ?: 0)
            )
        }
        syncAllLessonReminders(appCtx, repo.getDailySchedules())
        return GlobalScheduleReminderSettings(
            enabled = effectiveEnabled,
            minutesBefore = validMinutes
        )
    }

    fun saveAfterStartMinutesAndSync(
        context: Context,
        minutesAfterStart: Int,
        repository: TrackerRepository? = null
    ): Int {
        val validMinutes = minutesAfterStart.takeIf { it > 0 } ?: DEFAULT_AFTER_START_MINUTES
        val appCtx = context.applicationContext
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GLOBAL_REMINDER_AFTER_START_MINUTES, validMinutes)
            .apply()
        val repo = repository ?: TrackerRepository(AppDatabaseHelper.getInstance(appCtx))
        syncAllLessonReminders(appCtx, repo.getDailySchedules())
        return validMinutes
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun syncAllLessonReminders(context: Context, schedules: List<DailyScheduleEntity>? = null) {
        val appCtx = context.applicationContext
        ensureNotificationChannel(appCtx)
        val settings = getSettings(appCtx)
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val allLessons = schedules ?: runCatching {
            TrackerRepository(AppDatabaseHelper.getInstance(appCtx)).getDailySchedules()
        }.getOrDefault(emptyList())

        // Cancel previously registered alarms and all current schedule IDs first so deleted/modified lessons or updated durations never duplicate
        val oldIds = prefs.getStringSet(KEY_KNOWN_LESSON_IDS, emptySet()).orEmpty()
        val idsToCancel = buildSet {
            oldIds.forEach { it.toLongOrNull()?.let(::add) }
            allLessons.forEach { add(it.id) }
        }
        idsToCancel.forEach { oldId ->
            cancelAlarmForLessonId(appCtx, alarmManager, oldId)
        }

        val validMinutes = settings.minutesBefore
        if (!settings.enabled || validMinutes == null || validMinutes <= 0) {
            prefs.edit().putStringSet(KEY_KNOWN_LESSON_IDS, emptySet()).apply()
            return
        }

        val activeIds = mutableSetOf<String>()
        for (lesson in allLessons) {
            val triggerMillis = computeNextTriggerMillis(
                dayOfWeekAr = lesson.dayOfWeek,
                startTime24 = lesson.startTime,
                minutesBefore = validMinutes
            ) ?: continue

            scheduleSingleLessonAlarm(
                context = appCtx,
                alarmManager = alarmManager,
                lesson = lesson,
                minutesBefore = validMinutes,
                triggerAtMillis = triggerMillis
            )
            activeIds.add(lesson.id.toString())
            val afterStartMillis = computeNextTriggerMillisAfterStart(
                dayOfWeekAr = lesson.dayOfWeek,
                startTime24 = lesson.startTime,
                minutesAfterStart = settings.minutesAfterStart
            )
            if (afterStartMillis != null) {
                scheduleSingleLessonAlarm(
                    context = appCtx,
                    alarmManager = alarmManager,
                    lesson = lesson,
                    minutesBefore = settings.minutesAfterStart,
                    triggerAtMillis = afterStartMillis,
                    reminderType = REMINDER_AFTER_START
                )
            }
        }

        prefs.edit().putStringSet(KEY_KNOWN_LESSON_IDS, activeIds).apply()
    }

    private fun cancelAlarmForLessonId(
        context: Context,
        alarmManager: AlarmManager,
        lessonId: Long
    ) {
        val intent = Intent(context, ScheduleReminderReceiver::class.java).apply {
            action = ACTION_LESSON_REMINDER
        }
        val baseCode = (lessonId.hashCode() and 0x3FFFFFFF) * 2
        // Also cancel the legacy pre-reminder request code from older app versions.
        listOf((lessonId.hashCode() and 0x7FFFFFFF), baseCode, baseCode + 1).distinct().forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun scheduleSingleLessonAlarm(
        context: Context,
        alarmManager: AlarmManager,
        lesson: DailyScheduleEntity,
        minutesBefore: Int,
        triggerAtMillis: Long,
        reminderType: String = REMINDER_BEFORE
    ) {
        val intent = Intent(context, ScheduleReminderReceiver::class.java).apply {
            action = ACTION_LESSON_REMINDER
            putExtra(EXTRA_LESSON_ID, lesson.id)
            putExtra(EXTRA_DAY_OF_WEEK, lesson.dayOfWeek)
            putExtra(EXTRA_CLASS_NAME, lesson.className)
            putExtra(EXTRA_SECTION_NAME, lesson.sectionName)
            putExtra(EXTRA_LESSON_NUMBER, lesson.lessonNumber)
            putExtra(EXTRA_START_TIME, lesson.startTime)
            putExtra(EXTRA_END_TIME, lesson.endTime)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
            putExtra(EXTRA_REMINDER_TYPE, reminderType)
        }
        val baseCode = (lesson.id.hashCode() and 0x3FFFFFFF) * 2
        val requestCode = baseCode + if (reminderType == REMINDER_AFTER_START) 1 else 0
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    fun computeNextTriggerMillis(
        dayOfWeekAr: String,
        startTime24: String,
        minutesBefore: Int
    ): Long? {
        val targetDay = mapArabicDayToDayOfWeek(dayOfWeekAr) ?: return null
        val parts = startTime24.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var candidateDate = now.toLocalDate()
        while (candidateDate.dayOfWeek != targetDay) {
            candidateDate = candidateDate.plusDays(1)
        }

        val lessonDateTime = LocalDateTime.of(candidateDate, LocalTime.of(hour, minute))
        var reminderDateTime = lessonDateTime.minusMinutes(minutesBefore.toLong())

        if (!reminderDateTime.isAfter(now)) {
            reminderDateTime = reminderDateTime.plusWeeks(1)
        }

        return reminderDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    fun computeNextTriggerMillisAfterStart(
        dayOfWeekAr: String,
        startTime24: String,
        minutesAfterStart: Int
    ): Long? {
        val targetDay = mapArabicDayToDayOfWeek(dayOfWeekAr) ?: return null
        val parts = startTime24.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var candidateDate = now.toLocalDate()
        while (candidateDate.dayOfWeek != targetDay) candidateDate = candidateDate.plusDays(1)
        var trigger = LocalDateTime.of(candidateDate, LocalTime.of(hour, minute))
            .plusMinutes(minutesAfterStart.toLong())
        if (!trigger.isAfter(now)) trigger = trigger.plusWeeks(1)
        return trigger.atZone(zone).toInstant().toEpochMilli()
    }

    fun mapArabicDayToDayOfWeek(dayAr: String): DayOfWeek? {
        return when (dayAr.trim()) {
            "الأحد" -> DayOfWeek.SUNDAY
            "الاثنين" -> DayOfWeek.MONDAY
            "الثلاثاء" -> DayOfWeek.TUESDAY
            "الأربعاء" -> DayOfWeek.WEDNESDAY
            "الخميس" -> DayOfWeek.THURSDAY
            "الجمعة" -> DayOfWeek.FRIDAY
            "السبت" -> DayOfWeek.SATURDAY
            else -> null
        }
    }

    fun getArabicTodayName(): String {
        val today = LocalDateTime.now(ZoneId.systemDefault()).dayOfWeek
        return when (today) {
            DayOfWeek.SUNDAY -> "الأحد"
            DayOfWeek.MONDAY -> "الاثنين"
            DayOfWeek.TUESDAY -> "الثلاثاء"
            DayOfWeek.WEDNESDAY -> "الأربعاء"
            DayOfWeek.THURSDAY -> "الخميس"
            DayOfWeek.FRIDAY -> "الجمعة"
            DayOfWeek.SATURDAY -> "السبت"
        }
    }

    fun formatTime12HourAr(timeStr: String): String {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return timeStr
        val h = parts[0].toIntOrNull() ?: return timeStr
        val m = parts[1].toIntOrNull() ?: return timeStr
        if (h !in 0..23 || m !in 0..59) return timeStr
        val hour12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        val minuteStr = String.format(java.util.Locale.US, "%02d", m)
        val periodLabel = when {
            h == 0 && m == 0 -> "منتصف الليل"
            h < 12 -> "صباحاً"
            else -> "مساءً"
        }
        return "$hour12:$minuteStr $periodLabel"
    }

    fun getArabicLessonLabel(lessonNum: Int): String {
        return when (lessonNum) {
            1 -> "الدرس الأول"
            2 -> "الدرس الثاني"
            3 -> "الدرس الثالث"
            4 -> "الدرس الرابع"
            5 -> "الدرس الخامس"
            6 -> "الدرس السادس"
            7 -> "الدرس السابع"
            8 -> "الدرس الثامن"
            9 -> "الدرس التاسع"
            10 -> "الدرس العاشر"
            else -> "الدرس $lessonNum"
        }
    }
}

class ScheduleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            ScheduleReminderManager.syncAllLessonReminders(appCtx)
            return
        }

        if (action == ScheduleReminderManager.ACTION_LESSON_REMINDER) {
            val settings = ScheduleReminderManager.getSettings(appCtx)
            val savedMinutes = settings.minutesBefore
            if (!settings.enabled || savedMinutes == null || savedMinutes <= 0) return

            val lessonId = intent.getLongExtra(ScheduleReminderManager.EXTRA_LESSON_ID, -1L)
            val dayOfWeek = intent.getStringExtra(ScheduleReminderManager.EXTRA_DAY_OF_WEEK) ?: ""
            val className = intent.getStringExtra(ScheduleReminderManager.EXTRA_CLASS_NAME) ?: ""
            val sectionName = intent.getStringExtra(ScheduleReminderManager.EXTRA_SECTION_NAME) ?: ""
            val lessonNumber = intent.getIntExtra(ScheduleReminderManager.EXTRA_LESSON_NUMBER, 1)
            val startTime = intent.getStringExtra(ScheduleReminderManager.EXTRA_START_TIME) ?: ""
            val reminderType = intent.getStringExtra(ScheduleReminderManager.EXTRA_REMINDER_TYPE) ?: "before"
            val minutesBefore = intent.getIntExtra(
                ScheduleReminderManager.EXTRA_MINUTES_BEFORE,
                savedMinutes
            )
            val minutesAfterStart = settings.minutesAfterStart

            if (ScheduleReminderManager.hasNotificationPermission(appCtx)) {
                ScheduleReminderManager.ensureNotificationChannel(appCtx)
                val openAppIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    appCtx,
                    (lessonId.hashCode() and 0x7FFFFFFF),
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val lessonLabel = ScheduleReminderManager.getArabicLessonLabel(lessonNumber)
                val time12 = ScheduleReminderManager.formatTime12HourAr(startTime)
                val isAfterStart = reminderType == "after_start"
                val title = if (isAfterStart) "تذكير أثناء الحصة ($lessonLabel)" else "تذكير بالحصة القادمة ($lessonLabel)"
                val body = if (isAfterStart) {
                    "مضى $minutesAfterStart دقيقة على بدء حصة $className / شعبة $sectionName (بدأت الساعة $time12 - $dayOfWeek)"
                } else {
                    "تبدأ حصة $className / شعبة $sectionName بعد $minutesBefore دقائق (الساعة $time12 - $dayOfWeek)"
                }

                val notification = NotificationCompat.Builder(appCtx, ScheduleReminderManager.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .build()

                runCatching {
                    NotificationManagerCompat.from(appCtx)
                        .notify(20000 + (lessonId.toInt() and 0x3FFF) * 2 + if (isAfterStart) 1 else 0, notification)
                }
            }

            // Reschedule for next week
            ScheduleReminderManager.syncAllLessonReminders(appCtx)
        }
    }
}
