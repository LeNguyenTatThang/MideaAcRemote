package com.midea.acremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.midea.acremote.protocol.AcMode
import com.midea.acremote.protocol.AcState
import com.midea.acremote.protocol.FanSpeed
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.components.ChipRow
import com.midea.acremote.ui.components.SectionCard
import com.midea.acremote.ui.components.clickableNoIndication
import com.midea.acremote.ui.theme.MideaOn
import com.midea.acremote.ui.theme.MideaOff

private data class Feature(
    val label: String,
    val on: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val state by vm.state.collectAsState()
    val lastSent by vm.lastSent.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PowerHeader(
            state = state,
            onPower = vm::power,
            onUnit = { vm.setUnit(it) }
        )

        TemperatureCard(state = state, onStep = vm::stepTemp)

        SectionCard(title = "Chế độ") {
            ChipRow(
                items = AcMode.entries.map { it.label to (it == state.mode) },
                onSelect = { vm.setMode(AcMode.entries[it]) }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tốc độ quạt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                items = FanSpeed.entries.map { it.label to (it == state.fan) },
                enabled = state.fanSelectable,
                onSelect = { vm.setFan(FanSpeed.entries[it]) }
            )
        }

        SectionCard(title = "Tuỳ chọn") {
            ToggleRow(
                label = "Ngủ (Sleep)",
                checked = state.sleep,
                onCheckedChange = { vm.toggleSleep() }
            )
            ToggleRow(
                label = "Cảm biến (Follow Me)",
                checked = state.followMe,
                onCheckedChange = { vm.toggleFollowMe() }
            )
            if (state.followMe) {
                Spacer(Modifier.height(8.dp))
                StepperRow(
                    label = "Nhiệt độ cảm biến",
                    value = state.sensorTemp,
                    unit = state.unitLabel,
                    min = state.sensorTempMin,
                    max = state.sensorTempMax,
                    onStep = vm::stepSensorTemp
                )
            }
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                label = "Bật âm báo khi nhận lệnh",
                checked = state.beep,
                onCheckedChange = { vm.setBeep(it) }
            )
        }

        SectionCard(title = "Chức năng") {
            FeatureGrid(
                features = listOf(
                    Feature("Đảo gió", state.swing, true, vm::toggleSwing),
                    Feature("Turbo", state.turbo, true, vm::toggleTurbo),
                    Feature("Tiết kiệm", state.eco, true, vm::toggleEco),
                    Feature("LED", state.led, true, vm::toggleLed),
                    Feature("Tự làm sạch", state.selfClean, state.selfCleanAvailable, vm::toggleSelfClean),
                    Feature("Chống đóng băng", state.freezeProtect, state.freezeProtectAvailable, vm::toggleFreezeProtect),
                    Feature("Im lặng", state.quiet, true, vm::toggleQuiet)
                )
            )
        }

        TimerSummary(state)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lưu tổ hợp")
            }
            OutlinedButton(onClick = vm::resend, modifier = Modifier.weight(1f)) {
                Text("Gửi lại")
            }
        }

        if (!lastSent.isNullOrBlank()) {
            Text(
                text = lastSent ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showSaveDialog) {
        SaveFavoriteDialog(
            onDismiss = { showSaveDialog = false },
            onSave = {
                vm.saveFavorite(it)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun PowerHeader(
    state: AcState,
    onPower: () -> Unit,
    onUnit: (useFahrenheit: Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onPower,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (state.power) MideaOn else MideaOff
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = "Bật/tắt",
                modifier = Modifier.size(34.dp),
                tint = Color.White
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Máy lạnh",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (state.power) "Đang bật" else "Đang tắt",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.power) MideaOn else MideaOff
            )
        }
        UnitSwitch(useFahrenheit = state.useFahrenheit, onSelect = onUnit)
    }
}

@Composable
private fun UnitSwitch(useFahrenheit: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("°C" to false, "°F" to true).forEach { (label, isF) ->
            val selected = useFahrenheit == isF
            Box(
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickableNoIndication { onSelect(isF) }
                )
            }
        }
    }
}

@Composable
private fun TemperatureCard(state: AcState, onStep: (Int) -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepButton("-", enabled = state.tempSelectable && state.temp > state.tempMin) {
                onStep(-1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.tempSelectable) "${state.temp}${state.unitLabel}" else "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.tempSelectable) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.tempCelsius()}°C  /  ${state.tempFahrenheit()}°F",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StepButton("+", enabled = state.tempSelectable && state.temp < state.tempMax) {
                onStep(1)
            }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    onStep: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        StepButton("−", enabled = value > min) { onStep(-1) }
        Text(
            text = "$value$unit",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center
        )
        StepButton("+", enabled = value < max) { onStep(1) }
    }
}

@Composable
private fun FeatureGrid(features: List<Feature>) {
    features.chunked(3).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { f ->
                FeatureChip(f, Modifier.weight(1f))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FeatureChip(feature: Feature, modifier: Modifier = Modifier) {
    val active = feature.on && feature.enabled
    val background =
        if (!feature.enabled) MaterialTheme.colorScheme.surfaceVariant
        else if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (active) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .clickableNoIndication(enabled = feature.enabled, onClick = feature.onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = feature.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (feature.enabled) contentColor
            else MaterialTheme.colorScheme.outline,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimerSummary(state: AcState) {
    val onLabel = if (state.onTimerMinutes > 0) formatMinutes(state.onTimerMinutes) else "Tắt"
    val offLabel = if (state.offTimerMinutes > 0) formatMinutes(state.offTimerMinutes) else "Tắt"
    SectionCard(title = "Hẹn giờ") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bật máy: $onLabel", style = MaterialTheme.typography.bodyMedium)
            Text("Tắt máy: $offLabel", style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.timerAvailable) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Hẹn giờ bị tắt khi đang bật Follow Me.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "%02d:%02d".format(h, m)
}

@Composable
private fun SaveFavoriteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lưu tổ hợp") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tên tổ hợp") },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
