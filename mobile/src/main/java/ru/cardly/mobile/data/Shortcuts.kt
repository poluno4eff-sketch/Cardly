package ru.cardly.mobile.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ru.cardly.core.model.Card
import ru.cardly.mobile.MainActivity
import ru.cardly.mobile.R

/**
 * Ярлыки на значке приложения: долгое нажатие на иконку Cardly → сразу нужная карта.
 * Показываем 4 самые используемые (и избранные) карты.
 */
object CardShortcuts {
    const val EXTRA_CARD_ID = "ru.cardly.extra.CARD_ID"
    private var lastSignature: String? = null

    fun update(context: Context, cards: List<Card>) {
        val top = cards.sortedWith(
            compareByDescending<Card> { it.favorite }.thenByDescending { it.useCount }.thenByDescending { it.lastUsedAt ?: 0L },
        ).take(4)
        val signature = top.joinToString("|") { "${it.id}:${it.name}:${it.frontImagePath}" }
        if (signature == lastSignature) return
        lastSignature = signature
        runCatching {
            val shortcuts = top.mapIndexed { index, card ->
                ShortcutInfoCompat.Builder(context, "card-${card.id}")
                    .setShortLabel(card.name.take(24).ifBlank { "Карта" })
                    .setLongLabel(card.name.ifBlank { "Карта" })
                    .setIcon(iconFor(context, card))
                    .setRank(index)
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(EXTRA_CARD_ID, card.id),
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    /** Иконка ярлыка: фото карты в адаптивной форме, иначе значок приложения. */
    private fun iconFor(context: Context, card: Card): IconCompat {
        val photo = CardImages.load(card.frontImagePath, 324) ?: return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val size = 216 // 108dp × 2 — размер адаптивной иконки
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val visible = size * 72f / 108f
        val height = visible / (85.6f / 53.98f)
        val left = (size - visible) / 2f
        val top = (size - height) / 2f
        canvas.drawColor(0xFF101218.toInt())
        canvas.drawBitmap(photo, null, RectF(left, top, left + visible, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
        return IconCompat.createWithAdaptiveBitmap(output)
    }
}
