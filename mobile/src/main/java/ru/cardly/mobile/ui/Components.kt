package ru.cardly.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Кнопка верхней панели: иконка в мягком круге. */
@Composable
fun HeaderButton(icon: ImageVector, description: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .then(
                if (highlighted) Modifier.background(Brand.gradient)
                else Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, Modifier.size(23.dp),
            tint = if (highlighted) Color.White else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Выпадающее меню в едином стиле: скруглённое, на приподнятом фоне. */
@Composable
fun CardlyMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        offset = DpOffset(0.dp, 6.dp),
        content = content,
    )
}

@Composable
fun MenuHeader(text: String) {
    Text(
        text, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun MenuRow(
    text: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) Brand.danger else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = color, fontSize = 16.sp) },
        leadingIcon = if (icon != null) ({ Icon(icon, null, tint = if (danger) Brand.danger else MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        trailingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
        onClick = onClick,
        modifier = Modifier.widthIn(min = 220.dp),
    )
}

/** Карточка-группа для настроек и форм. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Иконка в цветном скруглённом квадрате. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.58f).dp)) }
}

@Composable
fun PanelRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun PanelSwitch(icon: ImageVector, tint: Color, title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    PanelRow(icon, tint, title, subtitle, onClick = { onChecked(!checked) }) {
        Switch(
            checked, onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun PanelDivider() {
    Box(
        Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Поле ввода в стиле Cardly. */
@Composable
fun CardlyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    supportingText: String? = null,
    isError: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = if (leadingIcon != null) ({ Icon(leadingIcon, null) }) else null,
        trailingIcon = trailing,
        supportingText = if (supportingText != null) ({ Text(supportingText) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

/** Крупная кнопка действия с иконкой (используется в листах «Добавить» и в визитках). */
@Composable
fun ActionTile(icon: ImageVector, title: String, subtitle: String?, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 44)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Пилюля-кнопка с градиентом (главное действие экрана). */
@Composable
fun GradientPill(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Brand.gradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VerticalSpace(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun ChipButton(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Отступ сверху под строку состояния и вырез камеры. Берём больший из системного отступа
 * и высоты строки состояния устройства — после возврата из камеры/сканера система иногда
 * на мгновение сообщает нулевой отступ, и шапка уезжала под строку состояния.
 */
@Suppress("DiscouragedApi", "InternalInsetResource")
fun Modifier.safeTopPadding(): Modifier = composed {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val deviceStatusBar = androidx.compose.runtime.remember {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
    val insetTop = androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(density)
    val top = with(density) { maxOf(insetTop, deviceStatusBar).toDp() }
    this.padding(top = top)
}

/** Тонкая светлая рамка вокруг фото карты/визитки — чтобы тёмная картинка не сливалась с чёрным фоном. */
fun Modifier.photoFrame(radius: androidx.compose.ui.unit.Dp): Modifier = composed {
    val color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
    this.border(1.dp, color, RoundedCornerShape(radius))
}
