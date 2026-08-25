package com.gopay.example.checkout

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import cz.gopay.sdk.model.QrPaymentDetails

/**
 * Turns whatever `getQrPaymentInfo` returned into something scannable.
 *
 * The gateway's `format: PNG` payload shape isn't guaranteed, so this is deliberately defensive:
 * if the payload decodes as a base64 image we show that, otherwise we treat it as a payment string
 * (SPAYD and friends) and encode it locally. Returns `null` when there's nothing to render, and the
 * sheet falls back to the account rows.
 */
fun QrPaymentDetails.toBitmapOrNull(sizePx: Int = 640): Bitmap? {
    val payload = qrCode.spayd ?: qrCode.sepa ?: qrCode.paybysquare ?: qrCode.mnbQr ?: return null
    return payload.decodeBase64ImageOrNull() ?: encodeQrBitmap(payload, sizePx)
}

private fun String.decodeBase64ImageOrNull(): Bitmap? = try {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: IllegalArgumentException) {
    null
}

private fun encodeQrBitmap(contents: String, sizePx: Int): Bitmap? = try {
    val matrix = QRCodeWriter().encode(
        contents,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    )
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val offset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
} catch (e: Exception) {
    // A payload too long for a QR symbol, or an encoder failure — fall back to the account rows.
    null
}
