package ru.cardly.mobile.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.AppSettings
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.Panel
import ru.cardly.mobile.ui.PanelDivider
import ru.cardly.mobile.ui.PanelRow
import ru.cardly.mobile.ui.PanelSwitch
import ru.cardly.mobile.ui.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    version: String,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupFolder: String,
    onPickBackupFolder: () -> Unit,
    payCardPath: String?,
    onPayCardCamera: () -> Unit,
    onPayCardGallery: () -> Unit,
    onPayCardRemove: () -> Unit,
) {
    var payMenu by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (licensesOpen) androidx.compose.material3.AlertDialog(
        onDismissRequest = { licensesOpen = false },
        title = { Text("Лицензии") },
        text = {
            Text(
                "Cardly $version — by Alex (poluno4eff).\n" +
                    "Свободная программа, GNU GPL v3. Распространяется «как есть».\n\n" +
                    "Экран оплаты и дизайны карт на часах — Mir Pay Wallet (the-dise), GPL-3.0.\n" +
                    "Распознавание текста — Tesseract, Leptonica (Apache 2.0).\n" +
                    "Штрихкоды — ZXing (Apache 2.0).\n" +
                    "Шрифт логотипа — Pirata One (SIL OFL 1.1).\n" +
                    "AndroidX, Compose, Room, Kotlin — Apache 2.0.\n" +
                    "Сканеры кодов и документов — Google ML Kit.",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                licensesOpen = false
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/poluno4eff-sketch/Cardly")))
                }
            }) { Text("Исходный код") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { licensesOpen = false }) { Text("Закрыть") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeTopPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            Panel {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(Icons.Filled.SettingsBrightness, "Как в системе", settings.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) {
                        onChange(settings.copy(themeMode = ThemeMode.SYSTEM))
                    }
                    ThemeChoice(Icons.Filled.LightMode, "Светлая", settings.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) {
                        onChange(settings.copy(themeMode = ThemeMode.LIGHT))
                    }
                    ThemeChoice(Icons.Filled.DarkMode, "Тёмная", settings.themeMode == ThemeMode.DARK, Modifier.weight(1f)) {
                        onChange(settings.copy(themeMode = ThemeMode.DARK))
                    }
                }
                PanelDivider()
                PanelSwitch(Icons.Filled.Contrast, Color(0xFF9AA4B5), "Чёрный фон", null, settings.amoled) {
                    onChange(settings.copy(amoled = it))
                }
                PanelDivider()
                PanelSwitch(Icons.Filled.ScreenLockPortrait, Brand.cyan, "Только вертикально", null, settings.lockPortrait) {
                    onChange(settings.copy(lockPortrait = it))
                }
            }

            Spacer(Modifier.height(14.dp))
            Panel {
                PanelSwitch(Icons.Filled.Image, Brand.violet, "Фото карты над кодом", null, settings.showCardImage) {
                    onChange(settings.copy(showCardImage = it))
                }
                PanelDivider()
                PanelSwitch(Icons.Filled.WbSunny, Color(0xFFFFB020), "Максимальная яркость", null, settings.maximumBrightness) {
                    onChange(settings.copy(maximumBrightness = it))
                }
                PanelDivider()
                PanelRow(Icons.Filled.Brightness6, Color(0xFF4CC9F0), "Экран не гаснет, минут", null, trailing = null)
                Row(
                    Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "Выкл", 1 to "1", 3 to "3", 5 to "5", -1 to "Всегда").forEach { (minutes, title) ->
                        val selected = settings.keepScreenMinutes == minutes
                        Box(
                            Modifier.weight(if (minutes == 0 || minutes == -1) 1.6f else 1f).height(36.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Brand.gradient else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
                                .clickable { onChange(settings.copy(keepScreenMinutes = minutes)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(title, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                PanelDivider()
                PanelSwitch(Icons.Filled.Nfc, Brand.danger, "Отключать NFC при показе карты", null, settings.nfcProtection) {
                    onChange(settings.copy(nfcProtection = it))
                }
                PanelDivider()
                PanelSwitch(Icons.Filled.SwapHoriz, Brand.cyan, "Стрелки переключения карт", null, settings.cardSwitcher) {
                    onChange(settings.copy(cardSwitcher = it))
                }
            }

            Spacer(Modifier.height(14.dp))
            Panel {
                PanelRow(Icons.Filled.Watch, Brand.blue, "Синхронизировать часы", "Только скидочные карты", onClick = onSync, trailing = {
                    Icon(Icons.Filled.Sync, null, tint = MaterialTheme.colorScheme.primary)
                })
                PanelDivider()
                Box {
                    PanelRow(Icons.Filled.CreditCard, Brand.violet, "Карта оплаты на часах", if (payCardPath == null) "Дизайн выбирается на часах" else "Своя картинка", onClick = { payMenu = true }, trailing = {
                        val preview by ru.cardly.mobile.data.rememberImage(payCardPath, 240)
                        preview?.let {
                            androidx.compose.foundation.Image(
                                it.asImageBitmap(), null,
                                Modifier.width(52.dp).height(33.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } ?: Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                    ru.cardly.mobile.ui.CardlyMenu(payMenu, { payMenu = false }) {
                        ru.cardly.mobile.ui.MenuRow("Сфотографировать карту", Icons.Filled.CameraAlt) { payMenu = false; onPayCardCamera() }
                        ru.cardly.mobile.ui.MenuRow("Картинка из галереи", Icons.Filled.Image) { payMenu = false; onPayCardGallery() }
                        if (payCardPath != null) ru.cardly.mobile.ui.MenuRow("Убрать свою картинку", Icons.Filled.Delete, danger = true) { payMenu = false; onPayCardRemove() }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Panel {
                PanelRow(Icons.Filled.Schedule, Color(0xFF2ECC71), "Автокопия", null, trailing = null)
                Row(
                    Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "Выкл", 1 to "Каждый день", 7 to "Раз в неделю").forEach { (days, title) ->
                        val selected = settings.autoBackupDays == days
                        Box(
                            Modifier.weight(if (days == 0) 0.8f else 1.3f).height(36.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Brand.gradient else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
                                .clickable { onChange(settings.copy(autoBackupDays = days)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(title, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                PanelDivider()
                PanelRow(Icons.Filled.Folder, Color(0xFFFFB020), "Папка копий", backupFolder, onClick = onPickBackupFolder)
                PanelDivider()
                PanelRow(Icons.Filled.Save, Color(0xFF2ECC71), "Сохранить копию", null, onClick = onExport)
                PanelDivider()
                PanelRow(Icons.Filled.Restore, Color(0xFFFF8A3D), "Восстановить из копии", null, onClick = onImport)
            }

            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { licensesOpen = true }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Cardly $version", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("by Alex", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "poluno4eff", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:poluno4eff@mail.ru")))
                        }
                    }.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemeChoice(icon: ImageVector, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand.gradient else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        Icon(icon, null, tint = color)
        Text(title, Modifier.padding(top = 6.dp), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
