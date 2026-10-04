package com.midea.acremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Đối chiếu từng bit với unit test gốc của IRremoteESP8266
 * (test/ir_Midea_test.cpp) và src/ir_Midea.h/.cpp.
 */
class MideaProtocolTest {

    // ------------------------------------------------------------------
    // Checksum
    // ------------------------------------------------------------------

    @Test
    fun checksum_knownVectors() {
        assertEquals(0x62, MideaProtocol.checksum(MideaProtocol.unpack(0xA1826FFFFF62)))
        assertEquals(0x70, MideaProtocol.checksum(MideaProtocol.unpack(0xA18177FFFF70)))
        assertEquals(0x62, MideaProtocol.checksum(MideaProtocol.unpack(0xA1826FFFFF00)))
        assertEquals(0x00, MideaProtocol.checksum(MideaProtocol.unpack(0x000000000000)))
        assertEquals(0xDF, MideaProtocol.checksum(MideaProtocol.unpack(0x1234567890AB)))
        assertEquals(0xA0, MideaProtocol.checksum(MideaProtocol.unpack(0xFFFFFFFFFFFF)))
    }

    @Test
    fun validChecksum() {
        assertTrue(MideaProtocol.isValidChecksum(0xA1826FFFFF62))
        assertTrue(MideaProtocol.isValidChecksum(0xA18177FFFF70))
        assertFalse(MideaProtocol.isValidChecksum(0x1234567890AB))
    }

    @Test
    fun reverseBits() {
        assertEquals(0xFF, MideaProtocol.reverseBits(0xFF))
        assertEquals(0x00, MideaProtocol.reverseBits(0x00))
        assertEquals(0x7F, MideaProtocol.reverseBits(0xFE))
        assertEquals(0x62, MideaProtocol.reverseBits(0x46))
    }

    // ------------------------------------------------------------------
    // Đóng gói trạng thái
    // ------------------------------------------------------------------

    private fun base(
        power: Boolean = true,
        mode: Int = 2,
        fan: Int = 0,
        temp: Int = 77,
        useF: Boolean = true,
        sleep: Boolean = false,
        follow: Boolean = false,
        sensor: Int = 77,
        onMin: Int = 0,
        offMin: Int = 0,
        beep: Boolean = true,
        special: Boolean = false
    ) = AcState(
        power = power,
        mode = AcMode.fromCode(mode),
        fan = FanSpeed.fromCode(fan),
        useFahrenheit = useF,
        temp = temp,
        sleep = sleep,
        followMe = follow,
        sensorTemp = sensor,
        onTimerMinutes = onMin,
        offTimerMinutes = offMin,
        beep = beep,
        specialBit = special
    )

    @Test
    fun encode_stateReset() {
        assertEquals(0xA1826FFFFF62L, MideaProtocol.encode(base()))
    }

    @Test
    fun encode_power() {
        assertEquals(0xA1026FFFFFE2L, MideaProtocol.encode(base(power = false)))
        assertEquals(0xA1826FFFFF62L, MideaProtocol.encode(base(power = true)))
    }

    @Test
    fun encode_modes() {
        assertEquals(0xA1806FFFFF61L, MideaProtocol.encode(base(mode = 0)))
        assertEquals(0xA1816FFFFF60L, MideaProtocol.encode(base(mode = 1)))
        assertEquals(0xA1826FFFFF62L, MideaProtocol.encode(base(mode = 2)))
        assertEquals(0xA1836FFFFF63L, MideaProtocol.encode(base(mode = 3)))
        assertEquals(0xA1846FFFFF66L, MideaProtocol.encode(base(mode = 4)))
    }

    @Test
    fun encode_fanSpeeds() {
        assertEquals(0xA1826FFFFF62L, MideaProtocol.encode(base(fan = 0)))
        assertEquals(0xA18A6FFFFF6CL, MideaProtocol.encode(base(fan = 1)))
        assertEquals(0xA1926FFFFF7CL, MideaProtocol.encode(base(fan = 2)))
        assertEquals(0xA19A6FFFFF74L, MideaProtocol.encode(base(fan = 3)))
    }

    @Test
    fun encode_sleep() {
        assertEquals(0xA1C26FFFFF22L, MideaProtocol.encode(base(sleep = true)))
    }

    @Test
    fun encode_temperatureBounds() {
        assertEquals(0xA18260FFFF6CL, MideaProtocol.encode(base(temp = 62)))
        assertEquals(0xA18278FFFF78L, MideaProtocol.encode(base(temp = 86)))
        assertEquals(
            0xA18840FFFF56L,
            MideaProtocol.encode(base(mode = 0, fan = 1, temp = 17, useF = false))
        )
        assertEquals(
            0xA1884DFFFF5DL,
            MideaProtocol.encode(base(mode = 0, fan = 1, temp = 30, useF = false))
        )
    }

    /** `ConstructKnownMessage` trong test gốc. */
    @Test
    fun encode_constructKnownMessage() {
        val state = base(
            mode = 2, fan = 0, temp = 23, useF = false,
            follow = true, sensor = 25, beep = true
        )
        assertEquals(0xA482467F1A47L, MideaProtocol.encode(state))
    }

    @Test
    fun encode_onOffTimers() {
        // 7 giờ = 420 phút -> byte1 low7 = ((14-1)<<1)|1 = 0x1B
        val on7h = MideaProtocol.unpack(MideaProtocol.encode(base(onMin = 420, special = true)))
        assertEquals(0x9B, on7h[1])
        assertEquals(0xFF, on7h[2])
        assertTrue(MideaProtocol.isValidChecksum(MideaProtocol.pack(on7h)))

        // 30 phút tắt -> offTimer field = 0
        val off30 = MideaProtocol.unpack(MideaProtocol.encode(base(offMin = 30, special = true)))
        assertEquals(0x81, off30[2])

        // Không hẹn giờ -> byte1 = 0xFF, byte2 = 0xFF
        val none = MideaProtocol.unpack(MideaProtocol.encode(base()))
        assertEquals(0xFF, none[1])
        assertEquals(0xFF, none[2])
    }

    @Test
    fun encode_followMeDisablesOnTimerAndBeep() {
        val state = base(follow = true, sensor = 25, onMin = 420, temp = 23, useF = false)
        val bytes = MideaProtocol.unpack(MideaProtocol.encode(state))
        assertEquals(0, bytes[1] and 0x80)          // disableSensor = 0
        assertEquals(0, bytes[2] and 0x80)          // BeepDisable = 0 (setType Follow)
        assertEquals(MideaProtocol.TYPE_FOLLOW, bytes[5] and 0b111)
    }

    // ------------------------------------------------------------------
    // Giải ngược
    // ------------------------------------------------------------------

    @Test
    fun decode_roundTrip() {
        val vectors = listOf(
            0xA1826FFFFF62L,
            0xA18840FFFF56L,
            0xA482467F1A47L,
            0xA4A3477F1979L,
            0xA1A34DFF9B34L,
            0xA1A34D81FF21L,
            0xA1A368FFFF45L
        )
        for (v in vectors) {
            val decoded = MideaProtocol.decode(v)
            assertTrue("0x%X không phải checksum hợp lệ".format(v), MideaProtocol.isValidChecksum(v))
            assertEquals("round-trip 0x%X".format(v), v, MideaProtocol.encode(decoded))
        }
    }

    @Test
    fun decode_timers() {
        val on7h = MideaProtocol.decode(0xA1A34DFF9B34L)
        assertEquals(420, on7h.onTimerMinutes)
        assertEquals(0, on7h.offTimerMinutes)
        assertEquals(30, on7h.temp)
        assertEquals(AcMode.HEAT, on7h.mode)
        assertTrue(on7h.specialBit)

        val off30 = MideaProtocol.decode(0xA1A34D81FF21L)
        assertEquals(0, off30.onTimerMinutes)
        assertEquals(30, off30.offTimerMinutes)
        assertEquals(30, off30.temp)
    }

    @Test
    fun decode_pioneerSpecialBit() {
        val s = MideaProtocol.decode(0xA1A368FFFF45L)
        assertTrue(s.specialBit)
        assertEquals(0xA1A368FFFF45L, MideaProtocol.encode(s))
        assertNotEquals(0xA1A368FFFF45L, MideaProtocol.encode(s.copy(specialBit = false)))
    }

    // ------------------------------------------------------------------
    // Các gói lệnh đặc biệt
    // ------------------------------------------------------------------

    @Test
    fun togglePackets_haveValidChecksum() {
        val toggles = mapOf(
            "SwingV" to MideaProtocol.TOGGLE_SWING,
            "Econo" to MideaProtocol.TOGGLE_ECONO,
            "Light" to MideaProtocol.TOGGLE_LIGHT,
            "Turbo" to MideaProtocol.TOGGLE_TURBO,
            "SelfClean" to MideaProtocol.TOGGLE_SELF_CLEAN,
            "8CHeat" to MideaProtocol.TOGGLE_8C_HEAT,
            "QuietOn" to MideaProtocol.QUIET_ON,
            "QuietOff" to MideaProtocol.QUIET_OFF
        )
        toggles.forEach { (name, value) ->
            assertTrue("$name checksum sai", MideaProtocol.isValidChecksum(value))
        }
    }

    @Test
    fun togglePayloads_order() {
        val state = base()
        val payloads = MideaProtocol.togglePayloads(state, MideaProtocol.TOGGLE_SWING)
        assertEquals(2, payloads.size)
        assertEquals(MideaProtocol.encode(state), payloads[0])
        assertEquals(MideaProtocol.TOGGLE_SWING, payloads[1])
    }

    // ------------------------------------------------------------------
    // Xung thô
    // ------------------------------------------------------------------

    @Test
    fun pattern_singleMessageStructure() {
        val pattern = MideaProtocol.pattern(0xA1826FFFFF62L)

        // 2 frame x (header 2 + 48 bit x 2 + footer 2) = 200 phần tử
        assertEquals(200, pattern.size)
        assertEquals(MideaProtocol.HEADER_MARK, pattern[0])
        assertEquals(MideaProtocol.HEADER_SPACE, pattern[1])
        assertEquals(105600, pattern[pattern.size - 1])

        // Byte đầu trên dây là byte5 = 0xA1 = 10100001
        // bit7=1 -> ONE_SPACE, bit6=0 -> ZERO_SPACE ...
        assertEquals(MideaProtocol.BIT_MARK, pattern[2])
        assertEquals(MideaProtocol.ONE_SPACE, pattern[3])
        assertEquals(MideaProtocol.BIT_MARK, pattern[4])
        assertEquals(MideaProtocol.ZERO_SPACE, pattern[5])

        // Frame thứ hai là bản đảo: bit đầu tiên đảo lại -> ZERO_SPACE
        val secondFrameStart = 100
        assertEquals(MideaProtocol.HEADER_MARK, pattern[secondFrameStart])
        assertEquals(MideaProtocol.HEADER_SPACE, pattern[secondFrameStart + 1])
        assertEquals(MideaProtocol.ZERO_SPACE, pattern[secondFrameStart + 3])

        // Phải xen kẽ mark/space
        for (i in pattern.indices step 2) assertTrue("vị trí $i phải là mark", pattern[i] % 560 == 0)
    }

    @Test
    fun pattern_durationIsSafe() {
        val single = MideaProtocol.pattern(0xA1826FFFFF62L)
        val ms = MideaProtocol.durationUs(single) / 1000
        assertTrue("single message = ${ms}ms phải < 2000ms", ms < 2000)
        assertTrue("single message = ${ms}ms phải > 250ms", ms > 250)

        val two = MideaProtocol.pattern(0xA1826FFFFF62L, MideaProtocol.TOGGLE_SWING)
        val twoMs = MideaProtocol.durationUs(two) / 1000
        assertTrue("two messages = ${twoMs}ms phải < 2000ms", twoMs < 2000)
    }

    @Test
    fun pattern_wireByteOrder() {
        // 0x55AA55AA55AA: byte đầu trên dây 0x55 = 01010101
        val pattern = MideaProtocol.pattern(0x55AA55AA55AAL)
        val expectedSpaces = listOf(560, 1680, 560, 1680, 560, 1680, 560, 1680)
        for (i in 0..7) {
            assertEquals("bit $i", expectedSpaces[i], pattern[3 + i * 2])
        }
        // Byte thứ hai 0xAA = 10101010
        for (i in 0..7) {
            val expected = if (i % 2 == 0) 1680 else 560
            assertEquals("byte2 bit $i", expected, pattern[19 + i * 2])
        }
    }

    // ------------------------------------------------------------------
    // Chuyển đổi nhiệt độ
    // ------------------------------------------------------------------

    @Test
    fun temperatureConversions() {
        // celsiusToFahrenheit / fahrenheitToCelsius khớp IRutils.cpp
        assertEquals(77.0f, AcState.celsiusToFahrenheit(25f), 0.001f)
        assertEquals(73.4f, AcState.celsiusToFahrenheit(23f), 0.001f)
        assertEquals(64.4f, AcState.celsiusToFahrenheit(18f), 0.001f)
        assertEquals(18.333f, AcState.fahrenheitToCelsius(65f), 0.001f)

        // getTemp(true) khi native F: trunc(fToC + 0.5)
        assertEquals(18, (AcState.fahrenheitToCelsius(65f) + 0.5f).toInt())
        assertEquals(21, (AcState.fahrenheitToCelsius(70f) + 0.5f).toInt())
        assertEquals(27, (AcState.fahrenheitToCelsius(80f) + 0.5f).toInt())
    }

    @Test
    fun unitSwitchRoundTrip() {
        var s = AcState(useFahrenheit = false, temp = 23, sensorTemp = 25)
        s = s.setUnit(useFahrenheit = true)
        assertEquals(73, s.temp)
        s = s.setUnit(useFahrenheit = false)
        assertEquals(23, s.temp)

        // Min/max không vượt biên sau khi đổi đơn vị
        val min = AcState(useFahrenheit = false, temp = 17).setUnit(true)
        assertEquals(62, min.temp)
        val max = AcState(useFahrenheit = false, temp = 30).setUnit(true)
        assertEquals(86, max.temp)
    }

    @Test
    fun stepTempClampsToNativeRange() {
        assertEquals(17, AcState(useFahrenheit = false, temp = 17).stepTemp(-1).temp)
        assertEquals(30, AcState(useFahrenheit = false, temp = 30).stepTemp(1).temp)
        assertEquals(62, AcState(useFahrenheit = true, temp = 62).stepTemp(-1).temp)
        assertEquals(86, AcState(useFahrenheit = true, temp = 86).stepTemp(1).temp)
    }

    // ------------------------------------------------------------------
    // Trạng thái đi kèm
    // ------------------------------------------------------------------

    @Test
    fun featureAvailability() {
        assertFalse(AcState(mode = AcMode.HEAT).selfCleanAvailable)
        assertTrue(AcState(mode = AcMode.COOL).selfCleanAvailable)
        assertTrue(AcState(mode = AcMode.HEAT).freezeProtectAvailable)
        assertFalse(AcState(mode = AcMode.AUTO).fanSelectable)
        assertFalse(AcState(mode = AcMode.DRY).fanSelectable)
        assertTrue(AcState(mode = AcMode.COOL).fanSelectable)
        assertFalse(AcState(mode = AcMode.FAN).tempSelectable)
        assertFalse(AcState(followMe = true).timerAvailable)
    }
}
