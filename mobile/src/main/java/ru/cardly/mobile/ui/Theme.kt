package ru.cardly.mobile.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DisplayMode { LARGE, GRID }
enum class SortMode { FAVORITES, NAME, RECENT, ADDED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = true,
    val maximumBrightness: Boolean = true,
    /** Сколько минут экран не гаснет при показе карты: 0 — как обычно, -1 — пока открыта карта. Касание продлевает. */
    val keepScreenMinutes: Int = 3,
    val preventLock: Boolean = false,
    val nfcProtection: Boolean = false,
    val cardSwitcher: Boolean = true,
    /** Показывать фото карты над штрихкодом при показе карты. */
    val showCardImage: Boolean = true,
    /** Не поворачивать экран — всегда вертикально. */
    val lockPortrait: Boolean = true,
    /** Автокопия: 0 — выключена, 1 — каждый день, 7 — каждую неделю. */
    val autoBackupDays: Int = 7,
    val cardsDisplay: DisplayMode = DisplayMode.LARGE,
    val cardsSort: SortMode = SortMode.FAVORITES,
    val contactsDisplay: DisplayMode = DisplayMode.LARGE,
    val contactsSort: SortMode = SortMode.FAVORITES,
    /** В списке визиток показывать фото визитки (true) или распознанные данные (false). */
    val contactsShowPhoto: Boolean = false,
) {
    val keepScreenOn: Boolean get() = keepScreenMinutes != 0
}

class CardlyPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("cardly_settings", Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, default.name).orEmpty()) }.getOrDefault(default)

    fun load() = AppSettings(
        themeMode = enumOf("theme", ThemeMode.SYSTEM),
        amoled = preferences.getBoolean("amoled", true),
        maximumBrightness = preferences.getBoolean("maximumBrightness", true),
        keepScreenMinutes = preferences.getInt("keepScreenMinutes", if (preferences.getBoolean("keepScreenOn", true)) 3 else 0),
        preventLock = preferences.getBoolean("preventLock", false),
        nfcProtection = preferences.getBoolean("nfcProtection", false),
        cardSwitcher = preferences.getBoolean("cardSwitcher", true),
        showCardImage = preferences.getBoolean("showCardImage", true),
        lockPortrait = preferences.getBoolean("lockPortrait", true),
        autoBackupDays = preferences.getInt("autoBackupDays", 7),
        cardsDisplay = enumOf("cardsDisplay", DisplayMode.LARGE),
        cardsSort = enumOf("cardsSort", SortMode.FAVORITES),
        contactsDisplay = enumOf("contactsDisplay", DisplayMode.LARGE),
        contactsSort = enumOf("contactsSort", SortMode.FAVORITES),
        contactsShowPhoto = preferences.getBoolean("contactsShowPhoto", false),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString("theme", settings.themeMode.name)
            .putBoolean("amoled", settings.amoled)
            .putBoolean("maximumBrightness", settings.maximumBrightness)
            .putInt("keepScreenMinutes", settings.keepScreenMinutes)
            .putBoolean("preventLock", settings.preventLock)
            .putBoolean("nfcProtection", settings.nfcProtection)
            .putBoolean("cardSwitcher", settings.cardSwitcher)
            .putBoolean("showCardImage", settings.showCardImage)
            .putBoolean("lockPortrait", settings.lockPortrait)
            .putInt("autoBackupDays", settings.autoBackupDays)
            .putString("cardsDisplay", settings.cardsDisplay.name)
            .putString("cardsSort", settings.cardsSort.name)
            .putString("contactsDisplay", settings.contactsDisplay.name)
            .putString("contactsSort", settings.contactsSort.name)
            .putBoolean("contactsShowPhoto", settings.contactsShowPhoto)
            .apply()
    }
}

/** Фирменные цвета Cardly. */
object Brand {
    val blue = Color(0xFF176BFF)
    val cyan = Color(0xFF05CAE8)
    val violet = Color(0xFF7840FF)
    val gradient = Brush.linearGradient(listOf(blue, cyan, violet))
    /** Градиент логотипа — те же цвета, что у кнопки «+». */
    val titleGradient = Brush.horizontalGradient(listOf(blue, cyan, violet))
    /** Готический шрифт логотипа (Pirata One, лицензия SIL OFL), только буквы слова Cardly. */
    val logoFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(ru.cardly.mobile.R.font.cardly_logo))
    val danger = Color(0xFFFF5A5F)
    val star = Color(0xFFFFD24A)
}

@Composable
fun CardlyTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) {
        val amoled = settings.amoled
        darkColorScheme(
            background = if (amoled) Color.Black else Color(0xFF101218),
            surface = if (amoled) Color.Black else Color(0xFF101218),
            surfaceVariant = if (amoled) Color(0xFF1A1A1C) else Color(0xFF242733),
            surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0C0E13),
            surfaceContainerLow = if (amoled) Color(0xFF0D0D0F) else Color(0xFF151821),
            surfaceContainer = if (amoled) Color(0xFF131315) else Color(0xFF1A1D26),
            surfaceContainerHigh = if (amoled) Color(0xFF1A1A1D) else Color(0xFF20232D),
            surfaceContainerHighest = if (amoled) Color(0xFF232327) else Color(0xFF282B36),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB4B8C2),
            primary = Color(0xFF3D9BFF),
            onPrimary = Color.White,
            secondary = Color(0xFF35D8E6),
            outline = Color(0xFF3A3D46),
            outlineVariant = Color(0xFF26282E),
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF4F6FA),
            surface = Color(0xFFF4F6FA),
            surfaceVariant = Color(0xFFE6EAF2),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFBFD),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFEDEFF4),
            onBackground = Color(0xFF12141A),
            onSurface = Color(0xFF12141A),
            onSurfaceVariant = Color(0xFF5B606B),
            primary = Color(0xFF006BD6),
            onPrimary = Color.White,
            secondary = Color(0xFF007D89),
            outline = Color(0xFFCDD2DC),
            outlineVariant = Color(0xFFE3E6EC),
        )
    }
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
            content = content,
        )
    }
}
