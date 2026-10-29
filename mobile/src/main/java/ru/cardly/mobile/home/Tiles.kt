package ru.cardly.mobile.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card
import ru.cardly.mobile.data.rememberImage
import ru.cardly.mobile.ui.photoFrame
import ru.cardly.mobile.ui.Brand

/** Соотношение сторон банковской/дисконтной карты (ISO ID-1). */
const val CARD_RATIO = 85.6f / 53.98f

data class CardVisual(val colors: List<Color>, val accent: Color, val mark: String)

private val palette = listOf(
    listOf(Color(0xFF174EA6), Color(0xFF101D45)),
    listOf(Color(0xFF6A1B9A), Color(0xFF2A0B45)),
    listOf(Color(0xFF00897B), Color(0xFF00423A)),
    listOf(Color(0xFFE65100), Color(0xFF7A2A00)),
    listOf(Color(0xFFC2185B), Color(0xFF5E0B2C)),
    listOf(Color(0xFF37474F), Color(0xFF151B1F)),
)

fun Card.visual(): CardVisual = when (name.trim().lowercase()) {
    "лента" -> CardVisual(listOf(Color(0xFF075CAC), Color(0xFF073B86)), Color(0xFFFFC21A), "Л")
    "магнит" -> CardVisual(listOf(Color(0xFFF20B21), Color(0xFFB60013)), Color.White, "М")
    "пятёрочка", "пятерочка" -> CardVisual(listOf(Color(0xFF07933F), Color(0xFF005B2D)), Color(0xFFE72B31), "5")
    else -> CardVisual(
        palette[Math.floorMod(name.trim().lowercase().hashCode(), palette.size)],
        Color.White.copy(alpha = 0.9f),
        name.trim().take(1).uppercase().ifEmpty { "C" },
    )
}

/** Плитка дисконтной карты. Фото заполняет плитку целиком — без полос по краям. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardTile(card: Card, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val visual = card.visual()
    val image by rememberImage(card.frontImagePath, 1080)
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(visual.colors)).photoFrame(22.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), "Карта ${card.name}", Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (card.showName) Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.65f), 0.5f to Color.Transparent),
                ),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val circle = maxHeight * 0.62f
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(circle)
                        .clip(CircleShape).background(visual.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(visual.mark, color = visual.accent, fontSize = (circle.value * 0.5f).sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (card.showName || image == null) Text(
            card.name, Modifier.align(Alignment.TopStart).padding(18.dp).fillMaxWidth(0.7f),
            color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (card.favorite) Icon(
            Icons.Filled.Star, "Избранная", Modifier.align(Alignment.BottomStart).padding(14.dp).size(22.dp),
            tint = Brand.star,
        )
    }
}

/** Плитка визитки: фото визитки или аккуратная «бумажная» карточка с данными. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactTile(contact: BusinessCard, modifier: Modifier, compact: Boolean, showPhoto: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val image by rememberImage(if (showPhoto) contact.frontImagePath else null, 1080)
    Box(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2F3A), Color(0xFF15171D)))).photoFrame(22.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val bitmap = image
        if (bitmap != null) {
            // Режим «Фото»: только сама визитка, без надписей поверх.
            Image(bitmap.asImageBitmap(), "Визитка ${contact.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().padding(if (compact) 14.dp else 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        contact.title, color = Color.White, fontSize = if (compact) 16.sp else 22.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (contact.position.isNotBlank()) Text(
                        contact.position, color = Color.White.copy(alpha = 0.72f), fontSize = if (compact) 12.sp else 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column {
                    if (contact.company.isNotBlank() && contact.fullName.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Business, null, Modifier.size(16.dp), tint = Brand.cyan)
                        Text(contact.company, Modifier.padding(start = 6.dp), color = Color.White.copy(alpha = 0.86f), fontSize = if (compact) 12.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    contact.phones.firstOrNull()?.let { phone ->
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, null, Modifier.size(16.dp), tint = Brand.cyan)
                            Text(phone, Modifier.padding(start = 6.dp), color = Color.White.copy(alpha = 0.86f), fontSize = if (compact) 12.sp else 15.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (contact.favorite) Icon(
            Icons.Filled.Star, "Избранная", Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp), tint = Brand.star,
        )
    }
}
