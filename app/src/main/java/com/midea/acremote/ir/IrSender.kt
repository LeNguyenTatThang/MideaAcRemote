package com.midea.acremote.ir

/**
 * Tín hiệu hồng ngoại đã đóng gói sẵn để phát.
 *
 * @property carrierHz tần số carrier (Hz)
 * @property pattern mảng xen kẽ mark/space theo µs, bắt đầu bằng mark,
 *   độ dài lẻ/chẵn không quan trọng nhưng lý tưởng là chẵn
 */
data class IrSignal(
    val carrierHz: Int,
    val pattern: IntArray
) {
    val durationUs: Long get() = pattern.fold(0L) { a, b -> a + b }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrSignal) return false
        return carrierHz == other.carrierHz && pattern.contentEquals(other.pattern)
    }

    override fun hashCode(): Int = 31 * carrierHz + pattern.contentHashCode()
}

/** Kênh phát tín hiệu IR mà người dùng có thể chọn. */
enum class IrChannel(val id: String, val label: String) {
    PHONE_IR("phone", "Cổng hồng ngoại máy"),
    WIFI_IR("wifi", "IR blaster WiFi (HTTP)")
}

/**
 * Một thiết bị phát IR. Triển khai phải chạy bất đồng bộ và không chặn main thread.
 */
interface IrSender {
    val id: String
    val label: String

    /** Có thể dùng được không (đã bật quyền / có phần cứng / cấu hình đủ). */
    suspend fun isAvailable(): Boolean

    /** Gửi một tín hiệu. Ném ngoại lệ nếu thất bại. */
    suspend fun send(signal: IrSignal)

    /** Mô tả lỗi gần nhất, dùng cho toast trên UI. */
    val lastError: String?
}
