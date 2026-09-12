package com.danila.nimbo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.theme.*

private fun dialogMenuFill(colors: NebulaColors): Color = colors.panelFill
    .copy(alpha = maxOf(colors.panelFill.alpha, 0.94f))
    .compositeOver(colors.background)

private fun dialogControlFill(colors: NebulaColors): Color = colors.controlFill

private fun dialogBorder(colors: NebulaColors): Color = colors.panelBorder

@Composable
private fun scaleRoundedCornerShape(shape: RoundedCornerShape, scale: Float): RoundedCornerShape {
    val density = LocalDensity.current
    val dummySize = with(density) {
        androidx.compose.ui.geometry.Size(500.dp.toPx(), 500.dp.toPx())
    }
    return RoundedCornerShape(
        topStart = CornerSize(shape.topStart.toPx(dummySize, density) * scale),
        topEnd = CornerSize(shape.topEnd.toPx(dummySize, density) * scale),
        bottomEnd = CornerSize(shape.bottomEnd.toPx(dummySize, density) * scale),
        bottomStart = CornerSize(shape.bottomStart.toPx(dummySize, density) * scale)
    )
}

/**
 * Common dialog shell used by feature screens throughout Nimbo.
 *
 * The surface is deliberately opaque: a dialog must remain readable over dense server lists,
 * charts and animated backgrounds. Feature-specific content is scrollable while the title and
 * actions retain the same hierarchy on every screen.
 */
@Composable
fun NebulaMorphicDialog(
    onDismissRequest: () -> Unit,
    title: String,
    description: String? = null,
    confirmButtonText: String? = "ОК",
    cancelButtonText: String? = "Отмена",
    onConfirm: () -> Unit,
    confirmButtonColor: Color? = null,
    headerIcon: ImageVector? = null,
    headerIconTint: Color? = null,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val nebulaColors = LocalNebulaColors.current
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.88f).dp
    val cornerScale = LocalGlobalCornerRadius.current
    val resolvedShape = scaleRoundedCornerShape(RoundedCornerShape(30.dp), cornerScale)
    val resolvedAccent = confirmButtonColor ?: nebulaColors.accent
    val resolvedIconTint = headerIconTint ?: resolvedAccent

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 620.dp)
                .heightIn(max = maxDialogHeight)
                .navigationBarsPadding()
                .imePadding(),
            shape = resolvedShape,
            color = dialogMenuFill(nebulaColors),
            contentColor = nebulaColors.textPrimary,
            tonalElevation = 0.dp,
            shadowElevation = 22.dp,
            border = BorderStroke(1.dp, dialogBorder(nebulaColors))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (headerIcon != null) {
                        Surface(
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = resolvedIconTint.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, resolvedIconTint.copy(alpha = 0.28f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = headerIcon,
                                    contentDescription = null,
                                    tint = resolvedIconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!description.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = description,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Surface(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = dialogControlFill(nebulaColors),
                        border = BorderStroke(1.dp, dialogBorder(nebulaColors))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = nebulaColors.textSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = nebulaColors.divider)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 14.dp),
                    content = content
                )

                if (cancelButtonText != null || confirmButtonText != null) {
                    HorizontalDivider(color = nebulaColors.divider)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (cancelButtonText != null) {
                            OutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, dialogBorder(nebulaColors)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = dialogControlFill(nebulaColors),
                                    contentColor = nebulaColors.textSecondary
                                )
                            ) {
                                Text(
                                    text = cancelButtonText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (confirmButtonText != null) {
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier
                                    .weight(if (cancelButtonText != null) 1.15f else 1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, resolvedAccent.copy(alpha = 0.72f)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = resolvedAccent,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text(
                                    text = confirmButtonText,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
