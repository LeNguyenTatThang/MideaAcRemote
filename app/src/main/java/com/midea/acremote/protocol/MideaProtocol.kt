package com.midea.acremote.protocol

/**
 * Giao thức hồng ngoại Midea 48-bit.
 *
 * Nguồn tham chiếu (đã đối chiếu từng vector với unit test gốc):
 *  - https://github.com/crankyoldgit/IRremoteESP8266 (src/ir_Midea.h/.cpp, test/ir_Midea_test.cpp)
 *  - Manual remote Midea CR249-RG51A/B, CR247-RG10A, RG70E3(2)
 *
 * Cấu trúc gói trạng thái (6 byte, bit 0..47 của giá trị 48-bit):
 *
 *   byte0 = checksum
 *   byte1 = disableSensor:1 | sensorTempOrOnTimer:7
 *   byte2 = beepDisable:1 | offTimer:6 | 1          (bit0 luôn = 1)
 *   byte3 = 1 | useFahrenheit:1 | tempIndex:5        (bit6 luôn = 1)
 *   byte4 = power:1 | sleep:1 | specialBit | fan:2 | mode:3
 *   byte5 = header(0b10100):5 | type:3
 *
 * Trên dây các byte gửi theo thứ tự MSB trước: byte5, byte4, ..., byte0;
 * mỗi byte gửi bit cao trước (MSB-first).
 *
 * Checksum: tổng các byte đã đảo bit (byte1..byte5) phải chia hết cho 256.
 *
 * Mỗi message gửi 2 frame: frame thường và frame đảo toàn bộ 48 bit.
 */
object MideaProtocol {

    const val CARRIER_HZ = 38_000
    const val BITS = 48

    private const val TICK = 80 // µs / tick (kMideaTick)
    const val HEADER_MARK = 56 * TICK // 4480
    const val HEADER_SPACE = 56 * TICK // 4480
    const val BIT_MARK = 7 * TICK // 560
    const val ONE_SPACE = 21 * TICK // 1680
    const val ZERO_SPACE = 7 * TICK // 560
    const val FRAME_GAP = 70 * TICK // 5600 - kMideaMinGap
    const val MESSAGE_GAP = 100_000 // kDefaultMessageGap

    /** Header 5 bit xuất hiện trong byte5. */
    const val HEADER_BITS = 0b10100

    const val TYPE_COMMAND = 0b001
    const val TYPE_SPECIAL = 0b010
    const val TYPE_FOLLOW = 0b100

    /** Giá trị "không hẹn giờ bật / không dùng cảm biến" của byte1. */
    private const val SENSOR_OFF = 0x7F
    /** Giá trị "không hẹn giờ tắt" của trường offTimer (6 bit). */
    private const val OFF_TIMER_OFF = 0b111111

    // ---- Các gói lệnh đặc biệt (Type = Special) ----
    const val TOGGLE_SWING = 0xA201FFFFFF7CL
    const val TOGGLE_ECONO = 0xA202FFFFFF7EL
    const val TOGGLE_LIGHT = 0xA208FFFFFF75L
    const val TOGGLE_TURBO = 0xA209FFFFFF74L
    const val TOGGLE_SELF_CLEAN = 0xA20DFFFFFF70L
    const val TOGGLE_8C_HEAT = 0xA20FFFFFFF73L
    const val QUIET_ON = 0xA212FFFFFF6EL
    const val QUIET_OFF = 0xA213FFFFFF6FL

    /** Giá trị trạng thái mặc định - khớp `IRMideaAC::stateReset()`. */
    const val DEFAULT_STATE = 0xA1826FFFFF62L

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    /** Đảo bit của một byte (8 bit). */
    fun reverseBits(value: Int): Int {
        var src = value and 0xFF
        var dst = 0
        repeat(8) {
            dst = (dst shl 1) or (src and 1)
            src = src shr 1
        }
        return dst
    }

    /** Gói 6 byte thành giá trị 48-bit (byte0 là byte thấp nhất). */
    fun pack(bytes: IntArray): Long {
        var v = 0L
        for (i in 5 downTo 0) v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        return v
    }

    /** Tách payload 48-bit ra 6 byte theo thứ tự byte0..byte5. */
    fun unpack(payload: Long): IntArray {
        val out = IntArray(6)
        for (i in 0..5) out[i] = ((payload shr (8 * i)) and 0xFF).toInt()
        return out
    }

    /** Thứ tự byte trên dây: byte5 trước, byte0 sau. */
    fun wireBytes(payload: Long): IntArray {
        val b = unpack(payload)
        return intArrayOf(b[5], b[4], b[3], b[2], b[1], b[0])
    }

    /** Checksum: `reverseBits((256 - sum(reverseBits(byte[1..5]))) & 0xFF)`. */
    fun checksum(bytes: IntArray): Int {
        var sum = 0
        for (i in 1..5) sum += reverseBits(bytes[i])
        return reverseBits((256 - (sum and 0xFF)) and 0xFF)
    }

    /** Kiểm tra checksum của một payload 48-bit. */
    fun isValidChecksum(payload: Long): Boolean {
        val bytes = unpack(payload)
        return bytes[0] == checksum(bytes)
    }

    // ------------------------------------------------------------------
    // Đóng gói trạng thái -> payload 48-bit
    // ------------------------------------------------------------------

    /** byte3: chỉ số nhiệt độ lưu trong 5 bit. */
    fun tempIndex(state: AcState): Int =
        (state.temp - state.tempMin).coerceIn(0, 0x1F)

    /** byte2: hẹn giờ tắt (6 bit). */
    private fun offTimerCode(minutes: Int): Int {
        if (minutes <= 0) return OFF_TIMER_OFF
        val halfHours = (minutes.coerceAtMost(24 * 60)) / 30
        if (halfHours <= 0) return OFF_TIMER_OFF
        return (halfHours - 1) and 0x3F
    }

    /** byte1 khi KHÔNG bật Follow Me: hẹn giờ bật (bước 30 phút). */
    private fun onTimerCode(minutes: Int): Int {
        if (minutes <= 0) return SENSOR_OFF
        val halfHours = (minutes.coerceAtMost(24 * 60)) / 30
        if (halfHours <= 0) return SENSOR_OFF
        return (((halfHours - 1) shl 1) or 1) and 0x7F
    }

    /** byte1 khi bật Follow Me: (nhiệt độ cảm biến - min) + 1. */
    private fun sensorCode(state: AcState): Int {
        val native = state.sensorTemp.coerceIn(state.sensorTempMin, state.sensorTempMax)
        return (native - state.sensorTempMin + 1) and 0x7F
    }

    /**
     * Đóng gói trạng thái thành payload 48-bit (đã kèm checksum).
     */
    fun encode(state: AcState): Long {
        val follow = state.followMe
        val type = if (follow) TYPE_FOLLOW else TYPE_COMMAND

        // byte1: disableSensor = 0 khi Follow Me, ngược lại = 1.
        val low7 = if (follow) sensorCode(state) else onTimerCode(state.onTimerMinutes)
        val byte1 = ((if (follow) 0 else 1) shl 7) or low7

        // byte2: theo `setType()`, Command ép BeepDisable=1, Follow ép =0.
        val beepDisable = if (follow) 0 else if (state.beep) 1 else 0
        val byte2 = (beepDisable shl 7) or
            (offTimerCode(state.offTimerMinutes) shl 1) or 0b1

        // byte3: bit6 luôn = 1, bit5 = useFahrenheit, 5 bit thấp = temp index.
        val byte3 = 0x40 or
            (if (state.useFahrenheit) 0x20 else 0) or
            tempIndex(state)

        // byte4
        val byte4 = ((if (state.power) 1 else 0) shl 7) or
            ((if (state.sleep) 1 else 0) shl 6) or
            ((if (state.specialBit) 1 else 0) shl 5) or
            (state.fan.code shl 3) or
            state.mode.code

        // byte5
        val byte5 = (HEADER_BITS shl 3) or type

        val bytes = IntArray(6)
        bytes[1] = byte1
        bytes[2] = byte2
        bytes[3] = byte3
        bytes[4] = byte4
        bytes[5] = byte5
        bytes[0] = checksum(bytes)
        return pack(bytes)
    }

    /** Giải ngược payload về [AcState] (dùng cho paste mã hex / test). */
    fun decode(payload: Long): AcState {
        val b = unpack(payload)
        val type = b[5] and 0b111
        val follow = type == TYPE_FOLLOW
        val useF = (b[3] and 0x20) != 0
        val minTemp = if (useF) AcState.MIN_TEMP_F else AcState.MIN_TEMP_C
        val minSensor = if (useF) AcState.MIN_SENSOR_F else AcState.MIN_SENSOR_C

        val low7 = b[1] and 0x7F
        val sensor = if (follow) low7 - 1 + minSensor else 0

        val offTimerCode = (b[2] shr 1) and 0x3F
        val offMinutes = if (offTimerCode == OFF_TIMER_OFF) 0 else (offTimerCode + 1) * 30

        val onMinutes = if (follow || low7 == SENSOR_OFF) 0 else {
            val hh = (low7 shr 1) + 1
            hh * 30
        }

        return AcState(
            power = (b[4] and 0x80) != 0,
            mode = AcMode.fromCode(b[4] and 0b111),
            fan = FanSpeed.fromCode((b[4] shr 3) and 0b11),
            useFahrenheit = useF,
            temp = (b[3] and 0x1F) + minTemp,
            sleep = (b[4] and 0x40) != 0,
            followMe = follow,
            sensorTemp = if (follow) sensor.coerceIn(minSensor, if (useF) AcState.MAX_SENSOR_F else AcState.MAX_SENSOR_C) else sensorTempDefault(useF),
            offTimerMinutes = offMinutes,
            onTimerMinutes = onMinutes,
            beep = (b[2] and 0x80) != 0,
            specialBit = (b[4] and 0x20) != 0
        )
    }

    private fun sensorTempDefault(useFahrenheit: Boolean): Int =
        if (useFahrenheit) 77 else 25

    // ------------------------------------------------------------------
    // Đóng gói payload -> xung thô (µs)
    // ------------------------------------------------------------------

    private fun appendFrame(out: MutableList<Int>, bytes: IntArray) {
        out += HEADER_MARK
        out += HEADER_SPACE
        for (byte in bytes) {
            for (bit in 7 downTo 0) {
                out += BIT_MARK
                out += if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        out += BIT_MARK // footer mark
        out += FRAME_GAP
    }

    /**
     * Chuyển một hoặc nhiều payload thành mảng xung µs
     * (bắt đầu bằng mark, xen kẽ mark/space), khớp hệt `IRsend::sendMidea()`.
     */
    fun pattern(vararg payloads: Long): IntArray {
        require(payloads.isNotEmpty()) { "Cần ít nhất 1 payload" }
        val out = ArrayList<Int>(payloads.size * 200)
        for (payload in payloads) {
            val wire = wireBytes(payload)
            appendFrame(out, wire)
            val inverted = IntArray(wire.size) { wire[it] xor 0xFF }
            appendFrame(out, inverted)
            // Gộp kDefaultMessageGap vào space cuối cùng: 5600 + 100000 = 105600.
            out[out.size - 1] = out[out.size - 1] + MESSAGE_GAP
        }
        return out.toIntArray()
    }

    /** Tổng thời lượng của một mảng xung (µs). */
    fun durationUs(pattern: IntArray): Long = pattern.fold(0L) { acc, v -> acc + v }

    // ------------------------------------------------------------------
    // Danh sách lệnh cho UI
    // ------------------------------------------------------------------

    /**
     * Lệnh toggle: gửi kèm gói trạng thái hiện tại rồi mới gửi gói toggle
     * (đúng `IRMideaAC::send()` của thư viện tham chiếu).
     */
    fun togglePayloads(state: AcState, toggle: Long): List<Long> =
        listOf(encode(state), toggle)

    /** Payload cho nút Quiet (2 mã BẬT/TẮT riêng, không phải toggle). */
    fun quietPayloads(state: AcState): List<Long> =
        listOf(encode(state), if (state.quiet) QUIET_ON else QUIET_OFF)
}
