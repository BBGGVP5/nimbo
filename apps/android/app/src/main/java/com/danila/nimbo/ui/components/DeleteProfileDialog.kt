package com.danila.nimbo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.serverCountEn
import com.danila.nimbo.ui.i18n.serverCountRu
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

private val DeleteProfileColor = Color(0xFFFF5C62)

@Composable
fun DeleteProfileDialog(
    profileName: String,
    serverCount: Int? = null,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val displayName = profileName.ifBlank { t("Без названия", "Unnamed") }
    val serverCountText = serverCount?.let { t(serverCountRu(it), serverCountEn(it)) }

    NebulaMorphicDialog(
        onDismissRequest = onDismissRequest,
        title = t("Удалить профиль?", "Delete profile?"),
        description = t(
            "Проверьте профиль перед удалением. Это действие нельзя отменить.",
            "Check the profile before deleting it. This action cannot be undone."
        ),
        confirmButtonText = t("Удалить", "Delete"),
        cancelButtonText = t("Отмена", "Cancel"),
        confirmButtonColor = DeleteProfileColor,
        headerIcon = Icons.Outlined.Delete,
        headerIconTint = DeleteProfileColor,
        onConfirm = onConfirm
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = nebulaColors.textPrimary.copy(alpha = 0.055f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = nebulaColors.textPrimary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(DeleteProfileColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = DeleteProfileColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        color = nebulaColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (serverCountText != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = serverCountText,
                            color = nebulaColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeleteProfileColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = DeleteProfileColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    text = t(
                        "Подписка и все её серверы будут удалены с устройства.",
                        "The subscription and all of its servers will be removed from this device."
                    ),
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
