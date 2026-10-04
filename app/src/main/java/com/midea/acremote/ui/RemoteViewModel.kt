package com.midea.acremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.midea.acremote.AppContainer
import com.midea.acremote.data.Favorite
import com.midea.acremote.data.Schedule
import com.midea.acremote.data.Settings
import com.midea.acremote.protocol.AcMode
import com.midea.acremote.protocol.AcState
import com.midea.acremote.protocol.FanSpeed
import com.midea.acremote.protocol.MideaProtocol
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Chủ tịch điều khiển duy nhất của app.
 *
 * Mọi thao tác trên remote:
 *  1. cập nhật [state] ngay lập tức (UI phản hồi tức thì),
 *  2. lưu vào DataStore,
 *  3. đóng gói payload và phát ra kênh IR đã chọn.
 */
class RemoteViewModel(private val container: AppContainer) : ViewModel() {

    private val repo get() = container.repository

    val settings: StateFlow<Settings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val favorites: StateFlow<List<Favorite>> = repo.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val schedules: StateFlow<List<Schedule>> = repo.schedules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _local = MutableStateFlow(AcState())
    private var loaded = false
    private var lastPersisted: AcState? = null

    /** Trạng thái remote hiện tại (nguồn sự thật cho UI). */
    val state: StateFlow<AcState> = _local

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val message = _message.asSharedFlow()

    private val _lastSent = MutableStateFlow<String?>(null)
    val lastSent: StateFlow<String?> = _lastSent

    private val _phoneIrAvailable = MutableStateFlow<Boolean?>(null)
    val phoneIrAvailable: StateFlow<Boolean?> = _phoneIrAvailable

    init {
        viewModelScope.launch {
            _phoneIrAvailable.value = runCatching { container.phoneSender.isAvailable() }.getOrDefault(false)
        }
        viewModelScope.launch {
            settings.collect { s ->
                if (!loaded) {
                    loaded = true
                    _local.value = s.state
                } else if (s.state != _local.value && s.state != lastPersisted) {
                    // Thay đổi từ nguồn khác (vd: lịch hẹn chạy nền).
                    _local.value = s.state
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Gửi tín hiệu
    // ------------------------------------------------------------------

    private suspend fun transmit(state: AcState, extra: Long? = null) {
        try {
            val s = settings.value
            val text = container.sendState(state, s, extra)
            _lastSent.value = text
        } catch (t: Throwable) {
            _message.tryEmit("Lỗi: ${t.message ?: "không gửi được tín hiệu"}")
        }
    }

    /** Cập nhật trạng thái và phát ngay. */
    private fun mutate(transform: (AcState) -> AcState) {
        val new = transform(_local.value)
        if (new == _local.value) return
        _local.value = new
        viewModelScope.launch {
            repo.setState(new)
            lastPersisted = new
            transmit(new)
        }
    }

    /** Cập nhật trạng thái kèm một gói toggle riêng biệt. */
    private fun mutateWithToggle(transform: (AcState) -> AcState, toggle: Long) {
        val new = transform(_local.value)
        _local.value = new
        viewModelScope.launch {
            repo.setState(new)
            lastPersisted = new
            transmit(new, toggle)
        }
    }

    // ------------------------------------------------------------------
    // Các nút chính
    // ------------------------------------------------------------------

    fun power() = mutate { it.copy(power = !it.power) }

    fun setMode(mode: AcMode) = mutate { s ->
        var n = s.copy(mode = mode)
        if (mode != AcMode.HEAT) n = n.copy(freezeProtect = false)
        if (mode != AcMode.COOL && mode != AcMode.DRY && mode != AcMode.AUTO) {
            n = n.copy(selfClean = false)
        }
        n
    }

    fun setFan(fan: FanSpeed) = mutate { if (it.fanSelectable) it.copy(fan = fan) else it }

    fun stepTemp(delta: Int) = mutate { if (it.tempSelectable) it.stepTemp(delta) else it }

    fun setUnit(useFahrenheit: Boolean) = mutate { it.setUnit(useFahrenheit) }

    fun toggleSleep() = mutate { it.copy(sleep = !it.sleep) }

    fun toggleFollowMe() = mutate {
        if (it.followMe) it.copy(followMe = false)
        else it.copy(followMe = true, onTimerMinutes = 0)
    }

    fun stepSensorTemp(delta: Int) = mutate { it.stepSensorTemp(delta) }

    fun setOnTimer(minutes: Int) = mutate {
        val m = minutes.coerceIn(0, MAX_TIMER_MIN)
        it.copy(
            onTimerMinutes = m,
            followMe = if (m > 0) false else it.followMe
        )
    }

    fun setOffTimer(minutes: Int) = mutate {
        it.copy(offTimerMinutes = minutes.coerceIn(0, MAX_TIMER_MIN))
    }

    fun clearTimers() = mutate { it.copy(onTimerMinutes = 0, offTimerMinutes = 0) }

    fun setSpecialBit(on: Boolean) = mutate { it.copy(specialBit = on) }

    fun setBeep(on: Boolean) = mutate { it.copy(beep = on) }

    fun resend() = viewModelScope.launch { transmit(_local.value) }

    // ------------------------------------------------------------------
    // Các nút toggle (gửi kèm gói lệnh đặc biệt)
    // ------------------------------------------------------------------

    fun toggleSwing() = mutateWithToggle({ it.toggleSwing() }, MideaProtocol.TOGGLE_SWING)
    fun toggleTurbo() = mutateWithToggle({ it.toggleTurbo() }, MideaProtocol.TOGGLE_TURBO)
    fun toggleEco() = mutateWithToggle({ it.toggleEco() }, MideaProtocol.TOGGLE_ECONO)
    fun toggleLed() = mutateWithToggle({ it.toggleLed() }, MideaProtocol.TOGGLE_LIGHT)

    fun toggleSelfClean() = mutateWithToggle(
        { s -> if (s.selfCleanAvailable) s.toggleSelfClean() else s },
        MideaProtocol.TOGGLE_SELF_CLEAN
    )

    fun toggleFreezeProtect() = mutateWithToggle(
        { s -> if (s.freezeProtectAvailable) s.toggleFreezeProtect() else s },
        MideaProtocol.TOGGLE_8C_HEAT
    )

    fun toggleQuiet() {
        val new = _local.value.toggleQuiet()
        _local.value = new
        viewModelScope.launch {
            repo.setState(new)
            lastPersisted = new
            val payloads = MideaProtocol.quietPayloads(new)
            try {
                val text = container.send(payloads, settings.value.channel,
                    container.httpConfig(settings.value))
                _lastSent.value = text
            } catch (t: Throwable) {
                _message.tryEmit("Lỗi: ${t.message ?: "không gửi được tín hiệu"}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Yêu thích
    // ------------------------------------------------------------------

    fun saveFavorite(name: String) = viewModelScope.launch {
        val trimmed = name.trim().ifBlank { "Tổ hợp ${favorites.value.size + 1}" }
        repo.upsertFavorite(Favorite(name = trimmed, state = _local.value))
        _message.tryEmit("Đã lưu \"$trimmed\"")
    }

    fun applyFavorite(favorite: Favorite) = mutate { favorite.state.copy(childLock = _local.value.childLock) }

    fun deleteFavorite(id: String) = viewModelScope.launch { repo.deleteFavorite(id) }

    // ------------------------------------------------------------------
    // Lịch hẹn
    // ------------------------------------------------------------------

    fun saveSchedule(schedule: Schedule) = viewModelScope.launch {
        repo.upsertSchedule(schedule)
        if (schedule.enabled) container.scheduler.schedule(schedule)
        else container.scheduler.cancel(schedule.id)
        _message.tryEmit("Đã lưu lịch \"${schedule.name}\"")
    }

    fun deleteSchedule(id: String) = viewModelScope.launch {
        repo.deleteSchedule(id)
        container.scheduler.cancel(id)
    }

    fun setScheduleEnabled(schedule: Schedule, enabled: Boolean) = viewModelScope.launch {
        val updated = schedule.copy(enabled = enabled)
        repo.upsertSchedule(updated)
        if (enabled) container.scheduler.schedule(updated) else container.scheduler.cancel(updated.id)
    }

    // ------------------------------------------------------------------
    // Cài đặt
    // ------------------------------------------------------------------

    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(on) }

    fun setHttp(url: String, body: String, auth: String) = viewModelScope.launch {
        repo.setHttp(url, body, auth)
    }

    fun setChannel(channel: com.midea.acremote.ir.IrChannel) = viewModelScope.launch {
        repo.setChannel(channel)
        _message.tryEmit("Đã chuyển sang ${channel.label}")
    }

    fun testChannel() = viewModelScope.launch {
        try {
            val s = settings.value
            val text = container.sendState(_local.value.copy(power = _local.value.power), s)
            _lastSent.value = text
            _message.tryEmit("Gửi thử thành công")
        } catch (t: Throwable) {
            _message.tryEmit("Thử thất bại: ${t.message}")
        }
    }

    fun notify(text: String) = _message.tryEmit(text)

    companion object {
        const val MAX_TIMER_MIN = 24 * 60

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RemoteViewModel(container) as T
        }
    }
}
