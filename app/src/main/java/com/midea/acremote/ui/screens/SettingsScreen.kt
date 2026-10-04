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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.midea.acremote.ir.IrChannel
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: RemoteViewModel) {
    val settings by vm.settings.collectAsState()
    val phoneAvailable by vm.phoneIrAvailable.collectAsState()

    var url by remember(settings.httpUrl) { mutableStateOf(settings.httpUrl) }
    var body by remember(settings.httpBody) { mutableStateOf(settings.httpBody) }
    var auth by remember(settings.httpAuth) { mutableStateOf(settings.httpAuth) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(settings.httpUrl, settings.httpBody, settings.httpAuth) {
        if (!dirty) {
            url = settings.httpUrl
            body = settings.httpBody
            auth = settings.httpAuth
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Kênh phát tín hiệu") {
            ChannelOption(
                title = IrChannel.PHONE_IR.label,
                description = when (phoneAvailable) {
                    null -> "Đang kiểm tra..."
                    true -> "Cổng hồng ngoại sẵn sàng."
                    false -> "Máy này không có cổng hồng ngoại."
                },
                selected = settings.channel == IrChannel.PHONE_IR,
                enabled = phoneAvailable != false,
                onSelect = { vm.setChannel(IrChannel.PHONE_IR) }
            )
            Spacer(Modifier.height(8.dp))
            ChannelOption(
                title = IrChannel.WIFI_IR.label,
                description = if (settings.httpUrl.isBlank())
                    "Chưa cấu hình địa chỉ thiết bị."
                else "Đang trỏ tới ${settings.httpUrl}",
                selected = settings.channel == IrChannel.WIFI_IR,
                enabled = true,
                onSelect = { vm.setChannel(IrChannel.WIFI_IR) }
            )
        }

        SectionCard(title = "Cấu hình IR blaster WiFi") {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; dirty = true },
                label = { Text("URL gửi lệnh (http://.../send)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it; dirty = true },
                label = { Text("Body JSON (template)") },
                placeholder = { Text("...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Biến có thể dùng: {carrier}, {timings}, {timings_csv}, {repeat}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = auth,
                onValueChange = { auth = it; dirty = true },
                label = { Text("Header Authorization (tuỳ chọn)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.setHttp(url, body, auth)
                        dirty = false
                    },
                    enabled = dirty,
                    modifier = Modifier.weight(1f)
                ) { Text("Lưu") }
                OutlinedButton(
                    onClick = {
                        vm.setHttp(url, body, auth)
                        dirty = false
                        vm.testChannel()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Lưu & gửi thử") }
            }
        }

        SectionCard(title = "Hiển thị") {
            SwitchRow(
                label = "Giữ màn hình sáng khi dùng remote",
                checked = settings.keepScreenOn,
                onCheckedChange = vm::setKeepScreenOn
            )
        }

        SectionCard(title = "Nâng cao") {
            SwitchRow(
                label = "Bit đặc biệt (Pioneer System)",
                checked = settings.state.specialBit,
                onCheckedChange = vm::setSpecialBit
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Một số máy Pioneer System yêu cầu bit này để chấp nhận lệnh. " +
                    "Nếu máy không nhận lệnh dù sóng tốt, hãy thử bật.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "Về app") {
            Text(
                text = "Midea AC Remote 1.0.0\n" +
                    "Giao thức hồng ngoại Midea 48-bit (38 kHz), " +
                    "đối chiếu với IRremoteESP8266.\n" +
                    "Hỗ trợ: Làm mát · Hút ẩm · Sưởi ấm · Quạt · Tự động, " +
                    "4 tốc độ quạt, hẹn giờ bật/tắt, Follow Me, " +
                    "Turbo/Eco/LED/Sleep/Swipe (đảo gió), tự làm sạch, " +
                    "chống đóng băng, im lặng.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChannelOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.outline
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = selected, enabled = enabled, onCheckedChange = { onSelect() })
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
