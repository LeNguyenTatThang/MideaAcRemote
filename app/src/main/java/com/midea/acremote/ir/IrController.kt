package com.midea.acremote.ir

import com.midea.acremote.protocol.MideaProtocol

/**
 * Điều phối việc đóng gói payload -> xung thô rồi phát ra kênh đã chọn.
 */
class IrController(private val phoneSender: PhoneIrSender) {

    /** Đóng gói một hoặc nhiều payload thành tín hiệu thô. */
    fun buildSignal(payloads: List<Long>): IrSignal {
        require(payloads.isNotEmpty()) { "Cần ít nhất 1 payload" }
        val array = MideaProtocol.pattern(*payloads.toLongArray())
        return IrSignal(MideaProtocol.CARRIER_HZ, array)
    }

    /** Mô tả payload dạng hex để hiển thị/log. */
    fun describe(payloads: List<Long>): String =
        payloads.joinToString(" + ") { "0x%012X".format(it) }

    /**
     * Gửi một tập lệnh ra kênh chỉ định.
     * @throws Exception nếu kênh không sẵn sàng hoặc gửi thất bại.
     * @return tín hiệu đã phát (dùng cho UI hiển thị "đã gửi ...").
     */
    suspend fun send(
        payloads: List<Long>,
        channel: IrChannel,
        http: HttpIrSender.HttpConfig
    ): IrSignal {
        val signal = buildSignal(payloads)
        val sender: IrSender = when (channel) {
            IrChannel.PHONE_IR -> phoneSender
            IrChannel.WIFI_IR -> HttpIrSender(http)
        }
        if (!sender.isAvailable()) {
            val reason = sender.lastError ?: "Kênh ${sender.label} không khả dụng"
            throw IllegalStateException(reason)
        }
        sender.send(signal)
        return signal
    }

    /** True nếu kênh đang sẵn sàng dùng. */
    suspend fun isChannelAvailable(channel: IrChannel, http: HttpIrSender.HttpConfig): Boolean =
        when (channel) {
            IrChannel.PHONE_IR -> phoneSender.isAvailable()
            IrChannel.WIFI_IR -> http.isConfigured
        }
}
