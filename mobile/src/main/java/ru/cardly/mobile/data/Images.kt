package ru.cardly.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Работа с картинками карт и визиток: уменьшенная загрузка с кешем,
 * чтобы список прокручивался плавно и не расходовал память.
 */
object CardImages {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(path: String, width: Int) =
        "$path:${runCatching { File(path).lastModified() }.getOrDefault(0L)}:$width"

    fun cached(path: String?, width: Int): Bitmap? = path?.let { cache.get(key(it, width)) }

    /** Загружает картинку не шире [width] пикселей (с запасом ×2 по степени двойки). */
    fun load(path: String?, width: Int = 1080): Bitmap? {
        if (path.isNullOrBlank()) return null
        val k = key(path, width)
        cache.get(k)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bitmap = runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull() ?: return null
        cache.put(k, bitmap)
        return bitmap
    }

    fun decodeUri(context: Context, uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > 2600) {
                    val scale = 2600f / longSide
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        }.getOrNull()
    } else {
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    /** Сохраняет выбранную картинку во временную папку (JPEG, длинная сторона ≤ [maxSide]). */
    fun stage(context: Context, uri: Uri, maxSide: Int = 1600): String? = runCatching {
        val decoded = decodeUri(context, uri) ?: error("cannot decode")
        stageBitmap(context, decoded, maxSide)
    }.getOrNull()

    fun stageBitmap(context: Context, source: Bitmap, maxSide: Int = 1600): String? = runCatching {
        val directory = File(context.cacheDir, "image_staging").apply { mkdirs() }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1))
        val output = if (scale < 1f) Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true,
        ) else source
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { check(output.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        file.absolutePath
    }.getOrNull()

    /** Переносит временную картинку в постоянную папку под именем [name].jpg. */
    fun commit(context: Context, stagedPath: String, folder: String, name: String): String? = runCatching {
        val staged = File(stagedPath)
        check(staged.isFile && staged.length() > 0L)
        val directory = File(context.filesDir, folder).apply { mkdirs() }
        // Новое имя при каждом сохранении — чтобы кеш и часы гарантированно увидели новую картинку.
        val target = File(directory, "$name-${System.currentTimeMillis()}.jpg")
        staged.copyTo(target, overwrite = true)
        staged.delete()
        target.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
    }
}

/** Картинка для показа в интерфейсе: грузится в фоне, из кеша — сразу. */
@Composable
fun rememberImage(path: String?, width: Int = 1080): State<Bitmap?> =
    produceState(initialValue = CardImages.cached(path, width), path, width) {
        if (path.isNullOrBlank()) { value = null; return@produceState }
        if (value == null) value = withContext(Dispatchers.IO) { CardImages.load(path, width) }
    }
