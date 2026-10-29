package ru.cardly.core.barcode

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat as ZxingBarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import ru.cardly.core.model.BarcodeFormat

/**
 * Проверяет, можно ли показать [value] выбранным типом кода.
 * Нужна, чтобы не сохранять карту, у которой тип и номер не подходят друг другу:
 * например, буквы в EAN-13 или неверное число цифр.
 */
fun canEncodeBarcode(value: String, format: BarcodeFormat): Boolean {
    if (value.isBlank()) return false
    if (format == BarcodeFormat.UNKNOWN) return false
    return runCatching {
        MultiFormatWriter().encode(
            value,
            format.toZxing(),
            256,
            256,
            mapOf(EncodeHintType.MARGIN to 0),
        )
    }.isSuccess
}

object BarcodeBitmapRenderer {
    fun render(
        value: String,
        format: BarcodeFormat,
        width: Int = 1400,
        height: Int = if (format.isSquare()) 1400 else 620,
        margin: Int = 1,
    ): Bitmap {
        require(value.isNotBlank()) { "Barcode value must not be blank" }

        val matrix = MultiFormatWriter().encode(
            value,
            format.toZxing(),
            width,
            height,
            mapOf(
                EncodeHintType.MARGIN to margin,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )

        val pixels = IntArray(width * height)
        var offset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[offset++] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

fun BarcodeFormat.isSquare(): Boolean = when (this) {
    BarcodeFormat.QR_CODE,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.AZTEC,
    -> true
    else -> false
}

private fun BarcodeFormat.toZxing(): ZxingBarcodeFormat = when (this) {
    BarcodeFormat.CODE_128 -> ZxingBarcodeFormat.CODE_128
    BarcodeFormat.CODE_39 -> ZxingBarcodeFormat.CODE_39
    BarcodeFormat.CODE_93 -> ZxingBarcodeFormat.CODE_93
    BarcodeFormat.CODABAR -> ZxingBarcodeFormat.CODABAR
    BarcodeFormat.EAN_13 -> ZxingBarcodeFormat.EAN_13
    BarcodeFormat.EAN_8 -> ZxingBarcodeFormat.EAN_8
    BarcodeFormat.ITF -> ZxingBarcodeFormat.ITF
    BarcodeFormat.UPC_A -> ZxingBarcodeFormat.UPC_A
    BarcodeFormat.UPC_E -> ZxingBarcodeFormat.UPC_E
    BarcodeFormat.QR_CODE -> ZxingBarcodeFormat.QR_CODE
    BarcodeFormat.DATA_MATRIX -> ZxingBarcodeFormat.DATA_MATRIX
    BarcodeFormat.PDF_417 -> ZxingBarcodeFormat.PDF_417
    BarcodeFormat.AZTEC -> ZxingBarcodeFormat.AZTEC
    BarcodeFormat.UNKNOWN -> ZxingBarcodeFormat.CODE_128
}

/**
 * Угадывает тип кода по номеру, введённому вручную: EAN-13/EAN-8/UPC-A —
 * если номер из цифр нужной длины с верной контрольной цифрой, иначе Code 128.
 */
fun guessBarcodeFormat(value: String): BarcodeFormat {
    val v = value.trim()
    if (v.isEmpty() || !v.all(Char::isDigit)) return BarcodeFormat.CODE_128
    return when {
        v.length == 13 && eanChecksumOk(v) -> BarcodeFormat.EAN_13
        v.length == 8 && eanChecksumOk(v) -> BarcodeFormat.EAN_8
        v.length == 12 && eanChecksumOk(v) -> BarcodeFormat.UPC_A
        else -> BarcodeFormat.CODE_128
    }
}

private fun eanChecksumOk(digits: String): Boolean {
    val body = digits.dropLast(1).map { it - '0' }
    val sum = body.reversed().mapIndexed { i, d -> if (i % 2 == 0) d * 3 else d }.sum()
    return (10 - sum % 10) % 10 == digits.last() - '0'
}
