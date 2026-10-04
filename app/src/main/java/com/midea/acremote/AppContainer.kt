package com.midea.acremote

import android.content.Context
import com.midea.acremote.data.AppRepository
import com.midea.acremote.data.Settings
import com.midea.acremote.ir.HttpIrSender
import com.midea.acremote.ir.IrChannel
import com.midea.acremote.ir.IrController
import com.midea.acremote.ir.PhoneIrSender
import com.midea.acremote.protocol.AcState
import com.midea.acremote.protocol.MideaProtocol
import com.midea.acremote.schedule.ScheduleScheduler

/** Container dependency injection thủ công. */
class AppContainer(context: Context) {

    val repository = AppRepository(context)
    val phoneSender = PhoneIrSender(context)
    val irController = IrController(phoneSender)
    val scheduler = ScheduleScheduler(context)

    fun httpConfig(settings: Settings) = HttpIrSender.HttpConfig(
        url = settings.httpUrl,
        bodyTemplate = settings.httpBody,
        authHeader = settings.httpAuth
    )

    suspend fun send(
        payloads: List<Long>,
        channel: IrChannel = IrChannel.PHONE_IR,
        http: HttpIrSender.HttpConfig = HttpIrSender.HttpConfig("")
    ): String {
        val signal = irController.send(payloads, channel, http)
        return "Đã gửi ${irController.describe(payloads)} (${signal.durationUs / 1000}ms)"
    }

    /** Gửi gói trạng thái của một [AcState]. */
    suspend fun sendState(
        state: AcState,
        settings: Settings,
        extraPayload: Long? = null
    ): String {
        val payloads = if (extraPayload != null) {
            MideaProtocol.togglePayloads(state, extraPayload)
        } else {
            listOf(MideaProtocol.encode(state))
        }
        return send(payloads, settings.channel, httpConfig(settings))
    }
}
