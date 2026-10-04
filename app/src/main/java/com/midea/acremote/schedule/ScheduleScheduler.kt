package com.midea.acremote.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.midea.acremote.data.Schedule
import java.util.Calendar

/**
 * Đặt/cancel báo thức cho các lịch hẹn.
 *
 * Cách tính lần chạy kế tiếp:
 *  - [Schedule.daysMask] == 0 (một lần): lần tới tiếp theo của giờ đã chọn
 *    (hôm nay nếu chưa tới, không thì ngày mai).
 *  - khác 0 (lặp hằng tuần): ngày gần nhất trong 7 ngày tới trùng [Schedule.daysMask].
 */
class ScheduleScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(schedule: Schedule) {
        if (!schedule.enabled) {
            cancel(schedule.id)
            return
        }
        val triggerAt = nextTriggerMillis(schedule, System.currentTimeMillis()) ?: return
        val pi = pendingIntent(schedule.id, schedule)

        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(id: String) {
        alarmManager.cancel(pendingIntent(id, null))
    }

    fun scheduleAll(list: List<Schedule>) {
        list.forEach { schedule(it) }
    }

    /** Tính mốc thời gian kế tiếp (ms since epoch), null nếu không tính được. */
    fun nextTriggerMillis(schedule: Schedule, now: Long): Long? {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (offset in 0..7) {
            val target = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now) continue
            if (schedule.daysMask == 0) return target.timeInMillis
            val dayIndex = dayIndexOf(target.get(Calendar.DAY_OF_WEEK))
            if (((schedule.daysMask shr dayIndex) and 1) == 1) return target.timeInMillis
        }
        return null
    }

    private fun pendingIntent(id: String, schedule: Schedule?): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_FIRE
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    companion object {
        /** Đổi Calendar.DAY_OF_WEEK (SUNDAY=1..SATURDAY=7) -> chỉ số 0=T2 .. 6=CN. */
        fun dayIndexOf(calendarDayOfWeek: Int): Int =
            if (calendarDayOfWeek == Calendar.SUNDAY) 6 else calendarDayOfWeek - 2
    }
}
