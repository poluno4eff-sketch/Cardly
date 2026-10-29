package ru.cardly.mobile.cards

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import ru.cardly.core.barcode.canEncodeBarcode
import ru.cardly.core.barcode.guessBarcodeFormat
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.Card
import ru.cardly.mobile.data.CardImages
import ru.cardly.mobile.data.rememberImage
import ru.cardly.mobile.home.CARD_RATIO
import ru.cardly.mobile.home.visual
import ru.cardly.mobile.scan.ScanValue
import ru.cardly.mobile.scan.decodeBarcode
import ru.cardly.mobile.scan.rememberBarcodeScanner
import ru.cardly.mobile.scan.rememberDocumentScanner
import ru.cardly.mobile.scan.rememberImagePicker
import ru.cardly.mobile.ui.photoFrame
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyField
import ru.cardly.mobile.ui.CardlyMenu
import ru.cardly.mobile.ui.ChipButton
import ru.cardly.mobile.ui.GradientPill
import ru.cardly.mobile.ui.MenuRow
import ru.cardly.mobile.ui.Panel
import ru.cardly.mobile.ui.PanelDivider
import ru.cardly.mobile.ui.PanelSwitch

/** Черновик карты для редактора. [stagedImage] — новое фото, ещё не сохранённое. */
data class CardDraft(
    val source: Card? = null,
    val name: String = source?.name.orEmpty(),
    val number: String = source?.barcodeValue.orEmpty(),
    val format: BarcodeFormat? = source?.barcodeFormat?.takeIf { it != BarcodeFormat.UNKNOWN },
    val note: String = source?.note.orEmpty(),
    val favorite: Boolean = source?.favorite ?: false,
    val showName: Boolean = source?.showName ?: true,
    val stagedImage: String? = null,
    val removeImage: Boolean = false,
    val stagedBack: String? = null,
    val removeBack: Boolean = false,
)

fun formatTitle(format: BarcodeFormat): String = when (format) {
    BarcodeFormat.CODE_128 -> "Code 128"; BarcodeFormat.CODE_39 -> "Code 39"; BarcodeFormat.CODE_93 -> "Code 93"
    BarcodeFormat.CODABAR -> "Codabar"; BarcodeFormat.EAN_13 -> "EAN-13"; BarcodeFormat.EAN_8 -> "EAN-8"
    BarcodeFormat.ITF -> "ITF"; BarcodeFormat.UPC_A -> "UPC-A"; BarcodeFormat.UPC_E -> "UPC-E"
    BarcodeFormat.QR_CODE -> "QR-код"; BarcodeFormat.DATA_MATRIX -> "Data Matrix"; BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.AZTEC -> "Aztec"; BarcodeFormat.UNKNOWN -> "Авто"
}

/**
 * Редактор карты. Главное — фото, название и номер. Тип штрихкода определяется
 * автоматически (по сканеру или по номеру) и пользователю не показывается.
 */
@Composable
fun CardEditorScreen(
    initial: CardDraft,
    otherCards: List<Card>,
    onClose: () -> Unit,
    onSave: (CardDraft, BarcodeFormat) -> Unit,
    onDelete: ((Card) -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }

    // Открытая сторона в превью: 0 — лицевая, 1 — обратная.
    val sides = rememberPagerState { 2 }
    fun close() { CardImages.delete(draft.stagedImage); CardImages.delete(draft.stagedBack); onClose() }
    BackHandler(onBack = ::close)

    fun applyScan(scan: ScanValue) {
        draft = draft.copy(number = scan.value, format = scan.format)
    }

    /** Новое фото стороны карты; если номер ещё пуст — пробуем найти на фото штрихкод. */
    fun useImage(staged: String?, back: Boolean) {
        if (staged == null) {
            Toast.makeText(context, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show(); return
        }
        draft = if (back) {
            CardImages.delete(draft.stagedBack)
            draft.copy(stagedBack = staged, removeBack = false)
        } else {
            CardImages.delete(draft.stagedImage)
            draft.copy(stagedImage = staged, removeImage = false)
        }
        if (draft.number.isBlank()) scope.launch {
            val found = withContext(Dispatchers.Default) { CardImages.load(staged, 2000)?.let { decodeBarcode(context, it) } }
            if (found != null && draft.number.isBlank()) {
                applyScan(found)
                Toast.makeText(context, "Номер карты найден на фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scanner = rememberBarcodeScanner(::applyScan)
    val codeFromImage = rememberImagePicker { uri ->
        busy = true
        scope.launch {
            val found = withContext(Dispatchers.Default) { CardImages.decodeUri(context, uri)?.let { decodeBarcode(context, it) } }
            busy = false
            if (found != null) applyScan(found) else Toast.makeText(context, "Код на изображении не найден", Toast.LENGTH_LONG).show()
        }
    }
    // Камера снимает одну или обе стороны: два снимка — лицевая и обратная, один — открытая сейчас сторона.
    val photoCamera = rememberDocumentScanner(pages = 2) { uris ->
        scope.launch {
            val staged = withContext(Dispatchers.IO) { uris.take(2).map { CardImages.stage(context, it) } }
            if (staged.size >= 2) {
                useImage(staged[0], back = false); useImage(staged[1], back = true)
            } else useImage(staged.firstOrNull(), back = sides.currentPage == 1)
        }
    }
    val photoGallery = rememberImagePicker { uri ->
        scope.launch { useImage(withContext(Dispatchers.IO) { CardImages.stage(context, uri) }, back = sides.currentPage == 1) }
    }

    val autoFormat = remember(draft.number) { guessBarcodeFormat(draft.number) }
    val chosenFormat = draft.format ?: autoFormat
    val fits = remember(draft.number, chosenFormat) { draft.number.isBlank() || canEncodeBarcode(draft.number.trim(), chosenFormat) }
    val savedFormat = if (fits) chosenFormat else BarcodeFormat.CODE_128
    val duplicate = remember(draft.number, otherCards) {
        draft.number.trim().takeIf { it.isNotEmpty() }?.let { n -> otherCards.firstOrNull { it.barcodeValue.equals(n, ignoreCase = true) } }
    }

    val imagePath = draft.stagedImage ?: draft.source?.frontImagePath?.takeUnless { draft.removeImage }
    val image by rememberImage(imagePath, 1080)
    val backPath = draft.stagedBack ?: draft.source?.backImagePath?.takeUnless { draft.removeBack }
    val backImage by rememberImage(backPath, 1080)
    val canSave = draft.name.isNotBlank() && draft.number.isNotBlank()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeTopPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close) { Icon(Icons.Filled.Close, "Закрыть") }
            Text(if (draft.source == null) "Новая карта" else "Карта", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            GradientPill("Сохранить", enabled = canSave, modifier = Modifier.padding(end = 8.dp)) {
                onSave(draft.copy(name = draft.name.trim(), number = draft.number.trim(), note = draft.note.trim()), savedFormat)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Превью карты — так она будет выглядеть в списке.
            val visual = Card(id = "", name = draft.name.ifBlank { "Карта" }, barcodeValue = "", createdAt = 0, updatedAt = 0).visual()
            // Две стороны карты листаются свайпом.
            HorizontalPager(state = sides, pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { page ->
                val bitmap = if (page == 0) image else backImage
                Box(
                    Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clip(RoundedCornerShape(22.dp)).photoFrame(22.dp)
                        .background(
                            if (page == 0) Brush.linearGradient(visual.colors)
                            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ),
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), if (page == 0) "Лицевая сторона" else "Обратная сторона", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.DocumentScanner, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.8f))
                            Text(
                                if (page == 0) "Добавьте фото карты" else "Обратная сторона (если нужна)",
                                color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    if (page == 0 && (bitmap == null || draft.showName)) Text(
                        draft.name, Modifier.align(Alignment.TopStart).padding(18.dp),
                        color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(2) { index ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (sides.currentPage == index) 9.dp else 7.dp).clip(RoundedCornerShape(50))
                            .background(
                                if (sides.currentPage == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            .clickable { scope.launch { sides.animateScrollToPage(index) } },
                    )
                }
                Text(
                    if (sides.currentPage == 0) "  Лицевая сторона" else "  Обратная сторона",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(Icons.Filled.DocumentScanner, "Камера", Modifier.weight(1f)) { photoCamera() }
                ChipButton(Icons.Filled.Image, "Галерея", Modifier.weight(1f)) { photoGallery() }
                val currentHasImage = if (sides.currentPage == 0) image != null else backImage != null
                if (currentHasImage) Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            draft = if (sides.currentPage == 0) {
                                CardImages.delete(draft.stagedImage); draft.copy(stagedImage = null, removeImage = true)
                            } else {
                                CardImages.delete(draft.stagedBack); draft.copy(stagedBack = null, removeBack = true)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Delete, "Убрать фото", tint = Brand.danger) }
            }

            CardlyField(
                draft.name, { draft = draft.copy(name = it) }, "Название магазина",
                capitalization = KeyboardCapitalization.Words,
            )
            CardlyField(
                draft.number, { draft = draft.copy(number = it, format = null) }, "Номер карты",
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                isError = !fits && !canEncodeBarcode(draft.number.trim(), BarcodeFormat.CODE_128),
                supportingText = when {
                    draft.number.isBlank() -> "Отсканируйте код или введите номер"
                    !fits && !canEncodeBarcode(draft.number.trim(), BarcodeFormat.CODE_128) -> "Такой номер не получится показать штрихкодом"
                    duplicate != null -> "Такая карта уже есть: «${duplicate.name}»"
                    else -> null
                },
                trailing = {
                    Row {
                        IconButton(onClick = { codeFromImage() }) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.ImageSearch, "Код с картинки")
                        }
                        IconButton(onClick = scanner) { Icon(Icons.Filled.QrCodeScanner, "Сканировать", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
            )

            if (image != null) {
                Panel {
                    PanelSwitch(Icons.Filled.TextFields, Brand.cyan, "Название поверх фото", null, draft.showName) {
                        draft = draft.copy(showName = it)
                    }
                }
            }

            CardlyField(draft.note, { draft = draft.copy(note = it) }, "Заметка", singleLine = false, minLines = 1)

            if (draft.source != null && onDelete != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onDelete(draft.source!!) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Brand.danger)
                    Text("Удалить карту", Modifier.padding(start = 8.dp), color = Brand.danger, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
