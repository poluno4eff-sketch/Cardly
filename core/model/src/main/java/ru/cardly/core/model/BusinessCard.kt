package ru.cardly.core.model

/**
 * Визитка. Хранится только на телефоне, на часы не отправляется.
 * Списки телефонов, почты и ссылок могут быть пустыми.
 */
data class BusinessCard(
    val id: String,
    val fullName: String = "",
    val company: String = "",
    val position: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val address: String = "",
    val note: String = "",
    /** Весь распознанный текст — нужен для поиска и чтобы ничего не потерять. */
    val rawText: String = "",
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
) {
    /** Что показывать заголовком: ФИО, иначе компания, иначе первый телефон. */
    val title: String
        get() = fullName.ifBlank { company }.ifBlank { phones.firstOrNull().orEmpty() }.ifBlank { "Визитка" }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        val digits = q.filter(Char::isDigit)
        return listOf(fullName, company, position, address, note, rawText).any { it.contains(q, true) } ||
            emails.any { it.contains(q, true) } || websites.any { it.contains(q, true) } ||
            (digits.length >= 3 && phones.any { it.filter(Char::isDigit).contains(digits) })
    }
}
