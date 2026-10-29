package ru.cardly.mobile.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import java.util.Locale
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card
import ru.cardly.mobile.ui.safeTopPadding
import ru.cardly.mobile.ui.ActionTile
import ru.cardly.mobile.ui.AppSettings
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyMenu
import ru.cardly.mobile.ui.DisplayMode
import ru.cardly.mobile.ui.HeaderButton
import ru.cardly.mobile.ui.MenuHeader
import ru.cardly.mobile.ui.MenuRow
import ru.cardly.mobile.ui.SortMode

enum class HomeTab { CARDS, CONTACTS }

enum class AddAction { SCAN_CODE, CODE_FROM_IMAGE, MANUAL_CARD, SCAN_CONTACT, CONTACT_FROM_GALLERY, CONTACT_QR, MANUAL_CONTACT }

fun List<Card>.filteredSorted(query: String, sort: SortMode): List<Card> {
    val q = query.trim()
    val filtered = if (q.isEmpty()) this else filter {
        it.name.contains(q, true) || it.cardNumber.contains(q, true) || it.barcodeValue.contains(q, true) || it.note.contains(q, true)
    }
    return when (sort) {
        SortMode.FAVORITES -> filtered.sortedWith(compareByDescending<Card> { it.favorite }.thenBy { it.sortOrder })
        SortMode.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
        SortMode.RECENT -> filtered.sortedByDescending { it.lastUsedAt ?: 0L }
        SortMode.ADDED -> filtered.sortedByDescending { it.createdAt }
    }
}

fun List<BusinessCard>.filteredSortedContacts(query: String, sort: SortMode): List<BusinessCard> {
    val filtered = filter { it.matches(query) }
    return when (sort) {
        SortMode.FAVORITES -> filtered.sortedWith(compareByDescending<BusinessCard> { it.favorite }.thenByDescending { it.createdAt })
        SortMode.NAME -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
        SortMode.RECENT -> filtered.sortedByDescending { it.lastUsedAt ?: 0L }
        SortMode.ADDED -> filtered.sortedByDescending { it.createdAt }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    cards: List<Card>?,
    contacts: List<BusinessCard>?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
    onEditCard: (Card) -> Unit,
    onToggleFavoriteCard: (Card) -> Unit,
    onDeleteCard: (Card) -> Unit,
    onOpenContact: (BusinessCard) -> Unit,
    onEditContact: (BusinessCard) -> Unit,
    onToggleFavoriteContact: (BusinessCard) -> Unit,
    onDeleteContact: (BusinessCard) -> Unit,
    onAdd: (AddAction) -> Unit,
    onOpenSettings: () -> Unit,
    onSync: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    val isCards = tab == HomeTab.CARDS
    val display = if (isCards) settings.cardsDisplay else settings.contactsDisplay
    val sort = if (isCards) settings.cardsSort else settings.contactsSort

    LaunchedEffect(tab) { query = ""; searchOpen = false }

    Column(Modifier.fillMaxSize().safeTopPadding().navigationBarsPadding()) {
        // ---------- Верхняя строка: как в утверждённом дизайне ----------
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cardly",
                style = TextStyle(brush = Brand.titleGradient, fontSize = 36.sp, fontFamily = Brand.logoFont),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderButton(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "Поиск") {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }
                if (isCards) HeaderButton(
                    if (display == DisplayMode.LARGE) Icons.Filled.GridView else Icons.Filled.ViewAgenda,
                    if (display == DisplayMode.LARGE) "Показать сеткой" else "Показать крупно",
                ) {
                    val next = if (display == DisplayMode.LARGE) DisplayMode.GRID else DisplayMode.LARGE
                    onSettingsChange(settings.copy(cardsDisplay = next))
                } else Box {
                    HeaderButton(
                        if (display == DisplayMode.LARGE) Icons.Filled.GridView else Icons.Filled.ViewAgenda, "Вид",
                    ) { viewMenu = true }
                    CardlyMenu(viewMenu, { viewMenu = false }) {
                        MenuHeader("На плитке")
                        MenuRow("Данные визитки", Icons.Filled.Notes, selected = !settings.contactsShowPhoto) {
                            viewMenu = false; onSettingsChange(settings.copy(contactsShowPhoto = false))
                        }
                        MenuRow("Фото визитки", Icons.Filled.Image, selected = settings.contactsShowPhoto) {
                            viewMenu = false; onSettingsChange(settings.copy(contactsShowPhoto = true))
                        }
                        MenuHeader("Размер")
                        MenuRow("Крупно", Icons.Filled.ViewAgenda, selected = display == DisplayMode.LARGE) {
                            viewMenu = false; onSettingsChange(settings.copy(contactsDisplay = DisplayMode.LARGE))
                        }
                        MenuRow("Сеткой", Icons.Filled.GridView, selected = display == DisplayMode.GRID) {
                            viewMenu = false; onSettingsChange(settings.copy(contactsDisplay = DisplayMode.GRID))
                        }
                    }
                }
                Box {
                    HeaderButton(Icons.AutoMirrored.Filled.Sort, "Сортировка") { sortMenu = true }
                    CardlyMenu(sortMenu, { sortMenu = false }) {
                        MenuHeader("Сортировка")
                        listOf(
                            SortMode.FAVORITES to "Сначала избранные",
                            SortMode.NAME to "По названию",
                            SortMode.RECENT to "Недавно открытые",
                            SortMode.ADDED to "Недавно добавленные",
                        ).forEach { (mode, title) ->
                            MenuRow(title, selected = sort == mode) {
                                sortMenu = false
                                onSettingsChange(if (isCards) settings.copy(cardsSort = mode) else settings.copy(contactsSort = mode))
                            }
                        }
                    }
                }
                HeaderButton(Icons.Filled.Add, if (isCards) "Добавить карту" else "Добавить визитку", highlighted = true) { addSheet = true }
                Box {
                    HeaderButton(Icons.Filled.MoreVert, "Меню") { appMenu = true }
                    CardlyMenu(appMenu, { appMenu = false }) {
                        MenuRow("Синхронизировать часы", Icons.Filled.Sync) { appMenu = false; onSync() }
                        MenuRow("Настройки", Icons.Filled.Settings) { appMenu = false; onOpenSettings() }
                    }
                }
            }
        }

        AnimatedVisibility(searchOpen) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).focusRequester(focus),
                placeholder = { Text(if (isCards) "Название или номер карты" else "Имя, компания, телефон") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Очистить") } },
                singleLine = true, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }

        // ---------- Список ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isCards) {
                val list = cards
                if (list != null) {
                    val visible = remember(list, query, sort) { list.filteredSorted(query, sort) }
                    if (visible.isEmpty()) EmptyState(
                        title = if (query.isNotBlank()) "Ничего не найдено" else "Добавьте первую карту",
                        text = if (query.isNotBlank()) "Измените запрос" else "Отсканируйте штрихкод — и карта всегда под рукой",
                        actionText = if (query.isBlank()) "Сканировать код" else null,
                        onAction = { onAdd(AddAction.SCAN_CODE) },
                    ) else TileGrid(display, visible.size, key = { visible[it].id }) { index, tileModifier ->
                        val card = visible[index]
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            CardTile(
                                card, tileModifier,
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onOpenCard(card, visible) },
                                onLongClick = { menu = true },
                            )
                            CardlyMenu(menu, { menu = false }) {
                                MenuRow("Изменить", Icons.Filled.Edit) { menu = false; onEditCard(card) }
                                MenuRow(if (card.favorite) "Убрать из избранного" else "В избранное", if (card.favorite) Icons.Filled.StarBorder else Icons.Filled.Star) {
                                    menu = false; onToggleFavoriteCard(card)
                                }
                                MenuRow("Удалить", Icons.Filled.Delete, danger = true) { menu = false; onDeleteCard(card) }
                            }
                        }
                    }
                }
            } else {
                val list = contacts
                if (list != null) {
                    val visible = remember(list, query, sort) { list.filteredSortedContacts(query, sort) }
                    if (visible.isEmpty()) EmptyState(
                        title = if (query.isNotBlank()) "Ничего не найдено" else "Пока нет визиток",
                        text = if (query.isNotBlank()) "Измените запрос" else "Сфотографируйте визитку — телефоны, почта и сайт распознаются сами",
                        actionText = if (query.isBlank()) "Сфотографировать визитку" else null,
                        onAction = { onAdd(AddAction.SCAN_CONTACT) },
                    ) else TileGrid(display, visible.size, key = { visible[it].id }) { index, tileModifier ->
                        val contact = visible[index]
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            ContactTile(
                                contact, tileModifier, compact = display == DisplayMode.GRID, showPhoto = settings.contactsShowPhoto,
                                onClick = { onOpenContact(contact) },
                                onLongClick = { menu = true },
                            )
                            CardlyMenu(menu, { menu = false }) {
                                MenuRow("Изменить", Icons.Filled.Edit) { menu = false; onEditContact(contact) }
                                MenuRow(if (contact.favorite) "Убрать из избранного" else "В избранное", if (contact.favorite) Icons.Filled.StarBorder else Icons.Filled.Star) {
                                    menu = false; onToggleFavoriteContact(contact)
                                }
                                MenuRow("Удалить", Icons.Filled.Delete, danger = true) { menu = false; onDeleteContact(contact) }
                            }
                        }
                    }
                }
            }
        }

        // ---------- Переключатель Карты / Визитки ----------
        // Отдельно от плиток, с отступом, чтобы не задеть случайно; приложение всегда открывается на картах.
        TabSwitch(tab, onTab)
    }

    if (addSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { addSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isCards) "Новая карта" else "Новая визитка",
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                fun pick(action: AddAction) { addSheet = false; onAdd(action) }
                if (isCards) {
                    ActionTile(Icons.Filled.QrCodeScanner, "Сканировать штрихкод", "Наведите камеру на код карты", Brand.blue) { pick(AddAction.SCAN_CODE) }
                    ActionTile(Icons.Filled.Image, "Код с фото или скриншота", "Найду штрихкод на картинке", Brand.violet) { pick(AddAction.CODE_FROM_IMAGE) }
                    ActionTile(Icons.Filled.Keyboard, "Ввести номер вручную", null, Color(0xFF8E96A3)) { pick(AddAction.MANUAL_CARD) }
                } else {
                    ActionTile(Icons.Filled.DocumentScanner, "Сфотографировать визитку", "Можно обе стороны, края обрежутся сами", Brand.blue) { pick(AddAction.SCAN_CONTACT) }
                    ActionTile(Icons.Filled.Image, "Фото из галереи", null, Brand.violet) { pick(AddAction.CONTACT_FROM_GALLERY) }
                    ActionTile(Icons.Filled.QrCodeScanner, "QR-код визитки", "Электронная визитка vCard", Brand.cyan) { pick(AddAction.CONTACT_QR) }
                    ActionTile(Icons.Filled.Keyboard, "Заполнить вручную", null, Color(0xFF8E96A3)) { pick(AddAction.MANUAL_CONTACT) }
                }
            }
        }
    }
}

/**
 * Сетка плиток. В крупном режиме высота плитки рассчитывается так,
 * чтобы на экране помещались ровно три карты.
 */
@Composable
private fun TileGrid(
    display: DisplayMode,
    count: Int,
    key: (Int) -> Any,
    tile: @Composable (Int, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val spacing = 10.dp
        // Лёжа (если поворот разрешён) — больше столбцов, карты в пропорциях настоящей карты.
        val landscape = maxWidth > maxHeight
        val columns = when {
            display == DisplayMode.LARGE -> if (landscape) 2 else 1
            else -> if (landscape) 4 else 2
        }
        val fitThree = (maxHeight - 12.dp - spacing * 2) / 3
        val largeHeight = min(fitThree, (maxWidth - 24.dp) / 1.35f)
        val state = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(count, key = key) { index ->
                val modifier = if (display == DisplayMode.LARGE && !landscape) Modifier.fillMaxWidth().height(largeHeight)
                else Modifier.fillMaxWidth().aspectRatio(CARD_RATIO)
                tile(index, modifier)
            }
        }
    }
}

@Composable
private fun TabSwitch(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        ) {
            TabButton(Icons.Filled.CreditCard, "Карты", tab == HomeTab.CARDS) { onTab(HomeTab.CARDS) }
            TabButton(Icons.Filled.ContactPhone, "Визитки", tab == HomeTab.CONTACTS) { onTab(HomeTab.CONTACTS) }
        }
    }
}

@Composable
private fun TabButton(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.width(132.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) Brand.gradient else SolidColor(Color.Transparent))
            .clickable(enabled = !selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(title, Modifier.padding(start = 8.dp), color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun EmptyState(title: String, text: String, actionText: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.CameraAlt, null, Modifier.size(40.dp), tint = Color.White) }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(text, Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null) Box(
            Modifier.padding(top = 22.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Brand.gradient)
                .clickable(onClick = onAction).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text(actionText, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}
