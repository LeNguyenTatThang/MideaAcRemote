package com.midea.acremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phát tín hiệu IR bằng cổng hồng ngoại có sẵn trên máy (ConsumerIrManager).
 *
 * Lưu ý ràng buộc của Android:
 *  - [ConsumerIrManager.transmit] là lời gọi binder đồng bộ -> phải chạy nền.
 *  - Tổng thời lượng pattern phải < 2 giây (mỗi message Midea ~292ms).
 *  - Pattern bắt đầu bằng mark.
 */
class PhoneIrSender(context: Context) : IrSender {

    override val id: String = IrChannel.PHONE_IR.id
    override val label: String = IrChannel.PHONE_IR.label
    override var lastError: String? = null
        private set

    private val manager: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    override suspend fun isAvailable(): Boolean = manager?.hasIrEmitter() == true

    override suspend fun send(signal: IrSignal) {
        val m = manager
        if (m == null || !m.hasIrEmitter()) {
            lastError = "Máy này không có cổng hồng ngoại"
            throw IllegalStateException(lastError)
        }
        if (signal.durationUs >= MAX_DURATION_US) {
            lastError = "Tín hiệu quá dài (${signal.durationUs / 1000}ms)"
            throw IllegalArgumentException(lastError)
        }
        try {
            withContext(Dispatchers.IO) {
                m.transmit(signal.carrierHz, signal.pattern)
            }
            lastError = null
        } catch (t: Throwable) {
            lastError = t.message ?: "Gửi tín hiệu IR thất bại"
            throw t
        }
    }

    companion object {
        /** Android yêu cầu pattern < 2 giây. */
        const val MAX_DURATION_US = 2_000_000L
    }
}
