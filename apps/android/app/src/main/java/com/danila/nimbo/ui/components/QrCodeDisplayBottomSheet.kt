package com.danila.nimbo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danila.nimbo.ui.i18n.loc
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDisplayBottomSheet(
    url: String,
    onDismiss: () -> Unit
) {
    val nebulaColors = LocalNebulaColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val compactLayout = configuration.screenHeightDp < 720
    val outerPadding = if (compactLayout) 12.dp else 16.dp
    val contentPadding = if (compactLayout) 18.dp else 24.dp
    val qrSize = if (compactLayout) 184.dp else 220.dp
    val qrCornerRadius = if (compactLayout) 24.dp else 32.dp
    val primarySpacer = if (compactLayout) 14.dp else 24.dp
    val secondarySpacer = if (compactLayout) 16.dp else 28.dp
    val actionHeight = if (compactLayout) 52.dp else 56.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent, // We use custom glass surface
        dragHandle = null, // Custom drag handle inside
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(outerPadding)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Transparent)
        ) {
            // Glass Surface with Blur
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(30.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                nebulaColors.surface.copy(alpha = 0.85f),
                                nebulaColors.surface.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Custom Drag Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(nebulaColors.textTertiary.copy(alpha = 0.3f))
                )

                Spacer(Modifier.height(primarySpacer))

                Text(
                    text = t("QR-код подписки", "Subscription QR"),
                    style = if (compactLayout) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                )

                Text(
                    text = t(
                        "Отсканируйте этот код на другом устройстве, чтобы добавить подписку",
                        "Scan this code on another device to add the subscription"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textSecondary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = if (compactLayout) 18.dp else 26.dp
                    )
                )

                // Premium QR Container
                Box(
                    modifier = Modifier
                        .size(qrSize)
                        .clip(RoundedCornerShape(qrCornerRadius))
                        .background(Color.White)
                        .border(
                            4.dp,
                            nebulaColors.accent.copy(alpha = 0.1f),
                            RoundedCornerShape(qrCornerRadius)
                        )
                        .padding(if (compactLayout) 16.dp else 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Black.copy(alpha = 0.9f)
                    )
                }

                Spacer(Modifier.height(if (compactLayout) 18.dp else 28.dp))

                // Link Card (Modernized)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = nebulaColors.textSecondary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Subscription URL", url))
                                Toast.makeText(
                                    context,
                                    loc("Ссылка скопирована", "Link copied"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                null,
                                tint = nebulaColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(secondarySpacer))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, loc("Поделиться подпиской", "Share subscription")))
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(actionHeight),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = nebulaColors.accent,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Поделиться", "Share"), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(actionHeight),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            nebulaColors.textTertiary.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            t("Закрыть", "Close"),
                            color = nebulaColors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
