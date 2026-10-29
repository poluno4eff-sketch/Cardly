package ru.cardly.mobile.business

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cardly.core.model.BusinessCard
import ru.cardly.mobile.data.rememberImage
import ru.cardly.mobile.home.CARD_RATIO
import ru.cardly.mobile.ui.photoFrame
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyMenu
import ru.cardly.mobile.ui.IconBadge
import ru.cardly.mobile.ui.MenuRow
import ru.cardly.mobile.ui.Panel
import ru.cardly.mobile.ui.PanelDivider

private val callGreen = Color(0xFF2ECC71)
private val whatsapp = Color(0xFF25D366)
private val telegram = Color(0xFF2AABEE)

/** Экран визитки: позвонить, написать, открыть сайт или адрес, сохранить в контакты. */
@Composable
fun BusinessViewScreen(
    contact: BusinessCard,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showBack by remember { mutableStateOf(false) }
    var rawOpen by remember { mutableStateOf(false) }
    val image by rememberImage(if (showBack) contact.backImagePath else contact.frontImagePath, 1440)
    // Звонок — первый номер (не факс); WhatsApp/Telegram — номер с такой подписью на визитке, иначе тот же.
    // Подписи номеров (WhatsApp, факс…): из самого номера, а для старых визиток — из сохранённого распознанного текста.
    val textTags = remember(contact.rawText) {
        if (contact.rawText.isBlank()) emptyMap()
        else BusinessCardParser.parse(contact.rawText).phones.mapNotNull { p ->
            BusinessCardParser.phoneTag(p)?.let { dialable(p).filter(Char::isDigit) to it }
        }.toMap()
    }
    fun tagOf(value: String) = BusinessCardParser.phoneTag(value) ?: textTags[dialable(value).filter(Char::isDigit)]
    val phone = (contact.phones.firstOrNull { tagOf(it)?.contains("факс") != true } ?: contact.phones.firstOrNull())?.let(::dialable)
    fun tagged(name: String) = contact.phones.firstOrNull { tagOf(it)?.contains(name, ignoreCase = true) == true }?.let(::dialable)
    val whatsappPhone = tagged("WhatsApp") ?: phone
    val telegramPhone = tagged("Telegram") ?: phone
    // Сайт (не ссылка на Telegram) — отдельная кнопка; при пяти кнопках делаем их чуть меньше.
    val site = contact.websites.firstOrNull { !it.contains("t.me/", ignoreCase = true) }
    val compact = site != null

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeTopPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Изменить") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                CardlyMenu(menu, { menu = false }) {
                    MenuRow("Поделиться", Icons.Filled.Share) { menu = false; share(context, contact) }
                    MenuRow(if (contact.favorite) "Убрать из избранного" else "В избранное", if (contact.favorite) Icons.Filled.StarBorder else Icons.Filled.Star) {
                        menu = false; onToggleFavorite()
                    }
                    MenuRow("Удалить", Icons.Filled.Delete, danger = true) { menu = false; confirmDelete = true }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            image?.let { bitmap ->
                Image(
                    bitmap.asImageBitmap(), "Визитка",
                    Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clip(RoundedCornerShape(20.dp)).photoFrame(20.dp)
                        .clickable(enabled = contact.backImagePath != null) { showBack = !showBack },
                    contentScale = ContentScale.Crop,
                )
                if (contact.backImagePath != null) Text(
                    if (showBack) "Обратная сторона · нажмите, чтобы перевернуть" else "Нажмите на фото, чтобы перевернуть",
                    Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(contact.title, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineLarge)
            val sub = listOf(contact.position, contact.company.takeIf { contact.fullName.isNotBlank() }.orEmpty())
                .filter(String::isNotBlank).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Быстрые действия
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction(Icons.Filled.Call, "Позвонить", callGreen, phone != null, compact) { phone?.let { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } }
                QuickAction(Icons.AutoMirrored.Filled.Send, "Telegram", telegram, telegramPhone != null || contact.telegram() != null, compact) {
                    val target = contact.telegram()?.let { "https://t.me/$it" } ?: telegramPhone?.let { "https://t.me/$it" }
                    target?.let { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
                QuickAction(Icons.AutoMirrored.Filled.Chat, "WhatsApp", whatsapp, whatsappPhone != null, compact) { whatsappPhone?.let { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${it.trimStart('+')}"))) } }
                QuickAction(Icons.Filled.Email, "Почта", Brand.violet, contact.emails.isNotEmpty(), compact) {
                    open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.emails.first()}")))
                }
                site?.let { url ->
                    QuickAction(Icons.Filled.Language, "Сайт", Brand.cyan, true, compact) {
                        open(context, Intent(Intent.ACTION_VIEW, Uri.parse(if (url.startsWith("http")) url else "https://$url")))
                    }
                }
            }

            Panel {
                contact.phones.forEachIndexed { index, value ->
                    if (index > 0) PanelDivider()
                    val number = dialable(value)
                    val tag = tagOf(value)
                    ContactRow(Icons.Filled.Call, callGreen, BusinessCardParser.phoneWithoutTag(value), tag?.replaceFirstChar(Char::uppercaseChar) ?: "Телефон", onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }, onLong = { copy(context, value) })
                }
                val rows = buildList {
                    contact.emails.forEach { add(Triple(Icons.Filled.Email, it, "mailto:$it")) }
                    contact.websites.forEach { add(Triple(Icons.Filled.Language, it, if (it.startsWith("http")) it else "https://$it")) }
                    if (contact.address.isNotBlank()) add(Triple(Icons.Filled.LocationOn, contact.address, "geo:0,0?q=" + Uri.encode(contact.address)))
                }
                rows.forEachIndexed { index, (icon, text, link) ->
                    if (index > 0 || contact.phones.isNotEmpty()) PanelDivider()
                    val tint = when (icon) { Icons.Filled.Email -> Brand.violet; Icons.Filled.LocationOn -> Color(0xFFFF8A3D); else -> Brand.cyan }
                    val label = when (icon) { Icons.Filled.Email -> "Почта"; Icons.Filled.LocationOn -> "Адрес"; else -> "Сайт" }
                    ContactRow(icon, tint, text, label, onClick = {
                        open(context, Intent(if (link.startsWith("mailto")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(link)))
                    }, onLong = { copy(context, text) })
                }
                if (contact.phones.isEmpty() && rows.isEmpty()) Text(
                    "Контактов нет — нажмите ✎, чтобы добавить", Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Brand.gradient)
                    .clickable { saveToContacts(context, contact) }.padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PersonAdd, null, tint = Color.White)
                Text("Сохранить в контакты", Modifier.padding(start = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            if (contact.note.isNotBlank()) {
                Text("Заметка", Modifier.padding(top = 18.dp, start = 4.dp), fontWeight = FontWeight.SemiBold)
                Text(contact.note, Modifier.padding(top = 4.dp, start = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (contact.rawText.isNotBlank()) {
                Text(
                    if (rawOpen) "Скрыть распознанный текст" else "Показать распознанный текст",
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(10.dp)).clickable { rawOpen = !rawOpen }.padding(6.dp),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
                if (rawOpen) Text(contact.rawText, Modifier.padding(6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить визитку?") },
        text = { Text("«${contact.title}» будет удалена.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = Brand.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, tint: Color, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(if (compact) 3.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(if (compact) 52.dp else 58.dp).clip(CircleShape).background(
                if (enabled) androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint))
                else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, title, tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(if (compact) 25.dp else 27.dp)) }
        Text(title, Modifier.padding(top = 6.dp), fontSize = 12.sp, maxLines = 1, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector, tint: Color, text: String, label: String,
    onClick: () -> Unit, onLong: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
        IconButton(onClick = onLong) { Icon(Icons.Filled.ContentCopy, "Скопировать", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
}

/** «+7 (949) 123-45-67 (WhatsApp)» → «+79491234567». */
fun dialable(value: String): String {
    val core = BusinessCardParser.phoneWithoutTag(value)
    val digits = core.filter { it.isDigit() }
    return if (core.trim().startsWith("+")) "+$digits" else digits
}

private fun BusinessCard.telegram(): String? = websites.firstOrNull { it.startsWith("t.me/", true) }
    ?.substringAfter("t.me/")?.substringBefore('/')?.takeIf { it.isNotBlank() }

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, value: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Контакт", value))
    }
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

/** Открывает системную форму нового контакта с заполненными полями (разрешения не нужны). */
private fun saveToContacts(context: Context, contact: BusinessCard) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        if (contact.fullName.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, contact.fullName)
        if (contact.company.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, contact.company)
        if (contact.position.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, contact.position)
        if (contact.address.isNotBlank()) putExtra(ContactsContract.Intents.Insert.POSTAL, contact.address)
        if (contact.note.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NOTES, contact.note)
        val data = ArrayList<ContentValues>()
        contact.phones.forEach { phone ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, dialable(phone))
                put(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    if (phone.contains("факс")) ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK else ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
                )
            }
        }
        contact.emails.forEach { email ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            }
        }
        contact.websites.forEach { site ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Website.URL, site)
            }
        }
        putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
    }
    open(context, intent)
}

/** Делится визиткой обычным текстом — открывается в любом мессенджере. */
private fun share(context: Context, contact: BusinessCard) {
    val text = buildString {
        appendLine(contact.title)
        if (contact.position.isNotBlank()) appendLine(contact.position)
        if (contact.company.isNotBlank() && contact.fullName.isNotBlank()) appendLine(contact.company)
        contact.phones.forEach { appendLine(it) }
        contact.emails.forEach { appendLine(it) }
        contact.websites.forEach { appendLine(it) }
        if (contact.address.isNotBlank()) appendLine(contact.address)
    }.trim()
    open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться визиткой"))
}
