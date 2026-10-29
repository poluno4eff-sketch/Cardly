package ru.cardly.wear

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.Card

object WearCardStore {
    const val ACTION_CARDS_UPDATED = "ru.cardly.wear.CARDS_UPDATED"
    private const val PREFS = "wear_cards"
    private const val KEY_CARDS = "cards"

    fun save(context: Context, cards: List<Card>) {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(JSONObject().apply {
                put("id", card.id)
                put("name", card.name)
                put("barcodeValue", card.barcodeValue)
                put("barcodeFormat", card.barcodeFormat.name)
                put("cardNumber", card.cardNumber)
                put("note", card.note)
                put("frontImagePath", card.frontImagePath ?: JSONObject.NULL)
                put("showName", card.showName)
                put("favorite", card.favorite)
                put("createdAt", card.createdAt)
                put("updatedAt", card.updatedAt)
                put("sortOrder", card.sortOrder)
                put("revision", card.revision)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CARDS, array.toString()).apply()
        context.sendBroadcast(Intent(ACTION_CARDS_UPDATED).setPackage(context.packageName))
    }

    fun load(context: Context): List<Card> = runCatching {
        val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CARDS, "[]").orEmpty()
        val array = JSONArray(source)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val createdAt = item.optLong("createdAt", System.currentTimeMillis())
                add(Card(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    barcodeValue = item.getString("barcodeValue"),
                    barcodeFormat = runCatching {
                        BarcodeFormat.valueOf(item.optString("barcodeFormat"))
                    }.getOrDefault(BarcodeFormat.CODE_128),
                    cardNumber = item.optString("cardNumber"),
                    note = item.optString("note"),
                    frontImagePath = item.optString("frontImagePath").takeIf { it.isNotBlank() && it != "null" },
                    showName = item.optBoolean("showName", true),
                    favorite = item.optBoolean("favorite"),
                    createdAt = createdAt,
                    updatedAt = item.optLong("updatedAt", createdAt),
                    sortOrder = item.optLong("sortOrder", createdAt),
                    revision = item.optLong("revision", 1L),
                ))
            }
        }
    }.getOrDefault(emptyList())
}
