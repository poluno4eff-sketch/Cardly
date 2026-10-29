package ru.cardly.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import ru.cardly.core.barcode.BarcodeBitmapRenderer
import ru.cardly.core.barcode.isSquare
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.Card

class MainActivity : ComponentActivity() {
    /** Меняется при каждом возвращении в приложение — тогда снова показываем экран оплаты. */
    private val openSignal = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        setContent { WearRoot(version = version, openSignal = openSignal.intValue, onTimerEnd = { finish() }) }
    }

    override fun onRestart() {
        super.onRestart()
        openSignal.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSignal.intValue++
    }
}

/**
 * Три экрана, листаются свайпом: Скидочные карты ← Оплата → Настройки.
 * Приложение открывается на экране оплаты (как Mir Pay Wallet).
 */
@Composable
private fun WearRoot(version: String, openSignal: Int, onTimerEnd: () -> Unit) {
    val context = LocalContext.current
    val pay = remember { PaySettings(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 3 }
    var cardOpen by remember { mutableStateOf(false) }
    // Каждое открытие приложения — сразу экран оплаты (как Mir Pay Wallet).
    LaunchedEffect(openSignal) { if (openSignal > 0) pager.scrollToPage(1) }
    // «Назад» на картах и в настройках возвращает к оплате.
    BackHandler(enabled = pager.currentPage != 1 && !cardOpen) { scope.launch { pager.animateScrollToPage(1) } }

    // Общий таймер: начинается заново при смене экрана, открытии карты и любом касании;
    // по окончании приложение закрывается.
    val page = pager.settledPage
    var restartKey by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(pay.timerSeconds) }
    var left by remember { mutableIntStateOf(pay.timerSeconds) }
    // Таймер идёт на оплате и на открытом коде карты; в списке карт и в настройках — пауза.
    val timed = page == 1 || (page == 0 && cardOpen)
    LaunchedEffect(page, cardOpen, restartKey) {
        total = pay.timerSeconds
        left = total
        if (!timed) return@LaunchedEffect
        val onPay = page == 1
        val vibrator = context.vibrator()
        val haptic = pay.haptic && onPay
        val every = pay.everySecond
        val ms = pay.vibrationMs
        if (haptic && !every) vibrator?.pulse(ms)
        repeat(total) {
            delay(1000)
            left -= 1
            if (haptic && every) vibrator?.pulse(ms)
        }
        if (haptic && !every) vibrator?.pulse(ms)
        onTimerEnd()
    }
    // Пока идёт отсчёт, экран не гаснет (нужно и для оплаты через Mir Pay).
    val activity = context as? Activity
    DisposableEffect(activity, timed) {
        if (timed) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) restartKey++
                }
            }
        },
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager, userScrollEnabled = !cardOpen, modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> WearHome(active = pager.settledPage == 0, onDetailChanged = { cardOpen = it }, onActivity = { restartKey++ })
                1 -> PayScreen(pay, left = left, total = total)
                else -> PaySettingsScreen(pay, version, active = pager.settledPage == 2)
            }
        }
        // На скидочных картах внизу тот же счётчик, что и на оплате.
        // На скидочных картах счётчик виден только на открытой карте с кодом — мелко у края, чтобы не задевать код.
        if (page == 0 && cardOpen) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
            Counter(left, color = Color(0xFF9A9A9A), fontSize = 14.sp)
        }
        if (!cardOpen) PageDots(3, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

private data class WearUiSettings(
    val amoled: Boolean,
    val maximumBrightness: Boolean,
    val keepScreenOn: Boolean,
    val autoCloseSeconds: Int,
)

@Composable
private fun WearHome(active: Boolean, onDetailChanged: (Boolean) -> Unit, onActivity: () -> Unit) {
    val context = LocalContext.current
    var cards by remember { mutableStateOf(WearCardStore.load(context)) }
    var selected by remember { mutableStateOf<Card?>(null) }
    LaunchedEffect(selected) { onDetailChanged(selected != null) }
    // Пустой список при первом запуске — сами забираем каталог, уже отправленный телефоном.
    LaunchedEffect(Unit) {
        if (cards.isEmpty()) withContext(Dispatchers.IO) { CatalogImporter.fetchExisting(context.applicationContext) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                cards = WearCardStore.load(context)
                selected = selected?.let { current -> cards.firstOrNull { it.id == current.id } }
            }
        }
        val filter = IntentFilter(WearCardStore.ACTION_CARDS_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val settings = remember(cards) {
        val prefs = context.getSharedPreferences("wear_display", 0)
        WearUiSettings(
            amoled = prefs.getBoolean("amoled", true),
            maximumBrightness = prefs.getBoolean("maximumBrightness", true),
            keepScreenOn = prefs.getBoolean("keepScreenOn", true),
            autoCloseSeconds = prefs.getInt("autoCloseSeconds", 15),
        )
    }
    val background = if (settings.amoled) Color.Black else Color(0xFF11131A)

    if (selected == null) {
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().background(background).padding(24.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    "Карты появятся после синхронизации с телефоном",
                    style = TextStyle(color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center, fontSize = 14.sp),
                )
            }
        } else {
            val haptics = LocalHapticFeedback.current
            val listState = rememberLazyListState()
            BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
                val rowsHeight = (60.dp * cards.size) + (4.dp * (cards.size - 1).coerceAtLeast(0))
                val centeredPadding = ((maxHeight - rowsHeight) / 2).coerceAtLeast(7.dp)
                val focus = remember { FocusRequester() }
                // Безель/коронка прокручивают список, когда эта страница на экране.
                LaunchedEffect(active) { if (active) runCatching { focus.requestFocus() } }
                val rotaryScope = androidx.compose.runtime.rememberCoroutineScope()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp)
                        .onRotaryScrollEvent { event ->
                            onActivity()
                            rotaryScope.launch { listState.animateScrollBy(event.verticalScrollPixels) }
                            true
                        }
                        .focusRequester(focus).focusable(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = centeredPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = cards, key = { it.id }) { card ->
                        WearCardRow(card) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = card
                        }
                    }
                }
            }
        }
    } else {
        WearCardDetail(selected!!, settings) { selected = null }
    }
}

private fun Card.wearColors(): List<Color> = when (name.trim().lowercase()) {
    "лента" -> listOf(Color(0xFF075CAC), Color(0xFF073B86))
    "магнит" -> listOf(Color(0xFFF20B21), Color(0xFFB60013))
    "пятёрочка", "пятерочка" -> listOf(Color(0xFF07933F), Color(0xFF005B2D))
    else -> listOf(Color(0xFF174EA6), Color(0xFF101D45))
}

@Composable
private fun WearCardRow(card: Card, onClick: () -> Unit) {
    val image = remember(card.frontImagePath, card.updatedAt) {
        card.frontImagePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(card.wearColors())).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) {
            Image(
                image.asImageBitmap(), "Изображение карты ${card.name}",
                // Тонкая белая рамка — картинка не сливается с цветом строки.
                Modifier.size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(7.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { CodeGlyph(square = card.barcodeFormat.isSquare()) }
        }
        BasicText(
            card.name, Modifier.padding(start = 9.dp).weight(1f),
            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WearCardDetail(card: Card, settings: WearUiSettings, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    WearDisplayPolicy(settings)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(card.id) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    // Квадратные коды (QR, Data Matrix, Aztec) рисуем квадратом и вписываем в круглый экран целиком.
    val square = card.barcodeFormat.isSquare()
    val bitmap = remember(card.id, card.barcodeValue, card.barcodeFormat) {
        runCatching {
            BarcodeBitmapRenderer.render(
                value = card.barcodeValue, format = card.barcodeFormat, width = 900,
                height = if (square) 900 else 430, margin = 0,
            )
        }.getOrElse {
            BarcodeBitmapRenderer.render(card.barcodeValue, BarcodeFormat.CODE_128, 900, 430, 0)
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val screen = if (maxWidth < maxHeight) maxWidth else maxHeight
        if (square) {
            // Вписанный в круг квадрат ≈ 70% диаметра; оставляем место под название и белые поля.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    card.name, Modifier.padding(bottom = 4.dp).size(width = screen * 0.6f, height = 18.dp),
                    style = TextStyle(color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Image(
                    bitmap.asImageBitmap(), "Код карты ${card.name}",
                    Modifier.size(screen * 0.6f), contentScale = ContentScale.Fit,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                card.frontImagePath?.let { path ->
                    remember(path, card.updatedAt) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                }?.let { cardImage ->
                    Image(
                        cardImage.asImageBitmap(), "Изображение карты ${card.name}",
                        Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                BasicText(
                    card.name, Modifier.padding(top = 2.dp).size(width = screen * 0.7f, height = 22.dp),
                    style = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Ширина ~80% диаметра — углы штрихкода не уходят за край круглого экрана.
                Image(
                    bitmap.asImageBitmap(), "Код карты ${card.name}",
                    Modifier.padding(top = 6.dp).size(width = screen * 0.8f, height = screen * 0.8f * 430f / 900f),
                    contentScale = ContentScale.FillBounds,
                )
                BasicText(
                    card.cardNumber.ifBlank { card.barcodeValue }, Modifier.padding(top = 4.dp),
                    style = TextStyle(color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun WearDisplayPolicy(settings: WearUiSettings) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, settings) {
        if (activity == null) return@DisposableEffect onDispose { }
        val window = activity.window
        val previous = window.attributes.screenBrightness
        if (settings.maximumBrightness) window.attributes = window.attributes.apply { screenBrightness = 1f }
        // «Экран не гаснет» держит общий таймер в WearRoot, здесь только яркость.
        onDispose { window.attributes = window.attributes.apply { screenBrightness = previous } }
    }
}

/** Стилизованный значок кода: мини-штрихкод или мини-QR, нарисованный линиями. */
@Composable
private fun CodeGlyph(square: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val white = Color.White
        if (square) {
            val cell = size.minDimension / 7f
            // Три «глазка» QR и пара точек в центре.
            listOf(Offset(0f, 0f), Offset(4f * cell, 0f), Offset(0f, 4f * cell)).forEach { o ->
                drawRect(white, o, Size(3f * cell, 3f * cell), style = Stroke(width = cell * 0.9f))
            }
            drawRect(white, Offset(4.6f * cell, 4.6f * cell), Size(cell * 1.4f, cell * 1.4f))
        } else {
            val widths = listOf(2f, 1f, 3f, 1f, 2f, 1f, 1f, 3f, 1f, 2f)
            val unit = size.width / (widths.sum() + widths.size - 1)
            var x = 0f
            widths.forEach { w ->
                drawRect(white, Offset(x, 0f), Size(w * unit, size.height))
                x += (w + 1f) * unit
            }
        }
    }
}
