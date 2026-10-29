package ru.cardly.mobile.business

/**
 * Разбор текста визитки на поля. Чистый Kotlin без Android, чтобы проверять тестами.
 *
 * Принцип: сначала вытаскиваем то, что надёжно ловится шаблоном (почта, сайты,
 * телефоны), затем по ключевым словам — адрес, компанию и должность, а ФИО
 * выбираем из оставшихся строк по форме (2–3 слова с заглавной) и размеру шрифта.
 * Результат всегда показывается пользователю для проверки.
 */

/** Строка распознанного текста. [height] — высота строки в пикселях, 0 если неизвестна. */
data class OcrLine(val text: String, val height: Int = 0)

data class ParsedContact(
    val fullName: String = "",
    val company: String = "",
    val position: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val address: String = "",
    val note: String = "",
) {
    val isEmpty: Boolean
        get() = fullName.isBlank() && company.isBlank() && phones.isEmpty() && emails.isEmpty() && websites.isEmpty()

    /** Дополняет пустые поля этого результата значениями из [other]. */
    fun mergedWith(other: ParsedContact): ParsedContact = ParsedContact(
        fullName = fullName.ifBlank { other.fullName },
        company = company.ifBlank { other.company },
        position = position.ifBlank { other.position },
        phones = BusinessCardParser.distinctPhones(phones + other.phones),
        emails = (emails + other.emails).distinctBy(String::lowercase),
        websites = (websites + other.websites).distinctBy(String::lowercase),
        address = address.ifBlank { other.address },
        note = note.ifBlank { other.note },
    )
}

object BusinessCardParser {

    fun parse(text: String): ParsedContact = parse(text.lines().map { OcrLine(it) })

    fun parse(input: List<OcrLine>): ParsedContact {
        val lines = input.mapNotNull { line ->
            val cleaned = line.text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
                .trim('|', '_', '~', '`', '•', '·', '—', '-', '*').trim()
            if (cleaned.length < 2 || !looksLikeText(cleaned)) null else Line(cleaned, line.height)
        }.toMutableList()

        val emails = mutableListOf<String>()
        val websites = mutableListOf<String>()
        val phones = mutableListOf<String>()
        // Подпись мессенджера на отдельной строке («WhatsApp») относится к номеру в следующей строке.
        var pendingTags = emptyList<String>()

        for (line in lines) {
            var rest = line.text
            val lineTags = MESSENGERS.filter { (regex, _) -> regex.containsMatchIn(line.text) }.map { it.second }

            // Почта: убираем пробелы вокруг @ и точек, кириллические «двойники» меняем на латиницу.
            val emailSource = latinizeLookalikes(rest.replace(Regex("\\s*@\\s*"), "@").replace(Regex("(?<=\\w)\\s*\\.\\s*(?=\\w)"), "."))
            EMAIL.findAll(emailSource).forEach { match ->
                emails += match.value.trimEnd('.').lowercase()
            }
            if (emails.isNotEmpty() && EMAIL.containsMatchIn(emailSource)) {
                rest = EMAIL.replace(emailSource, " ")
            }

            // Telegram-ник «@name» без домена.
            TELEGRAM_HANDLE.findAll(rest).forEach { match ->
                websites += "t.me/" + match.groupValues[1]
            }
            rest = TELEGRAM_HANDLE.replace(rest, " ")

            // Сайты и соцсети.
            val urlSource = latinizeLookalikes(rest)
            URL.findAll(urlSource).forEach { match ->
                val url = match.value.trimEnd('.', ',', ';', ')').removePrefix("http://").removePrefix("https://")
                if (url.count { it == '.' } >= 1 && !url.startsWith("@")) websites += url.lowercase()
            }
            if (URL.containsMatchIn(urlSource)) rest = URL.replace(urlSource, " ")

            // Телефоны. Строки с реквизитами (ИНН, ОГРН, счёт) пропускаем — там тоже длинные числа.
            val fax = FAX.containsMatchIn(rest)
            val found = if (REQUISITES.containsMatchIn(rest)) emptyList()
            else PHONE_CANDIDATE.findAll(fixDigitLookalikes(rest)).mapNotNull { normalizePhone(it.value) }.toList()
            val tags = (if (fax) listOf("факс") else emptyList()) + lineTags.ifEmpty { pendingTags }
            found.forEach { phones += if (tags.isEmpty()) it else "$it (${tags.joinToString(", ")})" }
            rest = PHONE_CANDIDATE.replace(fixDigitLookalikes(rest), " ")
            val labelOnly = rest.replace(LABELS, " ").replace(MESSENGER_WORDS, " ").none(Char::isLetterOrDigit)
            pendingTags = when {
                found.isNotEmpty() -> emptyList()
                lineTags.isNotEmpty() && labelOnly -> lineTags
                else -> emptyList()
            }

            line.rest = rest.replace(LABELS, " ").replace(Regex("\\s+"), " ").trim(' ', ',', ';', ':', '|', '/', '-', '.')
        }

        val remaining = lines.filter { it.rest.length >= 2 && it.rest.any(Char::isLetter) }

        val addressLines = remaining.filter { ADDRESS.containsMatchIn(it.rest) || POSTCODE.containsMatchIn(it.rest) }
        val address = addressLines.joinToString(", ") { it.rest.trim(',', ' ') }
        addressLines.forEach { it.used = true }

        val companyLine = remaining.firstOrNull { !it.used && COMPANY.containsMatchIn(it.rest) }
        companyLine?.used = true

        val positionLine = remaining.firstOrNull { !it.used && POSITION.containsMatchIn(it.rest) && it.rest.split(' ').size <= 8 }
        positionLine?.used = true

        val nameLine = remaining.filter { !it.used && isNameLike(it.rest) }
            .maxByOrNull { nameScore(it, remaining) }
        nameLine?.used = true

        // Если компания не нашлась по ключевым словам — берём самую крупную оставшуюся строку.
        val fallbackCompany = if (companyLine == null) {
            remaining.filter { !it.used && it.rest.length in 2..60 }.maxByOrNull { it.height }
                ?.takeIf { it.height > 0 || remaining.count { r -> !r.used } == 1 }
        } else null
        fallbackCompany?.used = true

        return ParsedContact(
            fullName = nameLine?.rest?.let(::normalizeName).orEmpty(),
            company = (companyLine ?: fallbackCompany)?.rest.orEmpty(),
            position = positionLine?.rest?.let(::capitalizeFirst).orEmpty(),
            phones = distinctPhones(phones),
            emails = emails.distinct(),
            websites = websites.distinct().filterNot { site -> emails.any { it.substringAfter('@') == site } },
            address = address,
        )
    }

    /** Убирает повторы номера; если один из повторов подписан (WhatsApp, факс…), оставляем подписанный. */
    fun distinctPhones(list: List<String>): List<String> {
        val order = mutableListOf<String>()
        val best = mutableMapOf<String, String>()
        for (phone in list) {
            val key = phoneDigits(phone)
            if (key !in best) { order += key; best[key] = phone }
            else if (phoneTag(best.getValue(key)) == null && phoneTag(phone) != null) best[key] = phone
        }
        return order.map(best::getValue)
    }

    /** Подпись номера: «+7 (949) 123-45-67 (WhatsApp)» → «WhatsApp». */
    fun phoneTag(value: String): String? = TAG.find(value)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)

    /** Номер без подписи. */
    fun phoneWithoutTag(value: String): String = value.replace(TAG, "").trim()

    private fun phoneDigits(value: String) = phoneWithoutTag(value).filter(Char::isDigit)

    /** Телефон в виде для показа: +7 (949) 123-45-67. null — если это не телефон. */
    fun normalizePhone(raw: String): String? {
        val hasPlus = raw.trim().startsWith("+")
        var digits = raw.filter(Char::isDigit)
        if (digits.length < 10 || digits.length > 13) return null
        if (digits.length == 11 && (digits[0] == '8' || digits[0] == '7')) digits = "7" + digits.drop(1)
        else if (digits.length == 10 && !hasPlus && digits[0] != '0') digits = "7$digits"
        else if (digits.length == 10 && digits[0] == '0') digits = "38$digits" // украинский формат 0XX…
        return formatPhone("+$digits")
    }

    fun formatPhone(e164: String): String {
        val d = e164.filter(Char::isDigit)
        return when {
            d.length == 11 && d[0] == '7' ->
                "+7 (${d.substring(1, 4)}) ${d.substring(4, 7)}-${d.substring(7, 9)}-${d.substring(9, 11)}"
            d.length == 12 && d.startsWith("380") ->
                "+380 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8, 10)} ${d.substring(10, 12)}"
            else -> "+$d"
        }
    }

    // ---------- внутреннее ----------

    private class Line(val text: String, val height: Int) {
        var rest: String = text
        var used: Boolean = false
    }

    private fun looksLikeText(value: String): Boolean {
        val good = value.count { it.isLetterOrDigit() || it == ' ' || it in "@.+-()/,:;«»\"'№&" }
        return good >= value.length * 0.7
    }

    private fun isNameLike(value: String): Boolean {
        if (value.any(Char::isDigit)) return false
        if (COMPANY.containsMatchIn(value) || POSITION.containsMatchIn(value) || ADDRESS.containsMatchIn(value)) return false
        val words = value.split(' ').filter(String::isNotBlank)
        if (words.size !in 2..4) return false
        return words.all { word ->
            val letters = word.trim('.', ',')
            letters.isNotEmpty() && letters[0].isUpperCase() &&
                letters.all { it.isLetter() || it == '-' || it == '.' || it == '\'' }
        }
    }

    private fun nameScore(line: Line, all: List<Line>): Double {
        val words = line.rest.split(' ').size
        val cyrillic = line.rest.count { it in 'А'..'я' || it == 'Ё' || it == 'ё' }.toDouble() / line.rest.length
        val maxHeight = all.maxOfOrNull { it.height }?.takeIf { it > 0 } ?: 1
        val heightScore = line.height.toDouble() / maxHeight
        val positionScore = 1.0 - all.indexOf(line).toDouble() / all.size.coerceAtLeast(1)
        val wordScore = when (words) { 3 -> 1.0; 2 -> 0.85; else -> 0.5 }
        return heightScore * 2 + wordScore + cyrillic * 0.5 + positionScore * 0.3
    }

    /** «ИВАНОВ ИВАН ИВАНОВИЧ» → «Иванов Иван Иванович». Инициалы и смешанный регистр не трогаем. */
    private fun normalizeName(value: String): String = value.split(' ').joinToString(" ") { word ->
        if (word.length > 2 && word.all { !it.isLetter() || it.isUpperCase() }) {
            word.lowercase().split('-').joinToString("-") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        } else word
    }

    private fun capitalizeFirst(value: String) = value.replaceFirstChar(Char::uppercaseChar)

    private val CYR_TO_LAT = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'к' to 'k', 'м' to 'm',
        'т' to 't', 'в' to 'b', 'н' to 'h', 'і' to 'i',
        'А' to 'A', 'Е' to 'E', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'У' to 'Y', 'Х' to 'X', 'К' to 'K', 'М' to 'M',
        'Т' to 'T', 'В' to 'B', 'Н' to 'H', 'І' to 'I',
    )

    /**
     * Меняет кириллические «двойники» на латиницу только внутри слов, где уже есть латиница
     * или которые похожи на адрес (есть @ или точка) — чтобы «info@mебель.рф» не превращалось в мусор.
     */
    private fun latinizeLookalikes(value: String): String = value.split(' ').joinToString(" ") { word ->
        val hasLatin = word.any { it in 'a'..'z' || it in 'A'..'Z' }
        val looksTechnical = '@' in word || (word.contains('.') && hasLatin) || word.startsWith("www", true)
        if (hasLatin && looksTechnical && !word.endsWith(".рф", true)) {
            word.map { CYR_TO_LAT[it] ?: it }.joinToString("")
        } else word
    }

    /** В кандидатах в телефон меняем O→0, l/I/|→1 между цифрами. */
    private fun fixDigitLookalikes(value: String): String =
        value.replace(Regex("(?<=[\\d\\s()\\-])[OoОо](?=[\\d\\s()\\-])"), "0")
            .replace(Regex("(?<=[\\d\\s()\\-])[lI|](?=[\\d\\s()\\-])"), "1")

    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val TELEGRAM_HANDLE = Regex("(?<![\\w.@])@([A-Za-z][A-Za-z0-9_]{4,31})\\b")
    private val URL = Regex(
        "(?iu)\\b(?:https?://)?(?:www\\.)?[a-zа-я0-9][a-zа-я0-9\\-]*(?:\\.[a-zа-я0-9\\-]+)*" +
            "\\.(?:ru|рф|com|su|net|org|info|biz|pro|online|shop|site|store|by|ua|kz|io|me|club|moscow|top|xyz|art|tech|dev)" +
            "(?:/[\\w\\-./?=&%#]*)?",
    )
    private val PHONE_CANDIDATE = Regex("\\+?\\d[\\d\\s()\\-.]{8,18}\\d")
    private val REQUISITES = Regex("(?iu)ИНН|ОГРН|КПП|БИК|ОКПО|р/с|к/с|счёт|счет")
    private val FAX = Regex("(?iu)факс|fax")
    private val TAG = Regex("\\s*\\(([^)\\d]*)\\)\\s*$")
    private val MESSENGERS = listOf(
        Regex("(?iu)whats\\s?app|в[ао]т?[сц]ап|(?<![\\p{L}])wa(?![\\p{L}])") to "WhatsApp",
        Regex("(?iu)telegram|телеграм|(?<![\\p{L}])tg(?![\\p{L}])") to "Telegram",
        Regex("(?iu)viber|вайбер") to "Viber",
    )
    private val MESSENGER_WORDS = Regex("(?iu)whats\\s?app|в[ао]т?[сц]ап|telegram|телеграм|viber|вайбер|(?<![\\p{L}])(wa|tg)(?![\\p{L}])")
    private val LABELS = Regex(
        "(?iu)(?<![\\p{L}])(тел(ефон)?|моб(ильный)?|раб(очий)?|сот(овый)?|факс|fax|tel|phone|mob|mobile|cell|e-?mail|почта|сайт|web|site|адрес|address|viber|whatsapp|telegram|tg|вайбер|ватсап|телеграм)\\.?\\s*:?(?![\\p{L}])",
    )
    private val ADDRESS = Regex(
        "(?iu)(?<![\\p{L}])(г\\.|город|ул\\.|улица|пр\\.|пр-т|просп|проспект|пер\\.|переулок|бул\\.|бульвар|ш\\.|шоссе|пл\\.|площадь|наб\\.|д\\.|дом|корп\\.|стр\\.|оф\\.|офис|кв\\.|обл\\.|область|р-н|район|мкр|пгт|ТЦ|ТРЦ|рынок)(?![\\p{L}])",
    )
    private val POSTCODE = Regex("(?<!\\d)\\d{6}(?!\\d)")
    private val COMPANY = Regex(
        "(?iu)(?<![\\p{L}])(ООО|ОАО|ЗАО|ПАО|АО|ИП|ЧП|ФЛП|ГУП|МУП|НКО|LLC|Ltd|Inc|GmbH|Corp|Group|компания|группа компаний|ГК|холдинг|завод|фабрика|магазин|салон|студия|агентство|центр|сервис|клиника|бюро|мебель)(?![\\p{L}])|[«\"]",
    )
    private val POSITION = Regex(
        "(?iu)(директор|генеральн|менеджер|руководител|начальник|специалист|инженер|бухгалтер|юрист|адвокат|консультант|дизайнер|координатор|представител|заместител|президент|основател|владел|мастер|врач|агент|продаж|отдел|эксперт|администратор|архитектор|технолог|нотариус|риелтор|риэлтор|оценщик|аналитик|разработчик|программист|снабжен|логист|маркетолог|CEO|CTO|CFO|COO|manager|director|head of|sales|founder|owner|engineer|designer|consultant)",
    )
}

/**
 * Разбор QR-визиток: vCard (BEGIN:VCARD) и MeCard (MECARD:).
 * Возвращает null, если это не визитка.
 */
object VCardParser {
    fun parse(raw: String): ParsedContact? {
        val text = raw.trim()
        return when {
            text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text)
            text.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(text)
            else -> null
        }
    }

    private fun parseVCard(text: String): ParsedContact {
        // Склеиваем перенесённые строки (начинаются с пробела или табуляции) и мягкие переносы quoted-printable.
        val unfolded = text.replace("\r\n", "\n").replace("\r", "\n")
            .replace(Regex("=\n"), "").replace(Regex("\n[ \t]"), "")
        var fn = ""; var n = ""; var org = ""; var title = ""; var address = ""; var note = ""
        val phones = mutableListOf<String>(); val emails = mutableListOf<String>(); val urls = mutableListOf<String>()
        for (line in unfolded.lines()) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val params = head.split(';')
            val key = params.first().substringAfterLast('.').uppercase()
            var value = line.substring(colon + 1)
            if (params.any { it.equals("ENCODING=QUOTED-PRINTABLE", true) }) value = decodeQuotedPrintable(value)
            value = value.replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").trim()
            when (key) {
                "FN" -> fn = value
                "N" -> n = value.split(';').let { parts ->
                    listOf(parts.getOrNull(0), parts.getOrNull(1), parts.getOrNull(2))
                        .filterNot { it.isNullOrBlank() }.joinToString(" ")
                }
                "ORG" -> org = value.split(';').filter(String::isNotBlank).joinToString(", ")
                "TITLE", "ROLE" -> if (title.isBlank()) title = value
                "TEL" -> BusinessCardParser.normalizePhone(value)?.let { phones += it } ?: run { if (value.isNotBlank()) phones += value }
                "EMAIL" -> if (value.isNotBlank()) emails += value.lowercase()
                "URL" -> if (value.isNotBlank()) urls += value.removePrefix("https://").removePrefix("http://")
                "ADR" -> if (address.isBlank()) address = value.split(';').filter(String::isNotBlank).joinToString(", ")
                "NOTE" -> note = value
            }
        }
        return ParsedContact(
            fullName = fn.ifBlank { n }, company = org, position = title,
            phones = phones.distinct(), emails = emails.distinct(), websites = urls.distinct(),
            address = address, note = note,
        )
    }

    private fun parseMeCard(text: String): ParsedContact {
        val body = text.substringAfter(':')
        val fields = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var escaped = false
        for (ch in body) {
            when {
                escaped -> { current.append(ch); escaped = false }
                ch == '\\' -> escaped = true
                ch == ';' -> { addMeField(current.toString(), fields); current.clear() }
                else -> current.append(ch)
            }
        }
        addMeField(current.toString(), fields)
        fun all(key: String) = fields.filter { it.first == key }.map { it.second }.filter(String::isNotBlank)
        val name = all("N").firstOrNull().orEmpty().split(',').filter(String::isNotBlank).joinToString(" ")
        return ParsedContact(
            fullName = name,
            company = all("ORG").firstOrNull().orEmpty(),
            position = all("TITLE").firstOrNull().orEmpty(),
            phones = all("TEL").map { BusinessCardParser.normalizePhone(it) ?: it }.distinct(),
            emails = all("EMAIL").map(String::lowercase).distinct(),
            websites = all("URL").map { it.removePrefix("https://").removePrefix("http://") }.distinct(),
            address = all("ADR").firstOrNull().orEmpty().split(",").map(String::trim).filter(String::isNotBlank).joinToString(", "),
            note = all("NOTE").firstOrNull().orEmpty(),
        )
    }

    private fun addMeField(field: String, out: MutableList<Pair<String, String>>) {
        val colon = field.indexOf(':')
        if (colon > 0) out += field.substring(0, colon).uppercase() to field.substring(colon + 1).trim()
    }

    private fun decodeQuotedPrintable(value: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) { bytes.write(byte); i += 3; continue }
            }
            bytes.write(c.code.toByte().toInt())
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
