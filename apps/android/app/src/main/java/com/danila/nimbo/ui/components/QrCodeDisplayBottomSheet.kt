package com.danila.nimbo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.i18n.loc
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Kept under the old name to avoid touching every caller, but intentionally rendered as a
 * Nimbo dialog rather than a Material bottom sheet. QR pairing is a focused action and must stay
 * readable over profile lists and animated backgrounds.
 */
@Composable
fun QrCodeDisplayBottomSheet(
    url: String,
    title: String = t("QR-код подписки", "Subscription QR"),
    description: String = t(
        "Отсканируйте этот код на другом устройстве, чтобы добавить подписку",
        "Scan this code on another device to add the subscription"
    ),
    shareTitle: String = loc("Поделиться подпиской", "Share subscription"),
    allowCopyAndShare: Boolean = true,
    onDismiss: () -> Unit
) {
    val colors = LocalNebulaColors.current
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenHeightDp < 720
    val qrSize = if (compact) 176.dp else 216.dp
    val qrBitmap = remember(url) { generateQrBitmap(url, 768) }

    NebulaMorphicDialog(
        onDismissRequest = onDismiss,
        title = title,
        description = description,
        confirmButtonText = t("Закрыть", "Close"),
        cancelButtonText = null,
        onConfirm = onDismiss,
        headerIcon = Icons.Default.QrCode2
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.controlFill,
                border = BorderStroke(1.dp, colors.panelBorder),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.padding(if (compact) 12.dp else 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(qrSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(
                                width = 2.dp,
                                color = colors.accent.copy(alpha = 0.30f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(if (compact) 12.dp else 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = t("QR-код", "QR code"),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (!allowCopyAndShare) {
                Text(
                    text = t(
                        "Защищённый одноразовый код · Wi‑Fi или Bluetooth",
                        "Secure one-time code · Wi-Fi or Bluetooth"
                    ),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.controlFill,
                    border = BorderStroke(1.dp, colors.panelBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Subscription URL", url))
                                Toast.makeText(
                                    context,
                                    loc("Ссылка скопирована", "Link copied"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = colors.softFill,
                            border = BorderStroke(1.dp, colors.panelBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = t("Копировать", "Copy"),
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, shareTitle))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Поделиться", "Share"), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

private fun generateQrBitmap(value: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val offset = y * size
        for (x in 0 until size) {
            pixels[offset + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
