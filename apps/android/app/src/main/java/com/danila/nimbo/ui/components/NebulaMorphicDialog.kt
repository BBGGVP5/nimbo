package com.danila.nimbo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danila.nimbo.ui.theme.*

@Composable
private fun scaleRoundedCornerShape(shape: RoundedCornerShape, scale: Float): RoundedCornerShape {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dummySize = with(density) { androidx.compose.ui.geometry.Size(500.dp.toPx(), 500.dp.toPx()) }
    return RoundedCornerShape(
        topStart = androidx.compose.foundation.shape.CornerSize(shape.topStart.toPx(dummySize, density) * scale),
        topEnd = androidx.compose.foundation.shape.CornerSize(shape.topEnd.toPx(dummySize, density) * scale),
        bottomEnd = androidx.compose.foundation.shape.CornerSize(shape.bottomEnd.toPx(dummySize, density) * scale),
        bottomStart = androidx.compose.foundation.shape.CornerSize(shape.bottomStart.toPx(dummySize, density) * scale)
    )
}

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
    val maxDialogHeight = (configuration.screenHeightDp * 0.86f).dp
    var isVisible by remember { mutableStateOf(false) }
    
    val cornerScale = LocalGlobalCornerRadius.current
    val blurRadius = LocalGlobalBlurRadius.current
    val reducedTransparency = LocalReducedTransparencyEnabled.current
    
    val baseShape = RoundedCornerShape(32.dp)
    val resolvedShape = scaleRoundedCornerShape(baseShape, cornerScale)
    val finalBlur = if (reducedTransparency) 0.dp else blurRadius.dp
    
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Backdrop Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isVisible) 0.6f else 0f }
                    .background(Color.Black)
            )

            // Dialog Surface
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + scaleIn(spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f)),
                exit = fadeOut(tween(300)) + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = maxDialogHeight)
                        .wrapContentHeight()
                        .clip(resolvedShape)
                        .background(nebulaColors.textPrimary.copy(alpha = 0.03f))
                        .border(
                            BorderStroke(1.dp, Brush.linearGradient(
                                listOf(nebulaColors.textPrimary.copy(alpha = 0.18f), nebulaColors.textPrimary.copy(alpha = 0.06f))
                            )),
                            resolvedShape
                        )
                ) {
                    // Glass Blur Effect
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .blur(finalBlur)
                            .background(
                                Brush.verticalGradient(
                                    listOf(nebulaColors.surface.copy(alpha = 0.82f), nebulaColors.surface.copy(alpha = 0.92f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (headerIcon != null) {
                            val iconTint = headerIconTint ?: confirmButtonColor ?: nebulaColors.accent
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                iconTint.copy(alpha = 0.20f),
                                                iconTint.copy(alpha = 0.08f)
                                            )
                                        )
                                    )
                                    .border(1.dp, iconTint.copy(alpha = 0.28f), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(iconTint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = headerIcon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        Text(
                            text = title,
                            color = nebulaColors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        if (description != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = description,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }

                        Column(modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                            content()
                        }

                        if (cancelButtonText != null || confirmButtonText != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (cancelButtonText != null) {
                                    OutlinedButton(
                                        onClick = onDismissRequest,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, nebulaColors.textTertiary.copy(alpha = 0.2f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = nebulaColors.textSecondary
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(cancelButtonText, fontWeight = FontWeight.Medium, maxLines = 1)
                                    }
                                }

                                if (confirmButtonText != null) {
                                    Button(
                                        onClick = onConfirm,
                                        modifier = Modifier
                                            .weight(if (cancelButtonText != null) 1.2f else 1f)
                                            .height(52.dp)
                                            .border(
                                                0.5.dp,
                                                (confirmButtonColor ?: nebulaColors.accent).copy(alpha = 0.4f),
                                                RoundedCornerShape(16.dp)
                                            ),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = (confirmButtonColor ?: nebulaColors.accent).copy(alpha = 0.25f),
                                            contentColor = confirmButtonColor ?: nebulaColors.accent
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(confirmButtonText, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

