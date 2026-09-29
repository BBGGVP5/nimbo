package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

@Composable
fun GlassHeader(title: String, icon: ImageVector, iconColor: Color, subtitle: String? = null,
    onBack: (() -> Unit)? = null, actions: @Composable (RowScope.() -> Unit)? = null, bordered: Boolean = true) {
    val colors = LocalNebulaColors.current
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        NimboBrandHeader()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Назад", "Back"), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            actions?.invoke(this)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
    }
}
