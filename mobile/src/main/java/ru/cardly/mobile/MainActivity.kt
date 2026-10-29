package ru.cardly.mobile

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.cardly.core.data.BusinessCardRepository
import ru.cardly.core.data.CardDatabase
import ru.cardly.core.data.CardRepository
import ru.cardly.core.data.RoomBusinessCardRepository
import ru.cardly.core.data.RoomCardRepository
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card
import ru.cardly.core.sync.WearCardCatalogPublisher
import ru.cardly.core.sync.WearDisplaySettings
import ru.cardly.core.sync.WearPayCardPublisher
import ru.cardly.mobile.business.BusinessEditorScreen
import ru.cardly.mobile.business.BusinessViewScreen
import ru.cardly.mobile.business.ContactDraft
import ru.cardly.mobile.business.VCardParser
import ru.cardly.mobile.cards.CardDraft
import ru.cardly.mobile.cards.CardEditorScreen
import ru.cardly.mobile.cards.CardViewScreen
import ru.cardly.mobile.data.AutoBackup
import ru.cardly.mobile.data.Backup
import ru.cardly.mobile.data.CardImages
import ru.cardly.mobile.data.CardShortcuts
import ru.cardly.mobile.home.filteredSorted
import ru.cardly.mobile.home.AddAction
import ru.cardly.mobile.home.HomeScreen
import ru.cardly.mobile.home.HomeTab
import ru.cardly.mobile.scan.decodeBarcode
import ru.cardly.mobile.scan.rememberBarcodeScanner
import ru.cardly.mobile.scan.rememberDocumentScanner
import ru.cardly.mobile.scan.rememberImagePicker
import ru.cardly.mobile.settings.SettingsScreen
import ru.cardly.mobile.ui.AppSettings
import ru.cardly.mobile.ui.Brand
import ru.cardly.mobile.ui.CardlyPreferences
import ru.cardly.mobile.ui.CardlyTheme

class MainActivity : ComponentActivity() {
    /** Когда приложение ушло в фон. Если отсутствовали дольше минуты — открываем снова карты. */
    private var stoppedAt = 0L
    private val returnSignal: MutableIntState = mutableIntStateOf(0)
    /** Карта, которую попросили открыть ярлыком с иконки приложения. */
    private val openCardRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCardRequest.value = intent?.getStringExtra(CardShortcuts.EXTRA_CARD_ID)
        val database = CardDatabase.get(applicationContext)
        val cards = RoomCardRepository(database.cardDao())
        val contacts = RoomBusinessCardRepository(database.businessCardDao())
        val publisher = runCatching { WearCardCatalogPublisher(applicationContext) }.getOrNull()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        setContent {
            val preferences = remember { CardlyPreferences(applicationContext) }
            var settings by remember { mutableStateOf(preferences.load()) }
            LaunchedEffect(settings.lockPortrait) {
                requestedOrientation = if (settings.lockPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            CardlyTheme(settings) {
                CardlyApp(
                    cardRepository = cards,
                    contactRepository = contacts,
                    publisher = publisher,
                    settings = settings,
                    version = version,
                    returnSignal = returnSignal.intValue,
                    openCardId = openCardRequest.value,
                    onOpenCardHandled = { openCardRequest.value = null },
                    onSettingsChange = { settings = it; preferences.save(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(CardShortcuts.EXTRA_CARD_ID)?.let { openCardRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) returnSignal.intValue++
        stoppedAt = 0
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }
}

/** Экраны приложения (простой стек без библиотеки навигации). */
private sealed interface Route {
    data object Home : Route
    data class CardView(val ids: List<String>, val startId: String) : Route
    data class CardEdit(val draft: CardDraft) : Route
    data class ContactView(val id: String) : Route
    data class ContactEdit(val draft: ContactDraft) : Route
    data object Settings : Route
}

/** Фоновая область для записи в базу, которая не должна прерываться при закрытии экрана. */
private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
private fun CardlyApp(
    cardRepository: CardRepository,
    contactRepository: BusinessCardRepository,
    publisher: WearCardCatalogPublisher?,
    settings: AppSettings,
    version: String,
    returnSignal: Int,
    openCardId: String?,
    onOpenCardHandled: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardsFlow = remember { cardRepository.observeActiveCards() }
    val contactsFlow = remember { contactRepository.observeAll() }
    val cards by cardsFlow.collectAsState(initial = null)
    val contacts by contactsFlow.collectAsState(initial = null)

    var tab by rememberSaveable { mutableStateOf(HomeTab.CARDS) }
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    var deleteCard by remember { mutableStateOf<Card?>(null) }
    var deleteContact by remember { mutableStateOf<BusinessCard?>(null) }
    fun push(route: Route) { stack = stack + route }
    fun pop() { stack = if (stack.size > 1) stack.dropLast(1) else listOf(Route.Home) }
    fun replaceTop(route: Route) { stack = stack.dropLast(1) + route }

    // Вернулись в приложение после перерыва — показываем карты (например, в магазине).
    LaunchedEffect(returnSignal) {
        if (returnSignal == 0) return@LaunchedEffect
        when (stack.last()) {
            Route.Home, is Route.ContactView, Route.Settings -> { stack = listOf(Route.Home); tab = HomeTab.CARDS }
            else -> Unit
        }
    }

    // Открытие карты ярлыком с иконки приложения.
    LaunchedEffect(openCardId, cards) {
        val id = openCardId ?: return@LaunchedEffect
        val list = cards ?: return@LaunchedEffect
        onOpenCardHandled()
        if (list.any { it.id == id }) {
            tab = HomeTab.CARDS
            stack = listOf(Route.Home, Route.CardView(list.filteredSorted("", settings.cardsSort).map { it.id }, id))
        }
    }

    // Ярлыки самых нужных карт на иконке приложения.
    LaunchedEffect(cards) {
        val list = cards ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { CardShortcuts.update(context, list) }
    }

    // Старые демо-карты из первых версий.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("cardly", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("demo_cards_removed", false)) {
            withContext(Dispatchers.IO) { listOf("demo-lenta", "demo-magnit", "demo-five").forEach { cardRepository.delete(it) } }
            prefs.edit().putBoolean("demo_cards_removed", true).apply()
        }
    }

    // ---------- Синхронизация с часами ----------
    var syncRequest by remember { mutableStateOf(0) }
    var handledSync by remember { mutableStateOf(0) }
    var publishedSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cards, settings.amoled, settings.maximumBrightness, settings.keepScreenOn, syncRequest) {
        val list = cards ?: return@LaunchedEffect
        val wear = publisher ?: return@LaunchedEffect
        val manual = syncRequest > handledSync
        val signature = wearSignature(list, settings)
        if (!manual && signature == publishedSignature) return@LaunchedEffect
        if (manual) handledSync = syncRequest
        delay(350)
        val sent = withContext(Dispatchers.IO) {
            wear.publish(list, WearDisplaySettings(settings.amoled, settings.maximumBrightness, settings.keepScreenOn, 15))
        }
        if (sent) publishedSignature = signature
        if (manual) Toast.makeText(context, if (sent) "Карты отправлены на часы" else "Часы не найдены", Toast.LENGTH_SHORT).show()
    }

    // ---------- Добавление ----------
    val scanCard = rememberBarcodeScanner { scan ->
        push(Route.CardEdit(CardDraft(number = scan.value, format = scan.format)))
    }
    val cardCodeFromImage = rememberImagePicker { uri ->
        scope.launch {
            val found = withContext(Dispatchers.Default) { CardImages.decodeUri(context, uri)?.let { decodeBarcode(context, it) } }
            if (found == null) Toast.makeText(context, "Код на изображении не найден", Toast.LENGTH_LONG).show()
            push(Route.CardEdit(CardDraft(number = found?.value.orEmpty(), format = found?.format)))
        }
    }
    fun openContactDraft(uris: List<android.net.Uri>) {
        scope.launch {
            val staged = withContext(Dispatchers.IO) { uris.take(2).mapNotNull { CardImages.stage(context, it, 2200) } }
            if (staged.isEmpty()) { Toast.makeText(context, "Не удалось открыть фото", Toast.LENGTH_SHORT).show(); return@launch }
            push(Route.ContactEdit(ContactDraft(stagedFront = staged[0], stagedBack = staged.getOrNull(1), recognize = true)))
        }
    }
    val scanContact = rememberDocumentScanner(pages = 2) { openContactDraft(it) }
    val contactFromGallery = rememberImagePicker { openContactDraft(listOf(it)) }
    val contactQr = rememberBarcodeScanner { scan ->
        val parsed = VCardParser.parse(scan.value)
        if (parsed == null) Toast.makeText(context, "В этом QR-коде нет визитки", Toast.LENGTH_LONG).show()
        else push(Route.ContactEdit(ContactDraft().applying(parsed, "")))
    }

    // ---------- Резервная копия ----------
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                Backup.export(context, uri, Backup.Content(cardRepository.getAll(), contactRepository.getAll()))
            }
            Toast.makeText(context, if (ok) "Копия сохранена" else "Не удалось сохранить", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { Backup.import(context, uri) }
            result.onSuccess { content ->
                withContext(Dispatchers.IO) {
                    cardRepository.saveAll(content.cards)
                    contactRepository.saveAll(content.contacts)
                }
                Toast.makeText(context, "Восстановлено: карт ${content.cards.size}, визиток ${content.contacts.size}", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "Это не копия Cardly", Toast.LENGTH_LONG).show() }
        }
    }

    // ---------- Автокопия ----------
    var backupFolder by remember { mutableStateOf(AutoBackup.folderTitle(context)) }
    var autoBackupChecked by remember { mutableStateOf(false) }
    LaunchedEffect(cards, contacts, settings.autoBackupDays) {
        val c = cards ?: return@LaunchedEffect
        val b = contacts ?: return@LaunchedEffect
        if (autoBackupChecked) return@LaunchedEffect
        autoBackupChecked = true
        delay(3_000) // не мешаем открытию карты на кассе
        withContext(Dispatchers.IO) { AutoBackup.runIfDue(context, settings.autoBackupDays, c, b) }
    }
    var askFolder by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) AutoBackup.markAsked(context)
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            AutoBackup.setFolder(context, uri)
            backupFolder = AutoBackup.folderTitle(context)
            // Сразу кладём первую копию в новую папку.
            val c = cards.orEmpty(); val b = contacts.orEmpty()
            if (c.isEmpty() && b.isEmpty()) Toast.makeText(context, "Папка выбрана", Toast.LENGTH_SHORT).show()
            else scope.launch {
                val ok = withContext(Dispatchers.IO) { AutoBackup.runIfDue(context, settings.autoBackupDays, c, b, force = true) }
                Toast.makeText(context, if (ok) "Копия сохранена в выбранную папку" else "Не удалось записать в эту папку", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- Своя карта оплаты для часов ----------
    val payCardDir = remember { java.io.File(context.filesDir, "pay_card").apply { mkdirs() } }
    var payCardPath by remember { mutableStateOf(payCardDir.listFiles()?.maxByOrNull { it.lastModified() }?.absolutePath) }
    fun usePayCard(uri: android.net.Uri) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                CardImages.decodeUri(context, uri)?.let { bitmap ->
                    // Под экран часов: обрезаем по центру до пропорций карты и уменьшаем до 512×323
                    // (как встроенные дизайны) — на часах файл занимает ~30–50 КБ.
                    val targetW = 512; val targetH = 323
                    val scale = maxOf(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
                    val cropW = (targetW / scale).toInt().coerceIn(1, bitmap.width)
                    val cropH = (targetH / scale).toInt().coerceIn(1, bitmap.height)
                    val cropped = android.graphics.Bitmap.createBitmap(bitmap, (bitmap.width - cropW) / 2, (bitmap.height - cropH) / 2, cropW, cropH)
                    val fitted = android.graphics.Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                    java.io.ByteArrayOutputStream().use { out ->
                        fitted.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray()
                    }
                }
            }
            if (bytes == null) { Toast.makeText(context, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show(); return@launch }
            val saved = withContext(Dispatchers.IO) {
                payCardDir.listFiles()?.forEach { it.delete() }
                java.io.File(payCardDir, "card-${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
            }
            payCardPath = saved.absolutePath
            val sent = withContext(Dispatchers.IO) { runCatching { WearPayCardPublisher(context).publish(bytes) }.getOrDefault(false) }
            Toast.makeText(context, if (sent) "Карта отправлена на часы" else "Сохранено — уйдёт на часы, когда они будут рядом", Toast.LENGTH_LONG).show()
        }
    }
    val payCardCamera = rememberDocumentScanner(pages = 1) { uris -> uris.firstOrNull()?.let(::usePayCard) }
    val payCardGallery = rememberImagePicker { usePayCard(it) }
    fun removePayCard() {
        payCardDir.listFiles()?.forEach { it.delete() }; payCardPath = null
        ioScope.launch { runCatching { WearPayCardPublisher(context).publish(null) } }
    }

    // ---------- Действия ----------
    fun toggleCardFavorite(card: Card) {
        ioScope.launch {
            cardRepository.save(card.copy(favorite = !card.favorite, updatedAt = System.currentTimeMillis(), revision = card.revision + 1))
        }
    }
    fun toggleContactFavorite(contact: BusinessCard) {
        ioScope.launch { contactRepository.save(contact.copy(favorite = !contact.favorite, updatedAt = System.currentTimeMillis())) }
    }
    fun saveCard(draft: CardDraft, format: BarcodeFormat) {
        val source = draft.source
        val id = source?.id ?: UUID.randomUUID().toString()
        ioScope.launch {
            val staged = draft.stagedImage
            val image = when {
                staged != null -> CardImages.commit(context, staged, "card_images", id)
                    ?.also { if (it != source?.frontImagePath) CardImages.delete(source?.frontImagePath) }
                    ?: source?.frontImagePath
                draft.removeImage -> { CardImages.delete(source?.frontImagePath); null }
                else -> source?.frontImagePath
            }
            val stagedBack = draft.stagedBack
            val back = when {
                stagedBack != null -> CardImages.commit(context, stagedBack, "card_images", "$id-back")
                    ?.also { if (it != source?.backImagePath) CardImages.delete(source?.backImagePath) }
                    ?: source?.backImagePath
                draft.removeBack -> { CardImages.delete(source?.backImagePath); null }
                else -> source?.backImagePath
            }
            val now = System.currentTimeMillis()
            val showName = if (image != null) draft.showName else true
            cardRepository.save(
                source?.copy(
                    name = draft.name, barcodeValue = draft.number, barcodeFormat = format, cardNumber = draft.number,
                    note = draft.note, frontImagePath = image, backImagePath = back, favorite = draft.favorite, showName = showName,
                    updatedAt = now, revision = source.revision + 1,
                ) ?: Card(
                    id = id, name = draft.name, barcodeValue = draft.number, barcodeFormat = format, cardNumber = draft.number,
                    note = draft.note, frontImagePath = image, backImagePath = back, favorite = draft.favorite, showName = showName,
                    createdAt = now, updatedAt = now, sortOrder = now,
                ),
            )
        }
        tab = HomeTab.CARDS
        pop()
        if (settings.autoBackupDays > 0 && AutoBackup.shouldAskFolder(context)) askFolder = true
    }
    fun saveContact(draft: ContactDraft) {
        val source = draft.source
        val id = source?.id ?: UUID.randomUUID().toString()
        val stagedFront = draft.stagedFront
        ioScope.launch {
            val front = if (stagedFront != null) CardImages.commit(context, stagedFront, "contact_images", "$id-front") else source?.frontImagePath
            val back = if (stagedFront != null) draft.stagedBack?.let { CardImages.commit(context, it, "contact_images", "$id-back") } else source?.backImagePath
            if (stagedFront != null) { CardImages.delete(source?.frontImagePath); CardImages.delete(source?.backImagePath) }
            val now = System.currentTimeMillis()
            contactRepository.save(
                BusinessCard(
                    id = id, fullName = draft.fullName.trim(), company = draft.company.trim(), position = draft.position.trim(),
                    phones = draft.phones, emails = draft.emails, websites = draft.websites,
                    address = draft.address.trim(), note = draft.note.trim(), rawText = draft.rawText,
                    frontImagePath = front, backImagePath = back, favorite = draft.favorite,
                    createdAt = source?.createdAt ?: now, updatedAt = now, lastUsedAt = source?.lastUsedAt,
                ),
            )
        }
        tab = HomeTab.CONTACTS
        if (source == null) replaceTop(Route.ContactView(id)) else pop()
        if (settings.autoBackupDays > 0 && AutoBackup.shouldAskFolder(context)) askFolder = true
    }

    // ---------- Экраны ----------
    val top = stack.last()
    AnimatedContent(
        targetState = top,
        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
        label = "screens",
    ) { route ->
        when (route) {
            Route.Home -> HomeScreen(
                tab = tab, onTab = { tab = it },
                cards = cards, contacts = contacts,
                settings = settings, onSettingsChange = onSettingsChange,
                onOpenCard = { card, visible -> push(Route.CardView(visible.map { it.id }, card.id)) },
                onEditCard = { push(Route.CardEdit(CardDraft(source = it))) },
                onToggleFavoriteCard = { toggleCardFavorite(it) },
                onDeleteCard = { deleteCard = it },
                onOpenContact = { push(Route.ContactView(it.id)) },
                onEditContact = { push(Route.ContactEdit(ContactDraft(source = it))) },
                onToggleFavoriteContact = { toggleContactFavorite(it) },
                onDeleteContact = { deleteContact = it },
                onAdd = { action ->
                    when (action) {
                        AddAction.SCAN_CODE -> scanCard()
                        AddAction.CODE_FROM_IMAGE -> cardCodeFromImage()
                        AddAction.MANUAL_CARD -> push(Route.CardEdit(CardDraft()))
                        AddAction.SCAN_CONTACT -> scanContact()
                        AddAction.CONTACT_FROM_GALLERY -> contactFromGallery()
                        AddAction.CONTACT_QR -> contactQr()
                        AddAction.MANUAL_CONTACT -> push(Route.ContactEdit(ContactDraft()))
                    }
                },
                onOpenSettings = { push(Route.Settings) },
                onSync = { syncRequest += 1 },
            )

            is Route.CardView -> {
                val byId = cards.orEmpty().associateBy { it.id }
                val list = route.ids.mapNotNull { byId[it] }
                CardViewScreen(
                    cards = list, startId = route.startId, settings = settings,
                    onBack = { pop() },
                    onEdit = { push(Route.CardEdit(CardDraft(source = it))) },
                    onToggleFavorite = { toggleCardFavorite(it) },
                    onDelete = { card -> removeCard(card, cardRepository); if (list.size <= 1) pop() },
                    onShown = { card ->
                        ioScope.launch {
                            cardRepository.getCard(card.id)?.let {
                                cardRepository.save(it.copy(lastUsedAt = System.currentTimeMillis(), useCount = it.useCount + 1))
                            }
                        }
                    },
                )
            }

            is Route.CardEdit -> CardEditorScreen(
                initial = route.draft,
                otherCards = cards.orEmpty().filter { it.id != route.draft.source?.id },
                onClose = { pop() },
                onSave = { draft, format -> saveCard(draft, format) },
                onDelete = { deleteCard = it },
            )

            is Route.ContactView -> {
                val contact = contacts?.firstOrNull { it.id == route.id }
                if (contact == null) {
                    // Только что сохранённая визитка появляется в базе с небольшой задержкой.
                    if (contacts != null) LaunchedEffect(route.id) { delay(1500); pop() }
                } else {
                    LaunchedEffect(route.id) {
                        ioScope.launch { contactRepository.save(contact.copy(lastUsedAt = System.currentTimeMillis())) }
                    }
                    BusinessViewScreen(
                        contact = contact,
                        onBack = { pop() },
                        onEdit = { push(Route.ContactEdit(ContactDraft(source = contact))) },
                        onToggleFavorite = { toggleContactFavorite(contact) },
                        onDelete = { removeContact(contact, contactRepository); pop() },
                    )
                }
            }

            is Route.ContactEdit -> BusinessEditorScreen(
                initial = route.draft,
                onClose = { pop() },
                onSave = { saveContact(it) },
                onDelete = { deleteContact = it },
            )

            Route.Settings -> SettingsScreen(
                settings = settings, version = version, onChange = onSettingsChange, onBack = { pop() },
                onSync = { syncRequest += 1 },
                onExport = { exportLauncher.launch("Cardly-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                backupFolder = backupFolder,
                onPickBackupFolder = { folderLauncher.launch(AutoBackup.initialFolderUri()) },
                payCardPath = payCardPath,
                onPayCardCamera = { payCardCamera() },
                onPayCardGallery = { payCardGallery() },
                onPayCardRemove = { removePayCard() },
            )
        }
    }

    if (askFolder) {
        AlertDialog(
            onDismissRequest = { askFolder = false; AutoBackup.markAsked(context) },
            title = { Text("Папка для копий") },
            text = { Text("Создайте папку «Cardly» в памяти телефона и нажмите «Использовать эту папку».") },
            confirmButton = {
                TextButton(onClick = { askFolder = false; folderLauncher.launch(AutoBackup.initialFolderUri()) }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { askFolder = false; AutoBackup.markAsked(context) }) { Text("Позже") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
    deleteCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteCard = null },
            title = { Text("Удалить карту?") },
            text = { Text("«${card.name}» будет удалена с телефона и часов.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCard = null
                    removeCard(card, cardRepository)
                    stack = listOf(Route.Home)
                }) { Text("Удалить", color = Brand.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteCard = null }) { Text("Отмена") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
    deleteContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleteContact = null },
            title = { Text("Удалить визитку?") },
            text = { Text("«${contact.title}» будет удалена.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteContact = null
                    removeContact(contact, contactRepository)
                    stack = listOf(Route.Home)
                }) { Text("Удалить", color = Brand.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteContact = null }) { Text("Отмена") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

private fun removeCard(card: Card, repository: CardRepository) {
    ioScope.launch { CardImages.delete(card.frontImagePath); CardImages.delete(card.backImagePath); repository.delete(card.id) }
}

private fun removeContact(contact: BusinessCard, repository: BusinessCardRepository) {
    ioScope.launch { CardImages.delete(contact.frontImagePath); CardImages.delete(contact.backImagePath); repository.delete(contact.id) }
}

/**
 * Что действительно нужно часам. Открытие карты (lastUsedAt, useCount) сюда не входит,
 * чтобы просмотр карты не запускал синхронизацию.
 */
private fun wearSignature(cards: List<Card>, settings: AppSettings): String = buildString {
    append(settings.amoled).append(settings.maximumBrightness).append(settings.keepScreenOn)
    cards.forEach { card ->
        append('|').append(card.id).append('§').append(card.name).append('§').append(card.barcodeValue)
            .append('§').append(card.barcodeFormat).append('§').append(card.cardNumber).append('§').append(card.note)
            .append('§').append(card.showName).append('§').append(card.favorite).append('§').append(card.sortOrder)
            .append('§').append(card.frontImagePath).append('§').append(card.updatedAt)
    }
}
