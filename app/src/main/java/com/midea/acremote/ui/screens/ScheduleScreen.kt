package com.midea.acremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.midea.acremote.data.Schedule
import com.midea.acremote.data.ScheduleAction
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.components.clickableNoIndication
import java.util.UUID

private data class ScheduleDraft(
    val id: String? = null,
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val daysMask: Int = 0,
    val action: ScheduleAction = ScheduleAction.POWER_ON,
    val enabled: Boolean = true
)

@Composable
fun ScheduleScreen(vm: RemoteViewModel) {
    val schedules by vm.schedules.collectAsState()
    var draft by remember { mutableStateOf<ScheduleDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { draft = ScheduleDraft() }) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm lịch")
            }
        }
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Chưa có lịch hẹn nào.\nBấm nút + để tạo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { vm.setScheduleEnabled(schedule, it) },
                        onEdit = {
                            draft = ScheduleDraft(
                                id = schedule.id,
                                name = schedule.name,
                                hour = schedule.hour,
                                minute = schedule.minute,
                                daysMask = schedule.daysMask,
                                action = schedule.action,
                                enabled = schedule.enabled
                            )
                        },
                        onDelete = { pendingDelete = schedule }
                    )
                }
            }
        }
    }

    draft?.let { d ->
        ScheduleEditDialog(
            draft = d,
            currentStateSummary = summarize(vm.state.value),
            onDismiss = { draft = null },
            onSave = { updated ->
                val base = d.id?.let { id -> schedules.firstOrNull { it.id == id } }
                vm.saveSchedule(
                    Schedule(
                        id = base?.id ?: UUID.randomUUID().toString(),
                        name = updated.name,
                        hour = updated.hour,
                        minute = updated.minute,
                        daysMask = updated.daysMask,
                        action = updated.action,
                        state = base?.state ?: vm.state.value,
                        enabled = updated.enabled
                    )
                )
                draft = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xoá lịch hẹn?") },
            text = { Text("\"${target.name}\" sẽ bị xoá.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSchedule(target.id)
                    pendingDelete = null
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Huỷ") }
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.timeLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${schedule.action.label} · " +
                        if (schedule.recurring) dayLabel(schedule.daysMask) else "Một lần",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Sửa")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Xoá")
            }
        }
    }
}

private fun dayLabel(mask: Int): String =
    Schedule.DAY_LABELS.filterIndexed { i, _ -> ((mask shr i) and 1) == 1 }.joinToString(",")

@Composable
private fun ScheduleEditDialog(
    draft: ScheduleDraft,
    currentStateSummary: String,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft) -> Unit
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var hourText by remember(draft.id) { mutableStateOf("%02d".format(draft.hour)) }
    var minuteText by remember(draft.id) { mutableStateOf("%02d".format(draft.minute)) }
    var daysMask by remember(draft.id) { mutableStateOf(draft.daysMask) }
    var action by remember(draft.id) { mutableStateOf(draft.action) }
    var enabled by remember(draft.id) { mutableStateOf(draft.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Lịch hẹn mới" else "Sửa lịch hẹn") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Giờ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Phút") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionLabel("Lặp lại trong tuần")
                Schedule.DAY_LABELS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { offset, label ->
                            val index = Schedule.DAY_LABELS.indexOf(label)
                            SelectChip(
                                label = label,
                                selected = ((daysMask shr index) and 1) == 1,
                                onClick = { daysMask = daysMask xor (1 shl index) }
                            )
                        }
                        Spacer(Modifier.width(0.dp))
                    }
                }
                Text(
                    text = if (daysMask == 0)
                        "Không chọn ngày = chạy một lần ở lần tới tiếp theo."
                    else "Lặp lại vào các ngày đã chọn.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionLabel("Hành động")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScheduleAction.entries.forEach { a ->
                        SelectChip(
                            label = a.label,
                            selected = action == a,
                            onClick = { action = a },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (action == ScheduleAction.APPLY_STATE) {
                    Text(
                        text = "Tổ hợp sẽ áp dụng: $currentStateSummary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kích hoạt", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: draft.hour
                val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: draft.minute
                onSave(
                    ScheduleDraft(
                        id = draft.id,
                        name = name.trim().ifBlank { "Hẹn giờ" },
                        hour = h,
                        minute = m,
                        daysMask = daysMask,
                        action = action,
                        enabled = enabled
                    )
                )
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .clickableNoIndication(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
