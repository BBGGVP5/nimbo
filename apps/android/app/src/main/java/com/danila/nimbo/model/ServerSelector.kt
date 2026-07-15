package com.danila.nimbo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danila.nimbo.model.Server

@Composable
fun ServerSelector(
    servers: List<Server>,
    selected: Server?,
    onSelect: (Server) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Text(text = selected?.name ?: "Выберите сервер")
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { srv ->
                DropdownMenuItem(text = { Text(srv.name) }, onClick = {
                    onSelect(srv); expanded = false
                })
            }
        }
    }
}
