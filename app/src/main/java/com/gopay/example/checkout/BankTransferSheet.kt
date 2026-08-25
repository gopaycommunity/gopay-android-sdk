package com.gopay.example.checkout

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cz.gopay.sdk.model.QrPaymentDetails
import java.io.File
import java.io.FileOutputStream

/**
 * Renders `QrPaymentDetails` from `session.getQrPaymentInfo(...)` — the recipient account plus a
 * scannable code — for shoppers who'd rather pay from their banking app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankTransferSheet(
    details: QrPaymentDetails,
    cart: DemoCart,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val bitmap = remember(details) { details.toBitmapOrNull() }

    val local = details.recipient?.bankAccount?.local
    val international = details.recipient?.bankAccount?.international

    // Every field is optional — collect first, so an empty result doesn't render a blank card.
    val rows = remember(details) {
        listOfNotNull(
            details.recipient?.name?.takeIf { it.isNotBlank() }?.let { "Recipient" to it },
            local?.let { acc ->
                val number = if (acc.prefix.isNotBlank()) "${acc.prefix}-${acc.accountNumber}" else acc.accountNumber
                number.takeIf { it.isNotBlank() }?.let { "Account" to it }
            },
            local?.bankCode?.takeIf { it.isNotBlank() }?.let { "Bank code" to it },
            local?.variableSymbol?.takeIf { it.isNotBlank() }?.let { "Variable symbol" to it },
            international?.iban?.takeIf { it.isNotBlank() }?.let { "IBAN" to it },
            international?.bic?.takeIf { it.isNotBlank() }?.let { "BIC" to it },
            international?.reference?.takeIf { it.isNotBlank() }?.let { "Reference" to it }
        )
    }

    // Text handed to the share sheet alongside the QR bitmap (when there is one).
    val shareText = remember(details, rows) {
        (listOf("Bank transfer — ${cart.formatted(details.amount)}") + rows.map { (label, value) -> "$label: $value" })
            .joinToString("\n")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CheckoutTheme.canvas
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CheckoutTheme.gutter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { shareBankTransfer(context, bitmap, shareText) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = CheckoutTheme.ink
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Transfer this amount", fontSize = 13.sp, color = CheckoutTheme.inkMuted)
                    Text(
                        text = cart.formatted(details.amount),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = CheckoutTheme.ink
                    )
                }
            }

            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(CheckoutTheme.cardRadius))
                        .border(1.dp, CheckoutTheme.hairline, RoundedCornerShape(CheckoutTheme.cardRadius))
                        .padding(14.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Bank transfer QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(200.dp)
                    )
                }
            } else {
                Text(
                    text = "The gateway returned no QR payload for this payment — use the account details below.",
                    fontSize = 12.5.sp,
                    color = CheckoutTheme.inkMuted,
                    textAlign = TextAlign.Center
                )
            }

            if (rows.isNotEmpty()) {
                SurfaceCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    rows.forEachIndexed { index, (label, value) ->
                        if (index > 0) Divider(color = CheckoutTheme.hairline)
                        DetailRow(label, value) { copyToClipboard(context, label, value) }
                    }
                }
            } else {
                Text(
                    text = "The gateway returned no recipient account for this payment — scan the code above to pay.",
                    fontSize = 12.5.sp,
                    color = CheckoutTheme.inkMuted,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "Your order completes once the transfer arrives. Nothing is charged from this screen.",
                fontSize = 12.5.sp,
                color = CheckoutTheme.inkMuted,
                textAlign = TextAlign.Center
            )

            PrimaryButton(text = "Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = CheckoutTheme.inkMuted)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = CheckoutTheme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = CheckoutTheme.accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

/**
 * Shares the QR bitmap (if any) plus the account details as text. A bitmap can't be attached to
 * an intent directly — it needs a `content://` URI, so this writes it to the cache dir first and
 * hands it out via the app's `FileProvider` (declared in AndroidManifest.xml / file_paths.xml).
 * Falls back to a text-only share if the bitmap is absent or the write fails.
 */
private fun shareBankTransfer(context: Context, bitmap: Bitmap?, text: String) {
    val imageUri = bitmap?.let { runCatching { saveBitmapToCache(context, it) }.getOrNull() }

    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (imageUri != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }

    if (imageUri == null && bitmap != null) {
        Toast.makeText(context, "Couldn't attach the QR image — sharing details only.", Toast.LENGTH_SHORT).show()
    }
    context.startActivity(Intent.createChooser(intent, "Share bank transfer"))
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, "bank_transfer_qr.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
