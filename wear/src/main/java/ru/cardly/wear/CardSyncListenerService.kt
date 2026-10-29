package ru.cardly.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.util.concurrent.TimeUnit
import ru.cardly.core.sync.CardCatalogCodec
import ru.cardly.core.sync.CardlyDataPaths

/** Получает каталог карт с телефона, когда он меняется. */
class CardSyncListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.lastOrNull { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == CardlyDataPaths.PAY_CARD }
            ?.dataItem?.freeze()?.let { PayCardImporter.import(applicationContext, it) }
        // Берём только последнее изменение каталога — промежуточные не нужны.
        val latest = dataEvents.lastOrNull {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == CardlyDataPaths.CARD_CATALOG
        }?.dataItem?.freeze() ?: return
        CatalogImporter.import(applicationContext, latest)
    }
}

/** Разбор каталога и сохранение карт и миниатюр на часах. Вызывать из фонового потока. */
object CatalogImporter {
    private val lock = Any()

    fun import(context: Context, item: DataItem) = synchronized(lock) {
        val imageDirectory = File(context.filesDir, "card_images").apply { mkdirs() }
        val catalog = CardCatalogCodec.decode(item, imageDirectory)
        context.getSharedPreferences("wear_display", Context.MODE_PRIVATE).edit()
            .putBoolean("amoled", catalog.settings.amoled)
            .putBoolean("maximumBrightness", catalog.settings.maximumBrightness)
            .putBoolean("keepScreenOn", catalog.settings.keepScreenOn)
            .putInt("autoCloseSeconds", catalog.settings.autoCloseSeconds)
            .apply()
        val client = Wearable.getDataClient(context)
        val cards = catalog.cards.map { synced ->
            val file = File(imageDirectory, "${synced.card.id}.jpg")
            val imagePath = synced.imageAsset?.let { asset ->
                runCatching {
                    val response = Tasks.await(client.getFdForAsset(asset), 15, TimeUnit.SECONDS)
                    response.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    file.absolutePath
                }.getOrNull()
            }
            if (imagePath == null) runCatching { file.delete() }
            synced.card.copy(frontImagePath = imagePath)
        }
        val activeIds = cards.mapTo(mutableSetOf()) { it.id }
        imageDirectory.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in activeIds) runCatching { file.delete() }
        }
        WearCardStore.save(context, cards)
    }

    /**
     * Первая установка на часы: каталог уже лежит в Data Layer, но события об изменении
     * не будет — забираем его сами.
     */
    fun fetchExisting(context: Context): Boolean = runCatching {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(CardlyDataPaths.CARD_CATALOG).build()
        val buffer = Tasks.await(Wearable.getDataClient(context).getDataItems(uri), 15, TimeUnit.SECONDS)
        try {
            val item = buffer.firstOrNull()?.freeze() ?: return@runCatching false
            import(context, item)
            true
        } finally {
            buffer.release()
        }
    }.getOrDefault(false)
}

/** Своя картинка карты оплаты с телефона: сохраняем и сразу делаем её выбранной. */
object PayCardImporter {
    fun import(context: Context, item: DataItem) {
        val map = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
        val file = CustomPayCard.file(context)
        val asset = map.getAsset("image")
        val settings = PaySettings(context)
        if (asset == null) {
            runCatching { file.delete() }
            if (settings.card == PayCard.CUSTOM) settings.card = PayCard.SECRET_GARDEN
        } else {
            runCatching {
                val response = Tasks.await(Wearable.getDataClient(context).getFdForAsset(asset), 15, TimeUnit.SECONDS)
                val tmp = File(context.filesDir, "pay_card_custom.tmp")
                response.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
                settings.card = PayCard.CUSTOM
            }
        }
        context.sendBroadcast(android.content.Intent(CustomPayCard.ACTION_UPDATED).setPackage(context.packageName))
    }
}
