package ru.cardly.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.cardly.core.model.BarcodeFormat
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["archived"]),
        Index(value = ["favorite"]),
        Index(value = ["groupId"]),
    ],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val barcodeValue: String,
    val barcodeFormat: String,
    val cardNumber: String,
    val note: String,
    val frontImagePath: String?,
    val backImagePath: String?,
    @ColumnInfo(defaultValue = "1") val showName: Boolean = true,
    val groupId: String?,
    val favorite: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
    val useCount: Long,
    val validFrom: Long?,
    val validUntil: Long?,
    val sortOrder: Long,
    val revision: Long,
)

@Dao
interface CardDao {
    @Query(
        """
        SELECT * FROM cards
        WHERE archived = 0
        ORDER BY favorite DESC, sortOrder ASC, name COLLATE NOCASE ASC
        """,
    )
    fun observeActiveCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CardEntity?

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Query("SELECT * FROM cards")
    suspend fun getAll(): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(cards: List<CardEntity>) {
        deleteAll()
        upsertAll(cards)
    }
}

/** Визитка. Списки хранятся строками, по одному значению на строку. */
@Entity(tableName = "business_cards")
data class BusinessCardEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val company: String,
    val position: String,
    val phones: String,
    val emails: String,
    val websites: String,
    val address: String,
    val note: String,
    val rawText: String,
    val frontImagePath: String?,
    val backImagePath: String?,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
)

@Dao
interface BusinessCardDao {
    @Query("SELECT * FROM business_cards ORDER BY favorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM business_cards")
    suspend fun getAll(): List<BusinessCardEntity>

    @Query("SELECT * FROM business_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BusinessCardEntity?

    @Upsert
    suspend fun upsert(card: BusinessCardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<BusinessCardEntity>)

    @Query("DELETE FROM business_cards WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [CardEntity::class, BusinessCardEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun businessCardDao(): BusinessCardDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN showName INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `business_cards` (" +
                        "`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `company` TEXT NOT NULL, " +
                        "`position` TEXT NOT NULL, `phones` TEXT NOT NULL, `emails` TEXT NOT NULL, " +
                        "`websites` TEXT NOT NULL, `address` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`rawText` TEXT NOT NULL, `frontImagePath` TEXT, `backImagePath` TEXT, " +
                        "`favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`lastUsedAt` INTEGER, PRIMARY KEY(`id`))",
                )
            }
        }

        @Volatile
        private var instance: CardDatabase? = null

        fun get(context: Context): CardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "cardly.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

interface CardRepository {
    fun observeActiveCards(): Flow<List<Card>>
    suspend fun getCard(id: String): Card?
    suspend fun save(card: Card)
    suspend fun saveAll(cards: List<Card>)
    suspend fun replaceAll(cards: List<Card>)
    suspend fun delete(id: String)
    suspend fun isEmpty(): Boolean
    suspend fun getAll(): List<Card>
}

class RoomCardRepository(
    private val dao: CardDao,
) : CardRepository {
    override fun observeActiveCards(): Flow<List<Card>> =
        dao.observeActiveCards().map { cards -> cards.map(CardEntity::toModel) }

    override suspend fun getCard(id: String): Card? = dao.getById(id)?.toModel()

    override suspend fun save(card: Card) = dao.upsert(card.toEntity())

    override suspend fun saveAll(cards: List<Card>) = dao.upsertAll(cards.map(Card::toEntity))

    override suspend fun replaceAll(cards: List<Card>) = dao.replaceAll(cards.map(Card::toEntity))

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun isEmpty(): Boolean = dao.count() == 0

    override suspend fun getAll(): List<Card> = dao.getAll().map(CardEntity::toModel)
}

interface BusinessCardRepository {
    fun observeAll(): Flow<List<BusinessCard>>
    suspend fun getAll(): List<BusinessCard>
    suspend fun get(id: String): BusinessCard?
    suspend fun save(card: BusinessCard)
    suspend fun saveAll(cards: List<BusinessCard>)
    suspend fun delete(id: String)
}

class RoomBusinessCardRepository(
    private val dao: BusinessCardDao,
) : BusinessCardRepository {
    override fun observeAll(): Flow<List<BusinessCard>> =
        dao.observeAll().map { cards -> cards.map(BusinessCardEntity::toModel) }

    override suspend fun getAll(): List<BusinessCard> = dao.getAll().map(BusinessCardEntity::toModel)
    override suspend fun get(id: String): BusinessCard? = dao.getById(id)?.toModel()
    override suspend fun save(card: BusinessCard) = dao.upsert(card.toEntity())
    override suspend fun saveAll(cards: List<BusinessCard>) = dao.upsertAll(cards.map(BusinessCard::toEntity))
    override suspend fun delete(id: String) = dao.deleteById(id)
}

private fun List<String>.joinLines(): String = map(String::trim).filter(String::isNotEmpty).joinToString("\n")
private fun String.splitLines(): List<String> = split('\n').map(String::trim).filter(String::isNotEmpty)

fun BusinessCard.toEntity(): BusinessCardEntity = BusinessCardEntity(
    id = id,
    fullName = fullName,
    company = company,
    position = position,
    phones = phones.joinLines(),
    emails = emails.joinLines(),
    websites = websites.joinLines(),
    address = address,
    note = note,
    rawText = rawText,
    frontImagePath = frontImagePath,
    backImagePath = backImagePath,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt,
)

fun BusinessCardEntity.toModel(): BusinessCard = BusinessCard(
    id = id,
    fullName = fullName,
    company = company,
    position = position,
    phones = phones.splitLines(),
    emails = emails.splitLines(),
    websites = websites.splitLines(),
    address = address,
    note = note,
    rawText = rawText,
    frontImagePath = frontImagePath,
    backImagePath = backImagePath,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt,
)

fun Card.toEntity(): CardEntity = CardEntity(
    id = id,
    name = name,
    barcodeValue = barcodeValue,
    barcodeFormat = barcodeFormat.name,
    cardNumber = cardNumber,
    note = note,
    frontImagePath = frontImagePath,
    backImagePath = backImagePath,
    showName = showName,
    groupId = groupId,
    favorite = favorite,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt,
    useCount = useCount,
    validFrom = validFrom,
    validUntil = validUntil,
    sortOrder = sortOrder,
    revision = revision,
)

fun CardEntity.toModel(): Card = Card(
    id = id,
    name = name,
    barcodeValue = barcodeValue,
    barcodeFormat = runCatching { BarcodeFormat.valueOf(barcodeFormat) }
        .getOrDefault(BarcodeFormat.UNKNOWN),
    cardNumber = cardNumber,
    note = note,
    frontImagePath = frontImagePath,
    backImagePath = backImagePath,
    showName = showName,
    groupId = groupId,
    favorite = favorite,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastUsedAt = lastUsedAt,
    useCount = useCount,
    validFrom = validFrom,
    validUntil = validUntil,
    sortOrder = sortOrder,
    revision = revision,
)
