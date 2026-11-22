package com.mindspark.compose.features.rooms.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun JoinRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val trimmed = code.trim()
    val isValid = trimmed.isNotEmpty() && trimmed.length <= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join with code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 8) code = it.uppercase() },
                label = { Text("Invitation code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = isValid
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
