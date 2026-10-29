package ru.cardly.wear

/*
 * Экран оплаты Cardly на часах.
 * Идея, настройки и дизайны карт — из проекта Mir Pay Wallet (https://github.com/the-dise/Mir-Pay-Wallet,
 * автор the-dise, GPL-3.0). Пока идёт отсчёт, экран не гаснет — Mir Pay успевает провести оплату по NFC.
 */

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

// ---------- Дизайны карт оплаты ----------

enum class PayCard(@DrawableRes val image: Int, val title: String, val color: Long) {
    SECRET_GARDEN(R.drawable.pay_card_secret_garden, "Лужайка", 0xFF0F9F5A),
    SWISS_PLUM(R.drawable.pay_card_swiss_plum, "Слива", 0xFF594FB2),
    LIGHT_CORAL(R.drawable.pay_card_light_coral, "Коралл", 0xFFF08080),
    SPICED_NECTARINE(R.drawable.pay_card_spiced_nectarine, "Нектарин", 0xFFFFB872),
    STARGATE_SHIMMER(R.drawable.pay_card_stargate_shimmer, "Мерцание", 0xFF6F7DFF),
    VEGA_VIOLET(R.drawable.pay_card_vega_violet, "Фиалка", 0xFF8F5BD6),
    WANDERLUST(R.drawable.pay_card_wounder_list, "Странник", 0xFF3E7BD9),
    /** Своя картинка, присланная с телефона (фото своей карты). */
    CUSTOM(R.drawable.pay_card_secret_garden, "Своя карта", 0xFF3D4A66),
}

/** Своя картинка карты оплаты, присланная с телефона. */
object CustomPayCard {
    const val ACTION_UPDATED = "ru.cardly.wear.PAY_CARD_UPDATED"
    fun file(context: Context) = java.io.File(context.filesDir, "pay_card_custom.jpg")
    fun exists(context: Context) = file(context).let { it.isFile && it.length() > 0 }
    fun load(context: Context): android.graphics.Bitmap? =
        runCatching { android.graphics.BitmapFactory.decodeFile(file(context).absolutePath) }.getOrNull()
}

/** Картинка выбранной карты: своя (если есть) или встроенный дизайн. */
@Composable
private fun PayCardImage(card: PayCard, modifier: Modifier, revision: Int = 0) {
    val context = LocalContext.current
    val custom = remember(card, revision) { if (card == PayCard.CUSTOM) CustomPayCard.load(context) else null }
    if (custom != null) Image(
        custom.asImageBitmap(), card.title, modifier, contentScale = ContentScale.Crop,
    ) else Image(painterResource(if (card == PayCard.CUSTOM) PayCard.SECRET_GARDEN.image else card.image), card.title, modifier, contentScale = ContentScale.Crop)
}

/** Силы вибрации — длительность импульса в миллисекундах. */
val VIBRATION_LEVELS = listOf(
    25 to "Нежная", 50 to "Мягкая", 75 to "По умолчанию", 100 to "Прочная",
    150 to "Энергичная", 200 to "Мощная", 300 to "Экстремальная",
)
val TIMER_PRESETS = listOf(10, 15, 20, 30, 45, 60, 90)

/** Настройки оплаты хранятся прямо на часах. */
class PaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("pay_settings", Context.MODE_PRIVATE)
    var timerSeconds: Int
        get() = prefs.getInt("timer", 15)
        set(value) = prefs.edit().putInt("timer", value).apply()
    var card: PayCard
        get() = runCatching { PayCard.valueOf(prefs.getString("card", PayCard.SECRET_GARDEN.name)!!) }.getOrDefault(PayCard.SECRET_GARDEN)
        set(value) = prefs.edit().putString("card", value.name).apply()
    var haptic: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(value) = prefs.edit().putBoolean("haptic", value).apply()
    var everySecond: Boolean
        get() = prefs.getBoolean("everySecond", false)
        set(value) = prefs.edit().putBoolean("everySecond", value).apply()
    var vibrationMs: Int
        get() = prefs.getInt("vibrationMs", 75)
        set(value) = prefs.edit().putInt("vibrationMs", value).apply()
    var ring: Boolean
        get() = prefs.getBoolean("ring", true)
        set(value) = prefs.edit().putBoolean("ring", value).apply()
}

internal fun Context.vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

internal fun Vibrator.pulse(ms: Int) = runCatching { vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)) }

// ---------- Экран оплаты ----------

/**
 * Карта и обратный отсчёт. Сам таймер общий для всех экранов и живёт в WearRoot:
 * сюда приходит только остаток [left] из [total] секунд.
 */
@Composable
fun PayScreen(settings: PaySettings, left: Int, total: Int) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { revision++ }
        }
        val filter = android.content.IntentFilter(CustomPayCard.ACTION_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val card = remember(revision) { settings.card }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(card.color), Color.Black))),
    ) {
        val size = if (maxWidth < maxHeight) maxWidth else maxHeight
        // Кольцо оставшегося времени по краю круглого экрана (можно выключить в настройках).
        if (settings.ring) Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val stroke = 4.dp.toPx()
            val diameter = minOf(this.size.width, this.size.height) - stroke
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            drawArc(
                Color.White, -90f, 360f * left / total.coerceAtLeast(1), false, topLeft, Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = size * 0.12f), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_pay_wallet), null, Modifier.size(16.dp))
            BasicText(" Оплата", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
        PayCardImage(
            card, revision = revision,
            modifier = Modifier.align(Alignment.Center).width(size * 0.72f).aspectRatio(512f / 323f)
                .shadow(16.dp, RoundedCornerShape(8.dp), clip = true, spotColor = Color.Black),
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.1f)) { Counter(left) }
    }
}

/** Двузначный счётчик с анимацией смены цифр. */
@Composable
fun Counter(value: Int, color: Color = Color.White, fontSize: androidx.compose.ui.unit.TextUnit = 22.sp) {
    val text = String.format("%02d", value.coerceIn(0, 99))
    Row {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
                label = "digit$index",
            ) { digit ->
                BasicText(digit.toString(), style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// ---------- Настройки оплаты ----------

@Composable
fun PaySettingsScreen(settings: PaySettings, version: String, active: Boolean) {
    val context = LocalContext.current
    var timer by remember { mutableIntStateOf(settings.timerSeconds) }
    var card by remember { mutableStateOf(settings.card) }
    var haptic by remember { mutableStateOf(settings.haptic) }
    var every by remember { mutableStateOf(settings.everySecond) }
    var vibration by remember { mutableIntStateOf(settings.vibrationMs) }
    var ring by remember { mutableStateOf(settings.ring) }
    // При каждом показе экрана перечитываем настройки: карта могла смениться с телефона.
    LaunchedEffect(active) {
        if (active) {
            timer = settings.timerSeconds; card = settings.card; haptic = settings.haptic
            every = settings.everySecond; vibration = settings.vibrationMs; ring = settings.ring
        }
    }
    var choosingCard by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    // «Назад» из вложенного экрана возвращает в настройки.
    androidx.activity.compose.BackHandler(enabled = active && (showAbout || choosingCard)) { showAbout = false; choosingCard = false }
    LaunchedEffect(active) { if (!active) { showAbout = false; choosingCard = false } }
    val focus = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val rotaryScope = androidx.compose.runtime.rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Фокус для безеля запрашиваем здесь: список внутри BoxWithConstraints появляется позже.
        LaunchedEffect(choosingCard, showAbout, active) {
            if (active) repeat(10) { if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect; delay(50) }
        }
        val vertical = maxHeight * 0.18f
        LazyColumn(
            Modifier.fillMaxSize()
                .onRotaryScrollEvent { event ->
                    rotaryScope.launch { listState.animateScrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focus).focusable(),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = vertical, bottom = vertical),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showAbout) {
                item { Header("Cardly $version") }
                item {
                    BasicText(
                        "by Alex · poluno4eff\n\nСвободная программа\nGNU GPL v3, «как есть»\n\ngithub.com/poluno4eff-sketch/Cardly\n\nЭкран оплаты и дизайны карт:\nMir Pay Wallet (the-dise)",
                        Modifier.padding(horizontal = 8.dp).clickable { showAbout = false },
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center),
                    )
                }
            } else if (choosingCard) {
                item { Header("Выберите карту") }
                val options = PayCard.entries.filter { it != PayCard.CUSTOM || CustomPayCard.exists(context) }
                items(options, key = { it.name }) { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(if (option == card) Color(0xFF2A3350) else Color(0xFF1C1C20))
                            .clickable { card = option; settings.card = option; choosingCard = false }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PayCardImage(option, Modifier.width(46.dp).aspectRatio(512f / 323f).clip(RoundedCornerShape(5.dp)))
                        BasicText(option.title, Modifier.padding(start = 10.dp), style = TextStyle(color = Color.White, fontSize = 15.sp))
                    }
                }
            } else {
                item { Header("Настройки") }
                item {
                    SettingChip("Отсчёт времени", "$timer с") {
                        val next = TIMER_PRESETS.firstOrNull { it > timer } ?: TIMER_PRESETS.first()
                        timer = next; settings.timerSeconds = next
                    }
                }
                item { ToggleChip("Кольцо таймера", null, ring) { ring = it; settings.ring = it } }
                item { SettingChip("Карта", card.title, previewCard = card) { choosingCard = true } }
                item { Header("Вибрация") }
                item { ToggleChip("В начале и в конце", null, haptic) { haptic = it; settings.haptic = it } }
                item { ToggleChip("Каждую секунду", null, every) { every = it; settings.everySecond = it } }
                item {
                    val title = VIBRATION_LEVELS.firstOrNull { it.first == vibration }?.second ?: "$vibration мс"
                    SettingChip("Интенсивность", title) {
                        val index = VIBRATION_LEVELS.indexOfFirst { it.first == vibration }
                        val next = VIBRATION_LEVELS[(index + 1) % VIBRATION_LEVELS.size].first
                        vibration = next; settings.vibrationMs = next
                    }
                }
                item { SettingChip("О приложении", "Cardly $version") { showAbout = true } }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    BasicText(
        text, Modifier.padding(top = 6.dp, bottom = 2.dp),
        style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
    )
}

@Composable
private fun SettingChip(title: String, value: String, previewCard: PayCard? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF202128))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            BasicText(value, style = TextStyle(color = Color(0xFF8AB4FF), fontSize = 12.sp))
        }
        if (previewCard != null) PayCardImage(previewCard, Modifier.width(34.dp).aspectRatio(512f / 323f).clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun ToggleChip(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF202128))
            .clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            if (subtitle != null) BasicText(subtitle, style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp))
        }
        // Переключатель
        Box(
            Modifier.width(34.dp).height(20.dp).clip(RoundedCornerShape(10.dp))
                .background(if (checked) Color(0xFF3D7BFF) else Color(0xFF45464F)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.padding(2.dp).size(16.dp).clip(CircleShape).background(Color.White)) }
    }
}

/** Точки внизу: на какой странице находимся (Карты · Оплата · Настройки). */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            Box(
                Modifier.size(if (index == current) 7.dp else 5.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)),
            )
        }
    }
}

// ---------- Кнопка оплаты на часах и компликация ----------

/**
 * Когда открывается Google Wallet (например, кнопкой оплаты на часах), закрываем его
 * и открываем экран оплаты Cardly — оплата пройдёт через Mir Pay. Включается в «Спец. возможностях».
 */
class PayAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.google.android.apps.walletnfcrel") return
        performGlobalAction(GLOBAL_ACTION_BACK)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    override fun onInterrupt() = Unit
}

/** Компликация на циферблат: одно касание — экран оплаты. */
class PayComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.MONOCHROMATIC_IMAGE) data(null) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return data(intent)
    }

    private fun data(tap: PendingIntent?) = MonochromaticImageComplicationData.Builder(
        MonochromaticImage.Builder(Icon.createWithResource(applicationContext, R.drawable.ic_pay_wallet)).build(),
        PlainComplicationText.Builder(getText(R.string.pay_complication_label)).build(),
    ).setTapAction(tap).build()
}
