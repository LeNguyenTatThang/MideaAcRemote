package com.midea.acremote.data

import com.midea.acremote.ir.IrChannel
import com.midea.acremote.protocol.AcState
import java.util.UUID

/** Cài đặt tổng quát của app + trạng thái remote hiện tại. */
data class Settings(
    val channel: IrChannel = IrChannel.PHONE_IR,
    val httpUrl: String = "",
    val httpBody: String = com.midea.acremote.ir.HttpIrSender.DEFAULT_BODY_TEMPLATE,
    val httpAuth: String = "",
    val keepScreenOn: Boolean = true,
    val state: AcState = AcState()
)

/** Một tổ hợp trạng thái đã lưu. */
data class Favorite(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val state: AcState
)

/** Hành động của lịch hẹn. */
enum class ScheduleAction(val label: String) {
    APPLY_STATE("Áp dụng tổ hợp"),
    POWER_ON("Bật máy"),
    POWER_OFF("Tắt máy")
}

/**
 * Lịch hẹn.
 *
 * @property daysMask bit 0 = Thứ 2 ... bit 6 = Chủ nhật.
 *   [daysMask] == 0 nghĩa là chạy một lần vào lần tới tiếp theo rồi tự tắt.
 */
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val action: ScheduleAction = ScheduleAction.POWER_ON,
    val state: AcState = AcState(),
    val enabled: Boolean = true
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)

    /** true nếu lặp lại hằng tuần. */
    val recurring: Boolean get() = daysMask != 0

    fun hasDay(dayIndex: Int): Boolean = (daysMask shr dayIndex) and 1 == 1

    companion object {
        val DAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        const val TOTAL_DAYS = 7
    }
}
