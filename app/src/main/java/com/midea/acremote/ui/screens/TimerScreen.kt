package com.midea.acremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.components.SectionCard

@Composable
fun TimerScreen(vm: RemoteViewModel) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TimerBlock(
            title = "Hẹn giờ bật máy",
            minutes = state.onTimerMinutes,
            enabled = state.timerAvailable,
            onStep = { vm.setOnTimer(state.onTimerMinutes + it) },
            onClear = { vm.setOnTimer(0) }
        )

        TimerBlock(
            title = "Hẹn giờ tắt máy",
            minutes = state.offTimerMinutes,
            enabled = true,
            onStep = { vm.setOffTimer(state.offTimerMinutes + it) },
            onClear = { vm.setOffTimer(0) }
        )

        SectionCard(title = "Ghi chú") {
            Text(
                text = "• Bước hẹn giờ là 30 phút, tối đa 24 giờ.\n" +
                    "• Hẹn giờ bật máy bị vô hiệu khi bật Follow Me " +
                    "(giao thức dùng chung byte).\n" +
                    "• Mỗi thay đổi được gửi ngay qua hồng ngoại như bấm trên remote thật.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = vm::clearTimers,
                enabled = state.onTimerMinutes > 0 || state.offTimerMinutes > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xoá toàn bộ hẹn giờ")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TimerBlock(
    title: String,
    minutes: Int,
    enabled: Boolean,
    onStep: (Int) -> Unit,
    onClear: () -> Unit
) {
    SectionCard(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Đang đặt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (minutes > 0) formatTimer(minutes) else "Tắt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (minutes > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onStep(-30) },
                enabled = enabled && minutes >= 30,
                modifier = Modifier.weight(1f)
            ) { Text("− 30 phút") }
            OutlinedButton(
                onClick = { onStep(30) },
                enabled = enabled && minutes + 30 <= 24 * 60,
                modifier = Modifier.weight(1f)
            ) { Text("+ 30 phút") }
            OutlinedButton(
                onClick = onClear,
                enabled = minutes > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Tắt") }
        }
        if (!enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Không dùng được khi bật Follow Me.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimer(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)
