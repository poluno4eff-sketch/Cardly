package ru.cardly.core.barcode

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import ru.cardly.core.model.BarcodeFormat

data class DecodedBarcode(val value: String, val format: BarcodeFormat)

object BarcodeBitmapDecoder {
    fun decode(bitmap: Bitmap): DecodedBarcode? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val result = runCatching {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        }.recoverCatching {
            // Светлый код на тёмном фоне.
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints)
        }.getOrNull() ?: return null
        return DecodedBarcode(result.text, result.barcodeFormat.toCardly())
    }
}

private fun com.google.zxing.BarcodeFormat.toCardly(): BarcodeFormat = when (this) {
    com.google.zxing.BarcodeFormat.CODE_128 -> BarcodeFormat.CODE_128
    com.google.zxing.BarcodeFormat.CODE_39 -> BarcodeFormat.CODE_39
    com.google.zxing.BarcodeFormat.CODE_93 -> BarcodeFormat.CODE_93
    com.google.zxing.BarcodeFormat.CODABAR -> BarcodeFormat.CODABAR
    com.google.zxing.BarcodeFormat.EAN_13 -> BarcodeFormat.EAN_13
    com.google.zxing.BarcodeFormat.EAN_8 -> BarcodeFormat.EAN_8
    com.google.zxing.BarcodeFormat.ITF -> BarcodeFormat.ITF
    com.google.zxing.BarcodeFormat.UPC_A -> BarcodeFormat.UPC_A
    com.google.zxing.BarcodeFormat.UPC_E -> BarcodeFormat.UPC_E
    com.google.zxing.BarcodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
    com.google.zxing.BarcodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    com.google.zxing.BarcodeFormat.PDF_417 -> BarcodeFormat.PDF_417
    com.google.zxing.BarcodeFormat.AZTEC -> BarcodeFormat.AZTEC
    else -> BarcodeFormat.UNKNOWN
}
