package com.midea.acremote.data

import com.midea.acremote.protocol.AcMode
import com.midea.acremote.protocol.AcState
import com.midea.acremote.protocol.FanSpeed

/**
 * Mã hóa [AcState] thành chuỗi ngắn, đọc ngược lại được.
 *
 * Định dạng: `k=v;k=v;...` với bool là `1`/`0`.
 * Không dùng JSON để khỏi phải thêm dependency và để chạy được trong unit test JUnit thuần.
 */
object StateCodec {

    fun encode(s: AcState): String = buildList {
        add(b("p", s.power))
        add(n("m", s.mode.code))
        add(n("f", s.fan.code))
        add(b("u", s.useFahrenheit))
        add(n("t", s.temp))
        add(b("sl", s.sleep))
        add(b("fm", s.followMe))
        add(n("st", s.sensorTemp))
        add(n("it", s.onTimerMinutes))
        add(n("ot", s.offTimerMinutes))
        add(b("be", s.beep))
        add(b("sb", s.specialBit))
        add(b("sw", s.swing))
        add(b("tu", s.turbo))
        add(b("ec", s.eco))
        add(b("le", s.led))
        add(b("cl", s.selfClean))
        add(b("fr", s.freezeProtect))
        add(b("qu", s.quiet))
        add(b("ck", s.childLock))
    }.joinToString(";")

    fun decode(raw: String?): AcState {
        if (raw.isNullOrBlank()) return AcState()
        val map = HashMap<String, String>(24)
        for (pair in raw.split(';')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            map[pair.substring(0, idx)] = pair.substring(idx + 1)
        }
        fun bool(k: String, def: Boolean) = map[k]?.let { it == "1" } ?: def
        fun int(k: String, def: Int) = map[k]?.toIntOrNull() ?: def

        return AcState(
            power = bool("p", true),
            mode = runCatching { AcMode.fromCode(int("m", 2)) }.getOrDefault(AcMode.AUTO),
            fan = runCatching { FanSpeed.fromCode(int("f", 0)) }.getOrDefault(FanSpeed.AUTO),
            useFahrenheit = bool("u", false),
            temp = int("t", 25),
            sleep = bool("sl", false),
            followMe = bool("fm", false),
            sensorTemp = int("st", 25),
            onTimerMinutes = int("it", 0),
            offTimerMinutes = int("ot", 0),
            beep = bool("be", true),
            specialBit = bool("sb", false),
            swing = bool("sw", false),
            turbo = bool("tu", false),
            eco = bool("ec", false),
            led = bool("le", true),
            selfClean = bool("cl", false),
            freezeProtect = bool("fr", false),
            quiet = bool("qu", false),
            childLock = bool("ck", false)
        )
    }

    private fun b(key: String, v: Boolean) = "$key=${if (v) 1 else 0}"
    private fun n(key: String, v: Int) = "$key=$v"
}
