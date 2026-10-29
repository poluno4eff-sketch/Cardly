package ru.cardly.core.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.Card

object CardlyDataPaths {
    const val CARD_CATALOG = "/cardly/cards/catalog"
    /** Своя картинка для экрана оплаты на часах. */
    const val PAY_CARD = "/cardly/pay/card"
}

data class WearDisplaySettings(
    val amoled: Boolean = true,
    val maximumBrightness: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoCloseSeconds: Int = 15,
)

data class DecodedCatalog(
    val cards: List<SyncedCard>,
    val settings: WearDisplaySettings,
)

data class SyncedCard(
    val card: Card,
    val imageAsset: Asset?,
)

object CardCatalogCodec {
    private const val KEY_SCHEMA = "schema"
    private const val KEY_UPDATED_AT = "catalogUpdatedAt"
    private const val KEY_CARDS = "cards"
    private const val KEY_SETTINGS = "settings"
    private const val SCHEMA_VERSION = 3

    fun encode(cards: List<Card>, settings: WearDisplaySettings): DataMap = DataMap().apply {
        putInt(KEY_SCHEMA, SCHEMA_VERSION)
        putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        putDataMapArrayList(KEY_CARDS, ArrayList(cards.map(::encodeCard)))
        putDataMap(KEY_SETTINGS, DataMap().apply {
            putBoolean("amoled", settings.amoled)
            putBoolean("maximumBrightness", settings.maximumBrightness)
            putBoolean("keepScreenOn", settings.keepScreenOn)
            putInt("autoCloseSeconds", settings.autoCloseSeconds)
        })
    }

    fun decode(dataItem: DataItem, imageDirectory: File): DecodedCatalog {
        val root = DataMapItem.fromDataItem(dataItem).dataMap
        val schema = root.getInt(KEY_SCHEMA, 0)
        if (schema !in 1..SCHEMA_VERSION) return DecodedCatalog(emptyList(), WearDisplaySettings())
        imageDirectory.mkdirs()
        val cards = root.getDataMapArrayList(KEY_CARDS)?.mapNotNull(::decodeCard).orEmpty()
        val settingsMap = root.getDataMap(KEY_SETTINGS)
        val settings = if (settingsMap == null) WearDisplaySettings() else WearDisplaySettings(
            amoled = settingsMap.getBoolean("amoled", true),
            maximumBrightness = settingsMap.getBoolean("maximumBrightness", true),
            keepScreenOn = settingsMap.getBoolean("keepScreenOn", true),
            autoCloseSeconds = settingsMap.getInt("autoCloseSeconds", 15).coerceIn(15, 20),
        )
        return DecodedCatalog(cards, settings)
    }

    private fun encodeCard(card: Card): DataMap = DataMap().apply {
        putString("id", card.id)
        putString("name", card.name)
        putString("barcodeValue", card.barcodeValue)
        putString("barcodeFormat", card.barcodeFormat.name)
        putString("cardNumber", card.cardNumber)
        putString("note", card.note)
        putBoolean("showName", card.showName)
        card.groupId?.let { putString("groupId", it) }
        putBoolean("favorite", card.favorite)
        putBoolean("archived", card.archived)
        putLong("createdAt", card.createdAt)
        putLong("updatedAt", card.updatedAt)
        card.lastUsedAt?.let { putLong("lastUsedAt", it) }
        putLong("useCount", card.useCount)
        card.validFrom?.let { putLong("validFrom", it) }
        card.validUntil?.let { putLong("validUntil", it) }
        putLong("sortOrder", card.sortOrder)
        putLong("revision", card.revision)
        encodeWearThumbnail(card.frontImagePath)?.let { putAsset("frontImageAsset", Asset.createFromBytes(it)) }
    }

    private fun decodeCard(map: DataMap): SyncedCard? {
        val id = map.getString("id") ?: return null
        val name = map.getString("name") ?: return null
        val barcodeValue = map.getString("barcodeValue") ?: return null
        val format = runCatching {
            BarcodeFormat.valueOf(map.getString("barcodeFormat") ?: BarcodeFormat.UNKNOWN.name)
        }.getOrDefault(BarcodeFormat.UNKNOWN)
        val card = Card(
            id = id, name = name, barcodeValue = barcodeValue, barcodeFormat = format,
            cardNumber = map.getString("cardNumber").orEmpty(), note = map.getString("note").orEmpty(),
            frontImagePath = null, showName = map.getBoolean("showName", true), groupId = map.optionalString("groupId"),
            favorite = map.getBoolean("favorite", false), archived = map.getBoolean("archived", false),
            createdAt = map.getLong("createdAt", 0L), updatedAt = map.getLong("updatedAt", 0L),
            lastUsedAt = map.optionalLong("lastUsedAt"), useCount = map.getLong("useCount", 0L),
            validFrom = map.optionalLong("validFrom"), validUntil = map.optionalLong("validUntil"),
            sortOrder = map.getLong("sortOrder", 0L), revision = map.getLong("revision", 1L),
        )
        return SyncedCard(card, map.getAsset("frontImageAsset"))
    }

    private fun encodeWearThumbnail(path: String?): ByteArray? = runCatching {
        if (path.isNullOrBlank()) return null
        val source = BitmapFactory.decodeFile(path) ?: return null
        val width = 320
        val height = (source.height * (width.toFloat() / source.width)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, output)
            output.toByteArray().takeIf { it.size <= 90_000 }
                ?: ByteArrayOutputStream().use { smaller ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 50, smaller)
                    smaller.toByteArray()
                }
        }
    }.getOrNull()

    private fun DataMap.optionalString(key: String): String? = if (containsKey(key)) getString(key) else null
    private fun DataMap.optionalLong(key: String): Long? = if (containsKey(key)) getLong(key) else null
}

class WearCardCatalogPublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    /**
     * Собирает каталог и отправляет его на часы. Тяжёлая работа (чтение и сжатие
     * картинок карт) — вызывать только из фонового потока.
     *
     * @return true, если Data Layer принял каталог. Если часов рядом нет
     *         или нет приложения-компаньона Wear OS, вернёт false.
     */
    fun publish(cards: List<Card>, settings: WearDisplaySettings = WearDisplaySettings()): Boolean =
        runCatching {
            val request = PutDataMapRequest.create(CardlyDataPaths.CARD_CATALOG).apply {
                dataMap.putAll(CardCatalogCodec.encode(cards, settings))
            }.asPutDataRequest().setUrgent()
            Tasks.await(dataClient.putDataItem(request), 10, TimeUnit.SECONDS)
            true
        }.getOrDefault(false)
}

/** Отправляет на часы свою картинку карты оплаты (или убирает её, если [jpeg] = null). */
class WearPayCardPublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun publish(jpeg: ByteArray?): Boolean = runCatching {
        val request = PutDataMapRequest.create(CardlyDataPaths.PAY_CARD).apply {
            dataMap.putLong("updatedAt", System.currentTimeMillis())
            if (jpeg != null) dataMap.putAsset("image", Asset.createFromBytes(jpeg))
        }.asPutDataRequest().setUrgent()
        Tasks.await(dataClient.putDataItem(request), 10, TimeUnit.SECONDS)
        true
    }.getOrDefault(false)
}
