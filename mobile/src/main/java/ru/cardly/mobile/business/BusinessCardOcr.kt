package ru.cardly.mobile.business

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.mobile.scan.decodeBarcode

data class RecognizedCard(val contact: ParsedContact, val rawText: String)

/**
 * Распознавание визитки полностью на телефоне, без Интернета.
 * 1) Если на визитке есть QR с контактом (vCard/MeCard) — берём данные из него.
 * 2) Текст распознаём Tesseract (русский + английский) и раскладываем по полям.
 */
object BusinessCardOcr {
    private const val LANGUAGES = "rus+eng"
    private val LOCK = Any()

    suspend fun recognize(context: Context, imagePaths: List<String>): RecognizedCard = withContext(Dispatchers.Default) {
        val dataPath = prepareTessData(context)
        var qrContact: ParsedContact? = null
        val lines = mutableListOf<OcrLine>()
        val text = StringBuilder()
        for (path in imagePaths) {
            val bitmap = loadForOcr(path) ?: continue
            if (qrContact == null) {
                decodeBarcode(context, bitmap)?.takeIf { it.format == BarcodeFormat.QR_CODE }?.let { qr ->
                    qrContact = VCardParser.parse(qr.value)
                }
            }
            if (dataPath != null) {
                val result = synchronized(LOCK) { runTesseract(dataPath, bitmap) }
                lines += result.first
                if (result.second.isNotBlank()) text.append(result.second.trim()).append("\n\n")
            }
            bitmap.recycle()
        }
        val ocrContact = BusinessCardParser.parse(lines)
        val contact = qrContact?.mergedWith(ocrContact) ?: ocrContact
        RecognizedCard(contact, text.toString().trim())
    }

    private fun runTesseract(dataPath: String, bitmap: Bitmap): Pair<List<OcrLine>, String> {
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(dataPath, LANGUAGES)) return emptyList<OcrLine>() to ""
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setImage(bitmap)
            val fullText = tess.getUTF8Text().orEmpty()
            val lines = mutableListOf<OcrLine>()
            val iterator = tess.resultIterator
            if (iterator != null) {
                iterator.begin()
                do {
                    val lineText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    if (!lineText.isNullOrBlank()) lines += OcrLine(lineText.trim(), rect?.height() ?: 0)
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                iterator.delete()
            }
            if (lines.isEmpty()) lines += fullText.lines().map { OcrLine(it) }
            lines to fullText
        } catch (_: Throwable) {
            emptyList<OcrLine>() to ""
        } finally {
            tess.recycle()
        }
    }

    /** Визитка ~90 мм: ширина около 2000 px даёт Tesseract нужные ~300 dpi. */
    private fun loadForOcr(path: String): Bitmap? {
        val source = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val longSide = maxOf(source.width, source.height)
        val target = 2000f
        val scale = target / longSide
        if (scale in 0.85f..1.15f) return source
        val scaled = Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true,
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    /** Копирует языковые модели из APK в папку приложения (один раз). */
    private fun prepareTessData(context: Context): String? = runCatching {
        val root = File(context.filesDir, "tesseract")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        for (lang in listOf("rus", "eng")) {
            val target = File(tessdata, "$lang.traineddata")
            if (target.isFile && target.length() > 0) continue
            val tmp = File(tessdata, "$lang.tmp")
            context.assets.open("tessdata/$lang.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        }
        root.absolutePath
    }.getOrNull()
}
