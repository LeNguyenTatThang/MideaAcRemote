package com.midea.acremote.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.midea.acremote.data.Favorite
import com.midea.acremote.protocol.AcState
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.components.SectionCard

@Composable
fun FavoritesScreen(vm: RemoteViewModel) {
    val favorites by vm.favorites.collectAsState()
    val state by vm.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<Favorite?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Trạng thái hiện tại") {
            Text(
                text = summarize(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { vm.saveFavorite("") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Lưu trạng thái hiện tại") }
        }

        if (favorites.isEmpty()) {
            SectionCard {
                Text(
                    text = "Chưa có tổ hợp nào. Bấm \"Lưu trạng thái hiện tại\" để tạo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteCard(
                        favorite = favorite,
                        onApply = { vm.applyFavorite(favorite) },
                        onDelete = { pendingDelete = favorite }
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xoá tổ hợp?") },
            text = { Text("\"${target.name}\" sẽ bị xoá.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFavorite(target.id)
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
private fun FavoriteCard(favorite: Favorite, onApply: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summarize(favorite.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onApply) { Text("Áp dụng") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Xoá")
            }
        }
    }
}

internal fun summarize(s: AcState): String {
    val mode = s.mode.label
    val fan = s.fan.label
    val temp = if (s.tempSelectable) "${s.temp}${s.unitLabel}" else "—"
    val power = if (s.power) "Bật" else "Tắt"
    return "$power · $mode · $temp · quạt $fan"
}
