package ru.cardly.mobile.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.cardly.core.barcode.BarcodeBitmapDecoder
import ru.cardly.core.model.BarcodeFormat

/** Результат сканирования кода. */
data class ScanValue(val value: String, val format: BarcodeFormat)

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Ждёт Task из Google Play Services; null — при ошибке или отмене. */
suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
}

fun Int.mlkitToCardly(): BarcodeFormat = when (this) {
    Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_ITF -> BarcodeFormat.ITF
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    else -> BarcodeFormat.CODE_128
}

/**
 * Сканер штрихкода камерой. Основной — сканер Google (быстрый, с автозумом,
 * не требует разрешения на камеру). Если сервисов Google нет — встроенный ZXing.
 */
@Composable
fun rememberBarcodeScanner(onResult: (ScanValue) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val fallback = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf(String::isNotBlank)?.let { value ->
            val format = runCatching { BarcodeFormat.valueOf(result.formatName.orEmpty()) }.getOrDefault(BarcodeFormat.CODE_128)
            callback.value(ScanValue(value, format))
        }
    }
    return remember(context) {
        {
            val activity = context.findActivity()
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build()
            runCatching {
                GmsBarcodeScanning.getClient(activity ?: context, options).startScan()
                    .addOnSuccessListener { barcode ->
                        val value = barcode.rawValue ?: barcode.displayValue
                        if (!value.isNullOrBlank()) callback.value(ScanValue(value, barcode.format.mlkitToCardly()))
                    }
                    .addOnFailureListener { fallback.launch(zxingOptions()) }
            }.onFailure { fallback.launch(zxingOptions()) }
        }
    }
}

private fun zxingOptions() = ScanOptions().setPrompt("Наведите камеру на код")
    .setBeepEnabled(false).setOrientationLocked(true)
    .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)

/** Ищет код на картинке: сначала ML Kit, затем ZXing. */
suspend fun decodeBarcode(context: Context, bitmap: Bitmap): ScanValue? {
    val mlkit = runCatching {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build(),
        )
        val result = scanner.process(InputImage.fromBitmap(bitmap, 0)).awaitOrNull()
        scanner.close()
        result?.firstOrNull { !it.rawValue.isNullOrBlank() }
    }.getOrNull()
    if (mlkit != null) return ScanValue(mlkit.rawValue!!, mlkit.format.mlkitToCardly())
    return BarcodeBitmapDecoder.decode(bitmap)?.let { ScanValue(it.value, it.format) }
}

/** Выбор картинки из галереи (системный выбор фото, без разрешений). */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback.value(uri)
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/**
 * Съёмка документа с автоматической обрезкой по краям (сканер документов Google).
 * Подходит и для пластиковых карт, и для визиток. [pages] — сколько сторон можно снять.
 * Если сканер недоступен, открывается выбор фото из галереи.
 */
@Composable
fun rememberDocumentScanner(pages: Int, onScanned: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onScanned)
    val picker = rememberImagePicker { callback.value(listOf(it)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) callback.value(uris)
        }
    }
    return remember(context, pages) {
        {
            val activity = context.findActivity()
            if (activity == null) picker() else runCatching {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(pages)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener {
                        Toast.makeText(context, "Сканер недоступен — выберите фото", Toast.LENGTH_SHORT).show()
                        picker()
                    }
            }.onFailure { picker() }
        }
    }
}
