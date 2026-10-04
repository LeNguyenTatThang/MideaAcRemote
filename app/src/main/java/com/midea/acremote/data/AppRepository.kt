package com.midea.acremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.midea.acremote.ir.IrChannel
import com.midea.acremote.protocol.AcState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "midea_ac_remote")

/**
 * Ngồn dữ liệu duy nhất của app: cài đặt, trạng thái remote, yêu thích, lịch hẹn.
 * Dùng DataStore Preferences, mọi thứ lưu dạng chuỗi để dễ debug.
 */
class AppRepository(private val context: Context) {

    private object Keys {
        val CHANNEL = stringPreferencesKey("channel")
        val HTTP_URL = stringPreferencesKey("http_url")
        val HTTP_BODY = stringPreferencesKey("http_body")
        val HTTP_AUTH = stringPreferencesKey("http_auth")
        val KEEP_SCREEN_ON = stringPreferencesKey("keep_screen_on")
        val STATE = stringPreferencesKey("state")
        val FAVORITES = stringPreferencesKey("favorites")
        val SCHEDULES = stringPreferencesKey("schedules")
    }

    val settings: Flow<Settings> = context.appDataStore.data.map { p ->
        Settings(
            channel = p[Keys.CHANNEL]?.let { id -> IrChannel.entries.firstOrNull { it.id == id } }
                ?: IrChannel.PHONE_IR,
            httpUrl = p[Keys.HTTP_URL] ?: "",
            httpBody = p[Keys.HTTP_BODY] ?: com.midea.acremote.ir.HttpIrSender.DEFAULT_BODY_TEMPLATE,
            httpAuth = p[Keys.HTTP_AUTH] ?: "",
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] != "0",
            state = StateCodec.decode(p[Keys.STATE])
        )
    }

    val favorites: Flow<List<Favorite>> = context.appDataStore.data.map { p ->
        decodeFavorites(p[Keys.FAVORITES])
    }

    val schedules: Flow<List<Schedule>> = context.appDataStore.data.map { p ->
        decodeSchedules(p[Keys.SCHEDULES])
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    suspend fun setChannel(channel: IrChannel) = edit { it[Keys.CHANNEL] = channel.id }

    suspend fun setHttp(url: String, body: String, auth: String) = edit {
        it[Keys.HTTP_URL] = url.trim()
        it[Keys.HTTP_BODY] = body.ifBlank { com.midea.acremote.ir.HttpIrSender.DEFAULT_BODY_TEMPLATE }
        it[Keys.HTTP_AUTH] = auth.trim()
    }

    suspend fun setKeepScreenOn(on: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = if (on) "1" else "0" }

    suspend fun setState(state: AcState) = edit { it[Keys.STATE] = StateCodec.encode(state) }

    // ------------------------------------------------------------------
    // Favorites
    // ------------------------------------------------------------------

    suspend fun upsertFavorite(favorite: Favorite) = edit { p ->
        val list = decodeFavorites(p[Keys.FAVORITES]).filterNot { it.id == favorite.id } + favorite
        p[Keys.FAVORITES] = encodeFavorites(list)
    }

    suspend fun deleteFavorite(id: String) = edit { p ->
        val list = decodeFavorites(p[Keys.FAVORITES]).filterNot { it.id == id }
        p[Keys.FAVORITES] = encodeFavorites(list)
    }

    // ------------------------------------------------------------------
    // Schedules
    // ------------------------------------------------------------------

    suspend fun upsertSchedule(schedule: Schedule) = edit { p ->
        val list = decodeSchedules(p[Keys.SCHEDULES]).filterNot { it.id == schedule.id } + schedule
        p[Keys.SCHEDULES] = encodeSchedules(list)
    }

    suspend fun deleteSchedule(id: String) = edit { p ->
        val list = decodeSchedules(p[Keys.SCHEDULES]).filterNot { it.id == id }
        p[Keys.SCHEDULES] = encodeSchedules(list)
    }

    suspend fun setScheduleEnabled(id: String, enabled: Boolean) = edit { p ->
        val list = decodeSchedules(p[Keys.SCHEDULES]).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        p[Keys.SCHEDULES] = encodeSchedules(list)
    }

    fun scheduleById(id: String): Flow<Schedule?> = context.appDataStore.data.map { p ->
        decodeSchedules(p[Keys.SCHEDULES]).firstOrNull { it.id == id }
    }

    // ------------------------------------------------------------------

    private suspend fun edit(
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ) {
        context.appDataStore.edit(block)
    }

    companion object {
        private fun sanitize(s: String) = s.replace('|', '/').replace('\n', ' ')

        private fun encodeFavorites(list: List<Favorite>): String =
            list.joinToString("\n") { "${it.id}|${sanitize(it.name)}|${StateCodec.encode(it.state)}" }

        private fun decodeFavorites(raw: String?): List<Favorite> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.lineSequence().mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 3) null
                else Favorite(id = parts[0], name = parts[1], state = StateCodec.decode(parts[2]))
            }.toList()
        }

        private fun encodeSchedules(list: List<Schedule>): String =
            list.joinToString("\n") {
                listOf(
                    it.id, sanitize(it.name), it.hour.toString(), it.minute.toString(),
                    it.daysMask.toString(), it.action.name, StateCodec.encode(it.state),
                    if (it.enabled) "1" else "0"
                ).joinToString("|")
            }

        private fun decodeSchedules(raw: String?): List<Schedule> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.lineSequence().mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 8) return@mapNotNull null
                val hour = parts[2].toIntOrNull() ?: return@mapNotNull null
                val minute = parts[3].toIntOrNull() ?: return@mapNotNull null
                Schedule(
                    id = parts[0],
                    name = parts[1],
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    daysMask = (parts[4].toIntOrNull() ?: 0) and 0x7F,
                    action = ScheduleAction.entries.firstOrNull { it.name == parts[5] }
                        ?: ScheduleAction.POWER_ON,
                    state = StateCodec.decode(parts[6]),
                    enabled = parts[7] == "1"
                )
            }.toList()
        }
    }
}
