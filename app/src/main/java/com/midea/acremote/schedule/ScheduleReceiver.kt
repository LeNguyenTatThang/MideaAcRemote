package com.midea.acremote.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.midea.acremote.MideaApp
import com.midea.acremote.data.ScheduleAction
import com.midea.acremote.protocol.AcState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Nhận sự kiện hẹn giờ rồi gửi tín hiệu IR tương ứng.
 * Lịch lặp sẽ được đặt lại cho lần kế tiếp; lịch một lần sẽ tự tắt.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val app = context.applicationContext as? MideaApp ?: return
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.container.repository
                val schedule = repo.scheduleById(id).first()
                if (schedule != null && schedule.enabled) {
                    val current = repo.settings.first()
                    val target: AcState = when (schedule.action) {
                        ScheduleAction.POWER_ON -> current.state.copy(power = true)
                        ScheduleAction.POWER_OFF -> current.state.copy(power = false)
                        ScheduleAction.APPLY_STATE -> schedule.state
                    }
                    repo.setState(target)
                    app.container.sendState(target)

                    if (schedule.recurring) {
                        app.container.scheduler.schedule(schedule)
                    } else {
                        repo.setScheduleEnabled(id, false)
                        app.container.scheduler.cancel(id)
                    }
                }
            } catch (_: Throwable) {
                // Không có gì để hiển thị từ BroadcastReceiver; lỗi đã nằm trong lastError.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.midea.acremote.SCHEDULE_FIRE"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
