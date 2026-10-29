package ru.cardly.mobile.cards

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Build
import android.view.WindowManager
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cardly.core.barcode.BarcodeBitmapRenderer
import ru.cardly.core.barcode.isSquare
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.Card
import ru.cardly.mobile.data.rememberImage
import ru.cardly.mobile.home.CARD_RATIO
import ru.cardly.mobile.home.visual
import ru.cardly.mobile.ui.photoFrame
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.AppSettings
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyMenu
import ru.cardly.mobile.ui.MenuRow

/**
 * Показ карты на кассе: фото, крупный штрихкод, номер.
 * Между картами — свайп влево/вправо или стрелки внизу.
 */
@Composable
fun CardViewScreen(
    cards: List<Card>,
    startId: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit,
    onToggleFavorite: (Card) -> Unit,
    onDelete: (Card) -> Unit,
    onShown: (Card) -> Unit,
) {
    BackHandler(onBack = onBack)
    if (cards.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val startIndex = remember(startId) { cards.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { cards.size }
    val current = cards[pager.currentPage.coerceIn(0, cards.lastIndex)]
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Card?>(null) }
    // Любое касание экрана продлевает время, пока экран не гаснет.
    var touches by remember { mutableStateOf(0) }
    DisplayPolicy(settings, wakeKey = current.id to touches)

    LaunchedEffect(current.id) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onShown(current)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeTopPadding().navigationBarsPadding()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) touches++
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(
                current.name, Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onEdit(current) }) { Icon(Icons.Filled.Edit, "Изменить") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                CardlyMenu(menu, { menu = false }) {
                    MenuRow("Скопировать номер", Icons.Filled.ContentCopy) { menu = false; copyNumber(context, current.cardNumber.ifBlank { current.barcodeValue }) }
                    MenuRow(if (current.favorite) "Убрать из избранного" else "В избранное", if (current.favorite) Icons.Filled.StarBorder else Icons.Filled.Star) {
                        menu = false; onToggleFavorite(current)
                    }
                    MenuRow("Удалить", Icons.Filled.Delete, danger = true) { menu = false; confirmDelete = current }
                }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1, key = { cards[it].id }) { page ->
            CardPage(cards[page], context, settings.showCardImage)
        }

        if (settings.cardSwitcher && cards.size > 1) {
            val colors = current.visual().colors
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).height(64.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Brush.horizontalGradient(colors)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1 + cards.size) % cards.size) } }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.ChevronLeft, "Предыдущая карта", Modifier.size(34.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pager.currentPage + 1} из ${cards.size}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % cards.size) } }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.ChevronRight, "Следующая карта", Modifier.size(34.dp), tint = Color.White)
                }
            }
        } else Spacer(Modifier.height(12.dp))
    }

    confirmDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить карту?") },
            text = { Text("«${card.name}» будет удалена с телефона и часов.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(card) }) { Text("Удалить", color = Brand.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun CardPage(card: Card, context: Context, showImage: Boolean) {
    val front by rememberImage(if (showImage) card.frontImagePath else null, 1440)
    val back by rememberImage(if (showImage) card.backImagePath else null, 1440)
    // Касание по фото переворачивает карту на обратную сторону (свайп занят переключением карт).
    var flipped by remember(card.id) { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        if (flipped) 180f else 0f, androidx.compose.animation.core.tween(380), label = "flip",
    )
    val barcode by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, card.barcodeValue, card.barcodeFormat) {
        value = withContext(Dispatchers.Default) {
            if (card.barcodeValue.isBlank()) null
            else runCatching { BarcodeBitmapRenderer.render(card.barcodeValue, card.barcodeFormat) }
                .recoverCatching { BarcodeBitmapRenderer.render(card.barcodeValue, BarcodeFormat.CODE_128) }
                .getOrNull()
        }
    }
    val number = card.cardNumber.ifBlank { card.barcodeValue }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        val shown = if (rotation <= 90f) front else (back ?: front)
        if (shown != null) Box(
            Modifier.fillMaxWidth().aspectRatio(CARD_RATIO)
                .graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
                .clip(RoundedCornerShape(22.dp)).photoFrame(22.dp)
                .clickable(enabled = back != null && front != null) { flipped = !flipped },
        ) {
            Image(
                shown.asImageBitmap(), "Карта ${card.name}",
                Modifier.fillMaxSize().graphicsLayer { if (rotation > 90f) rotationY = 180f },
                contentScale = ContentScale.Crop,
            )
            if (back != null && front != null) Box(
                Modifier.align(if (rotation > 90f) Alignment.BottomStart else Alignment.BottomEnd).padding(10.dp).size(34.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .graphicsLayer { if (rotation > 90f) rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.FlipCameraAndroid, "Перевернуть", Modifier.size(20.dp), tint = Color.White) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val code = barcode
            if (code != null) Image(
                code.asImageBitmap(), "Штрихкод ${card.name}",
                Modifier.fillMaxWidth(if (card.barcodeFormat.isSquare()) 0.72f else 1f)
                    .aspectRatio(if (card.barcodeFormat.isSquare()) 1f else 2.3f),
                contentScale = ContentScale.FillBounds,
            ) else Box(Modifier.fillMaxWidth().height(120.dp))
            if (number.isNotBlank()) Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { copyNumber(context, number) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(number, color = Color(0xFF111111), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                Icon(Icons.Filled.ContentCopy, "Скопировать", Modifier.padding(start = 8.dp).size(18.dp), tint = Color(0xFF777777))
            }
        }
        if (card.note.isNotBlank()) Text(card.note, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyNumber(context: Context, value: String) {
    if (value.isBlank()) return
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Номер карты", value))
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Номер скопирован", Toast.LENGTH_SHORT).show()
}

/**
 * Яркость и «не гасить экран» на время показа карты (выбранное время после последнего
 * касания или переключения карты) и защита от случайной NFC-оплаты.
 */
@Composable
private fun DisplayPolicy(settings: AppSettings, wakeKey: Any) {
    val activity = LocalContext.current as? Activity ?: return
    val window = activity.window
    val normalBrightness = remember { window.attributes.screenBrightness }

    // Яркость и экран: включаем при открытии/переключении карты, через минуту возвращаем как было.
    LaunchedEffect(wakeKey, settings.maximumBrightness, settings.keepScreenMinutes) {
        if (settings.maximumBrightness) window.attributes = window.attributes.apply { screenBrightness = 1f }
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // «Всегда» — пока открыта карта; без «не гасить» яркость держим минуту; иначе — выбранное время после касания.
        if (settings.keepScreenMinutes < 0) kotlinx.coroutines.awaitCancellation()
        kotlinx.coroutines.delay(settings.keepScreenMinutes.coerceAtLeast(1) * 60_000L)
        window.attributes = window.attributes.apply { screenBrightness = normalBrightness }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    DisposableEffect(activity, settings.nfcProtection) {
        var readerMode = false
        if (settings.nfcProtection) runCatching {
            NfcAdapter.getDefaultAdapter(activity)?.enableReaderMode(
                activity, { },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null,
            )
            readerMode = true
        }
        onDispose {
            if (readerMode) runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
            window.attributes = window.attributes.apply { screenBrightness = normalBrightness }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
