package com.midea.acremote.protocol

/**
 * Chế độ vận hành của máy lạnh.
 * Mã v3-bit khớp với [MideaProtocol]: COOL=0, DRY=1, AUTO=2, HEAT=3, FAN=4.
 */
enum class AcMode(val code: Int, val label: String) {
    AUTO(2, "Tự động"),
    COOL(0, "Làm mát"),
    DRY(1, "Hút ẩm"),
    HEAT(3, "Sưởi ấm"),
    FAN(4, "Quạt");

    companion object {
        fun fromCode(code: Int): AcMode = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** Tốc độ quạt. Mã 2-bit khớp với [MideaProtocol]. */
enum class FanSpeed(val code: Int, val label: String) {
    AUTO(0, "Tự động"),
    LOW(1, "Thấp"),
    MEDIUM(2, "Trung bình"),
    HIGH(3, "Cao");

    companion object {
        fun fromCode(code: Int): FanSpeed = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/**
 * Toàn bộ trạng thái máy lạnh mà remote lưu lại (giống remote thật).
 *
 * Các trường [power]..[onTimerMinutes] được đóng gói vào gói 48-bit của giao
 * thức Midea. Các trường còn lại (swing/turbo/eco/led/...) là lệnh "đảo trạng
 * thái" (toggle) gửi bằng gói riêng biệt - remote thật cũng làm tương tự.
 *
 * **Nhiệt độ lưu theo đơn vị gốc (native) của máy** giống hệt remote thật:
 * khi máy đang ở °C thì [temp] ∈ 17..30, khi ở °F thì [temp] ∈ 62..86.
 */
data class AcState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.AUTO,
    val fan: FanSpeed = FanSpeed.AUTO,

    /** Đơn vị gốc của máy (đóng vào bit useFahrenheit của giao thức). */
    val useFahrenheit: Boolean = false,

    /** Nhiệt độ đặt theo đơn vị gốc (°C: 17..30, °F: 62..86). */
    val temp: Int = 25,

    val sleep: Boolean = false,

    /** Follow Me / ComfortSense - gửi nhiệt độ cảm biến của remote. */
    val followMe: Boolean = false,

    /** Nhiệt độ cảm biến theo đơn vị gốc (°C: 0..37, °F: 32..99). */
    val sensorTemp: Int = 25,

    /** Hẹn giờ TẮT (phút). 0 = tắt. Bước 30 phút, tối đa 24 giờ. */
    val offTimerMinutes: Int = 0,

    /** Hẹn giờ BẬT (phút). 0 = tắt. Bước 30 phút. Tắc khi bật Follow Me. */
    val onTimerMinutes: Int = 0,

    /** Âm báo "bíp" khi máy nhận lệnh. Bị ép tắt ở chế độ Follow Me. */
    val beep: Boolean = true,

    /**
     * Bit đặc biệt ở bit 5 của byte4 (bit 37 của gói).
     * Một số máy Pioneer System yêu cầu bit này để chấp nhận lệnh.
     * @see <a href="https://github.com/crankyoldgit/IRremoteESP8266/issues/1342">issue #1342</a>
     */
    val specialBit: Boolean = false,

    // --- Các nút toggle (không nằm trong gói trạng thái) ---
    val swing: Boolean = false,
    val turbo: Boolean = false,
    val eco: Boolean = false,
    val led: Boolean = true,
    val selfClean: Boolean = false,
    val freezeProtect: Boolean = false,
    val quiet: Boolean = false,

    /** Khóa trẻ em - chỉ khóa giao diện app, không gửi tín hiệu IR. */
    val childLock: Boolean = false
) {
    val tempMin: Int get() = if (useFahrenheit) MIN_TEMP_F else MIN_TEMP_C
    val tempMax: Int get() = if (useFahrenheit) MAX_TEMP_F else MAX_TEMP_C

    val sensorTempMin: Int get() = if (useFahrenheit) MIN_SENSOR_F else MIN_SENSOR_C
    val sensorTempMax: Int get() = if (useFahrenheit) MAX_SENSOR_F else MAX_SENSOR_C

    val unitLabel: String get() = if (useFahrenheit) "°F" else "°C"

    /** Chuyển sang đơn vị khác, làm đúng như [IRMideaAC.setUseCelsius]. */
    fun setUnit(useFahrenheit: Boolean): AcState {
        if (this.useFahrenheit == useFahrenheit) return this
        val newTemp = if (useFahrenheit) {
            // Đang ở °C, đổi sang °F: trunc(cToF(temp)) - 62
            celsiusToFahrenheit(temp.toFloat()).toInt() - MIN_TEMP_F
        } else {
            // Đang ở °F, đổi sang °C: trunc(fToC(temp) + 0.5) - 17
            (fahrenheitToCelsius(temp.toFloat()) + 0.5f).toInt() - MIN_TEMP_C
        }
        val newSensor = if (useFahrenheit) {
            celsiusToFahrenheit(sensorTemp.toFloat()).toInt() - MIN_SENSOR_F
        } else {
            (fahrenheitToCelsius(sensorTemp.toFloat()) + 0.5f).toInt() - MIN_SENSOR_C
        }
        return copy(
            useFahrenheit = useFahrenheit,
            temp = newTemp.coerceIn(if (useFahrenheit) MIN_TEMP_F else MIN_TEMP_C,
                if (useFahrenheit) MAX_TEMP_F else MAX_TEMP_C),
            sensorTemp = newSensor.coerceIn(
                if (useFahrenheit) MIN_SENSOR_F else MIN_SENSOR_C,
                if (useFahrenheit) MAX_SENSOR_F else MAX_SENSOR_C)
        )
    }

    /** Nhiệt độ đặt theo đơn vị đối diện (cho hiển thị "25°C / 77°F"). */
    fun tempIn(useFahrenheit: Boolean): Int = when {
        this.useFahrenheit && !useFahrenheit ->
            (fahrenheitToCelsius(temp.toFloat()) + 0.5f).toInt()
        !this.useFahrenheit && useFahrenheit -> celsiusToFahrenheit(temp.toFloat()).toInt()
        else -> temp
    }

    fun tempCelsius(): Int = tempIn(false)
    fun tempFahrenheit(): Int = tempIn(true)

    fun stepTemp(delta: Int): AcState =
        copy(temp = (temp + delta).coerceIn(tempMin, tempMax))

    fun stepSensorTemp(delta: Int): AcState =
        copy(sensorTemp = (sensorTemp + delta).coerceIn(sensorTempMin, sensorTempMax))

    fun withTemp(value: Int): AcState = copy(temp = value.coerceIn(tempMin, tempMax))

    /** Tự làm sạch chỉ dùng được ở COOL / DRY / AUTO (theo tài liệu remote). */
    val selfCleanAvailable: Boolean
        get() = mode == AcMode.COOL || mode == AcMode.DRY || mode == AcMode.AUTO

    /** Chống đóng băng (8°C Heat) chỉ dùng được ở HEAT. */
    val freezeProtectAvailable: Boolean get() = mode == AcMode.HEAT

    /** Remote thật không cho đổi tốc độ quạt ở chế độ AUTO và DRY. */
    val fanSelectable: Boolean get() = mode != AcMode.AUTO && mode != AcMode.DRY

    /** Nhiệt độ đặt không có ý nghĩa ở chế độ QUẠT. */
    val tempSelectable: Boolean get() = mode != AcMode.FAN

    /** Hẹn giờ bật tắt không dùng cùng lúc với Follow Me. */
    val timerAvailable: Boolean get() = !followMe

    fun toggleSwing() = copy(swing = !swing)
    fun toggleTurbo() = copy(turbo = !turbo)
    fun toggleEco() = copy(eco = !eco)
    fun toggleLed() = copy(led = !led)
    fun toggleSelfClean() = copy(selfClean = !selfClean)
    fun toggleFreezeProtect() = copy(freezeProtect = !freezeProtect)
    fun toggleQuiet() = copy(quiet = !quiet)

    companion object {
        const val MIN_TEMP_C = 17
        const val MAX_TEMP_C = 30
        const val MIN_TEMP_F = 62
        const val MAX_TEMP_F = 86
        const val MIN_SENSOR_C = 0
        const val MAX_SENSOR_C = 37
        const val MIN_SENSOR_F = 32
        const val MAX_SENSOR_F = 99

        /** Giống hệt `celsiusToFahrenheit()` của thư viện tham chiếu. */
        fun celsiusToFahrenheit(c: Float): Float = (c * 9.0f) / 5.0f + 32.0f

        /** Giống hệt `fahrenheitToCelsius()` của thư viện tham chiếu. */
        fun fahrenheitToCelsius(f: Float): Float = (f - 32.0f) * 5.0f / 9.0f
    }
}
