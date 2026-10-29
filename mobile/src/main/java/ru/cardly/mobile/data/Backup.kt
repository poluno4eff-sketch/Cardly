package ru.cardly.mobile.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card

/** Резервная копия Cardly: один JSON-файл с картами, визитками и их картинками. */
object Backup {
    data class Content(val cards: List<Card>, val contacts: List<BusinessCard>)

    private fun encodeImage(path: String?): String? = runCatching {
        val file = path?.let(::File) ?: return null
        if (!file.isFile || file.length() == 0L) return null
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    private fun decodeImage(context: Context, folder: String, name: String, data: String?): String? = runCatching {
        if (data.isNullOrBlank()) return null
        val directory = File(context.filesDir, folder).apply { mkdirs() }
        val target = File(directory, "$name-${System.currentTimeMillis()}.jpg")
        target.writeBytes(Base64.decode(data, Base64.DEFAULT))
        target.absolutePath
    }.getOrNull()

    fun export(context: Context, uri: Uri, content: Content): Boolean = runCatching {
        val cards = JSONArray()
        content.cards.forEach { card ->
            cards.put(JSONObject().apply {
                put("id", card.id); put("name", card.name); put("barcodeValue", card.barcodeValue)
                put("barcodeFormat", card.barcodeFormat.name); put("cardNumber", card.cardNumber); put("note", card.note)
                put("showName", card.showName); put("groupId", card.groupId); put("favorite", card.favorite)
                put("archived", card.archived); put("createdAt", card.createdAt); put("updatedAt", card.updatedAt)
                put("lastUsedAt", card.lastUsedAt); put("useCount", card.useCount)
                put("validFrom", card.validFrom); put("validUntil", card.validUntil)
                put("sortOrder", card.sortOrder); put("revision", card.revision)
                encodeImage(card.frontImagePath)?.let { put("frontImage", it) }
                encodeImage(card.backImagePath)?.let { put("backImage", it) }
            })
        }
        val contacts = JSONArray()
        content.contacts.forEach { c ->
            contacts.put(JSONObject().apply {
                put("id", c.id); put("fullName", c.fullName); put("company", c.company); put("position", c.position)
                put("phones", JSONArray(c.phones)); put("emails", JSONArray(c.emails)); put("websites", JSONArray(c.websites))
                put("address", c.address); put("note", c.note); put("rawText", c.rawText); put("favorite", c.favorite)
                put("createdAt", c.createdAt); put("updatedAt", c.updatedAt); put("lastUsedAt", c.lastUsedAt)
                encodeImage(c.frontImagePath)?.let { put("frontImage", it) }
                encodeImage(c.backImagePath)?.let { put("backImage", it) }
            })
        }
        val root = JSONObject().put("format", "cardly-backup").put("version", 3)
            .put("cards", cards).put("businessCards", contacts)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString()) } ?: error("no output")
    }.isSuccess

    fun import(context: Context, uri: Uri): Result<Content> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("no input")
        val root = JSONObject(text)
        require(root.optString("format") == "cardly-backup") { "not a Cardly backup" }
        val now = System.currentTimeMillis()
        fun JSONObject.optLongOrNull(key: String) = if (has(key) && !isNull(key)) optLong(key) else null
        fun JSONArray?.strings() = buildList { if (this@strings != null) for (i in 0 until this@strings.length()) add(this@strings.optString(i)) }

        val cards = root.optJSONArray("cards")?.let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                Card(
                    id = id, name = item.optString("name"), barcodeValue = item.optString("barcodeValue"),
                    barcodeFormat = runCatching { BarcodeFormat.valueOf(item.optString("barcodeFormat")) }.getOrDefault(BarcodeFormat.CODE_128),
                    cardNumber = item.optString("cardNumber", item.optString("barcodeValue")), note = item.optString("note"),
                    frontImagePath = decodeImage(context, "card_images", id, item.optString("frontImage").takeIf(String::isNotBlank)),
                    backImagePath = decodeImage(context, "card_images", "$id-back", item.optString("backImage").takeIf(String::isNotBlank)),
                    showName = item.optBoolean("showName", true),
                    groupId = item.optString("groupId").takeIf { it.isNotBlank() && it != "null" },
                    favorite = item.optBoolean("favorite"), archived = item.optBoolean("archived"),
                    createdAt = item.optLong("createdAt", now), updatedAt = item.optLong("updatedAt", now),
                    lastUsedAt = item.optLongOrNull("lastUsedAt"), useCount = item.optLong("useCount", 0L),
                    validFrom = item.optLongOrNull("validFrom"), validUntil = item.optLongOrNull("validUntil"),
                    sortOrder = item.optLong("sortOrder", now), revision = item.optLong("revision", 1L),
                )
            }
        }.orEmpty()

        val contacts = root.optJSONArray("businessCards")?.let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                BusinessCard(
                    id = id, fullName = item.optString("fullName"), company = item.optString("company"),
                    position = item.optString("position"), phones = item.optJSONArray("phones").strings(),
                    emails = item.optJSONArray("emails").strings(), websites = item.optJSONArray("websites").strings(),
                    address = item.optString("address"), note = item.optString("note"), rawText = item.optString("rawText"),
                    frontImagePath = decodeImage(context, "contact_images", "$id-front", item.optString("frontImage").takeIf(String::isNotBlank)),
                    backImagePath = decodeImage(context, "contact_images", "$id-back", item.optString("backImage").takeIf(String::isNotBlank)),
                    favorite = item.optBoolean("favorite"), createdAt = item.optLong("createdAt", now),
                    updatedAt = item.optLong("updatedAt", now), lastUsedAt = item.optLongOrNull("lastUsedAt"),
                )
            }
        }.orEmpty()
        Content(cards, contacts)
    }
}
