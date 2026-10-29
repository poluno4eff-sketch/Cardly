package ru.cardly.mobile.business

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cardly.core.model.BusinessCard
import ru.cardly.mobile.data.CardImages
import ru.cardly.mobile.data.rememberImage
import ru.cardly.mobile.home.CARD_RATIO
import ru.cardly.mobile.scan.rememberDocumentScanner
import ru.cardly.mobile.scan.rememberImagePicker
import ru.cardly.mobile.ui.photoFrame
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyField
import ru.cardly.mobile.ui.ChipButton
import ru.cardly.mobile.ui.GradientPill
import ru.cardly.mobile.ui.Panel
import ru.cardly.mobile.ui.PanelSwitch

data class ContactDraft(
    val source: BusinessCard? = null,
    val fullName: String = source?.fullName.orEmpty(),
    val company: String = source?.company.orEmpty(),
    val position: String = source?.position.orEmpty(),
    val phones: List<String> = source?.phones.orEmpty(),
    val emails: List<String> = source?.emails.orEmpty(),
    val websites: List<String> = source?.websites.orEmpty(),
    val address: String = source?.address.orEmpty(),
    val note: String = source?.note.orEmpty(),
    val rawText: String = source?.rawText.orEmpty(),
    val favorite: Boolean = source?.favorite ?: false,
    val stagedFront: String? = null,
    val stagedBack: String? = null,
    /** Нужно ли распознать текст на новых фото при открытии редактора. */
    val recognize: Boolean = false,
) {
    fun applying(contact: ParsedContact, raw: String): ContactDraft = copy(
        fullName = fullName.ifBlank { contact.fullName },
        company = company.ifBlank { contact.company },
        position = position.ifBlank { contact.position },
        phones = (phones + contact.phones).distinctBy { it.filter(Char::isDigit) },
        emails = (emails + contact.emails).distinctBy(String::lowercase),
        websites = (websites + contact.websites).distinctBy(String::lowercase),
        address = address.ifBlank { contact.address },
        note = note.ifBlank { contact.note },
        rawText = raw.ifBlank { rawText },
    )

    val hasContent: Boolean
        get() = listOf(fullName, company, position, address).any(String::isNotBlank) ||
            phones.any(String::isNotBlank) || emails.any(String::isNotBlank) || stagedFront != null || source?.frontImagePath != null
}

/** Проверка и правка распознанной визитки. */
@Composable
fun BusinessEditorScreen(
    initial: ContactDraft,
    onClose: () -> Unit,
    onSave: (ContactDraft) -> Unit,
    onDelete: ((BusinessCard) -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var recognizing by remember { mutableStateOf(false) }

    fun close() {
        CardImages.delete(draft.stagedFront); CardImages.delete(draft.stagedBack); onClose()
    }
    BackHandler(onBack = ::close)

    fun recognize(paths: List<String>) {
        recognizing = true
        scope.launch {
            val result = runCatching { BusinessCardOcr.recognize(context, paths) }.getOrNull()
            recognizing = false
            if (result == null || (result.contact.isEmpty && result.rawText.isBlank())) {
                Toast.makeText(context, "Текст не распознан — заполните вручную", Toast.LENGTH_LONG).show()
            } else {
                draft = draft.applying(result.contact, result.rawText)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initial.recognize) {
            listOfNotNull(initial.stagedFront, initial.stagedBack).takeIf { it.isNotEmpty() }?.let(::recognize)
        }
    }

    fun setImages(uris: List<android.net.Uri>) {
        scope.launch {
            val staged = withContext(Dispatchers.IO) { uris.take(2).mapNotNull { CardImages.stage(context, it, 2200) } }
            if (staged.isEmpty()) return@launch
            CardImages.delete(draft.stagedFront); CardImages.delete(draft.stagedBack)
            draft = draft.copy(stagedFront = staged[0], stagedBack = staged.getOrNull(1))
            recognize(staged)
        }
    }
    val camera = rememberDocumentScanner(pages = 2) { setImages(it) }
    val gallery = rememberImagePicker { setImages(listOf(it)) }

    val front by rememberImage(draft.stagedFront ?: draft.source?.frontImagePath, 1080)
    val back by rememberImage(draft.stagedBack ?: draft.source?.backImagePath.takeIf { draft.stagedFront == null }, 720)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeTopPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close) { Icon(Icons.Filled.Close, "Закрыть") }
            Text(if (draft.source == null) "Новая визитка" else "Визитка", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            GradientPill("Сохранить", enabled = draft.hasContent && !recognizing, modifier = Modifier.padding(end = 8.dp)) {
                onSave(draft.copy(
                    phones = draft.phones.map(String::trim).filter(String::isNotEmpty),
                    emails = draft.emails.map(String::trim).filter(String::isNotEmpty),
                    websites = draft.websites.map(String::trim).filter(String::isNotEmpty),
                ))
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(if (back != null) 2f else 1f).aspectRatio(CARD_RATIO).clip(RoundedCornerShape(18.dp)).photoFrame(18.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = front
                    if (bitmap != null) Image(bitmap.asImageBitmap(), "Лицевая сторона", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.DocumentScanner, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Фото визитки", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (recognizing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Распознаю…", color = Color.White, modifier = Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                back?.let { bitmap ->
                    Image(
                        bitmap.asImageBitmap(), "Обратная сторона",
                        Modifier.weight(1f).aspectRatio(CARD_RATIO).clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(Icons.Filled.DocumentScanner, "Камера", Modifier.weight(1f)) { camera() }
                ChipButton(Icons.Filled.Image, "Галерея", Modifier.weight(1f)) { gallery() }
            }
            if (!recognizing && draft.rawText.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = Brand.cyan)
                Text("Поля заполнены автоматически — проверьте их", Modifier.padding(start = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CardlyField(draft.fullName, { draft = draft.copy(fullName = it) }, "Имя и фамилия", leadingIcon = Icons.Filled.Person, capitalization = KeyboardCapitalization.Words)
            CardlyField(draft.position, { draft = draft.copy(position = it) }, "Должность", leadingIcon = Icons.Filled.Work)
            CardlyField(draft.company, { draft = draft.copy(company = it) }, "Компания", leadingIcon = Icons.Filled.Business)

            ListFields("Телефон", Icons.Filled.Call, draft.phones, KeyboardType.Phone) { draft = draft.copy(phones = it) }
            ListFields("Email", Icons.Filled.Email, draft.emails, KeyboardType.Email) { draft = draft.copy(emails = it) }
            ListFields("Сайт или соцсеть", Icons.Filled.Language, draft.websites, KeyboardType.Uri) { draft = draft.copy(websites = it) }

            CardlyField(draft.address, { draft = draft.copy(address = it) }, "Адрес", leadingIcon = Icons.Filled.LocationOn, singleLine = false)
            CardlyField(draft.note, { draft = draft.copy(note = it) }, "Заметка", singleLine = false)

            if (draft.source != null && onDelete != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDelete(draft.source!!) }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Brand.danger)
                    Text("Удалить визитку", Modifier.padding(start = 8.dp), color = Brand.danger, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ListFields(label: String, icon: ImageVector, values: List<String>, keyboard: KeyboardType, onChange: (List<String>) -> Unit) {
    val items = values.ifEmpty { listOf("") }
    items.forEachIndexed { index, value ->
        CardlyField(
            value, { text -> onChange(items.toMutableList().also { it[index] = text }) }, label,
            leadingIcon = icon, keyboardType = keyboard, capitalization = KeyboardCapitalization.None,
            trailing = if (items.size > 1 || value.isNotBlank()) ({
                IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Filled.Close, "Убрать")
                }
            }) else null,
        )
    }
    if (items.last().isNotBlank()) Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onChange(items + "") }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Ещё ${label.lowercase()}", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}
