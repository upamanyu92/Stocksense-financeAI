package com.stocksense.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import com.stocksense.app.data.database.dao.*
import com.stocksense.app.data.database.entities.*

@Database(
    entities = [
        Stock::class,
        StockHistory::class,
        Prediction::class,
        Alert::class,
        LearningData::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stockDao(): StockDao
    abstract fun stockHistoryDao(): StockHistoryDao
    abstract fun predictionDao(): PredictionDao
    abstract fun alertDao(): AlertDao
    abstract fun learningDataDao(): LearningDataDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocksense.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/** Type converters for Room entities that contain enums. */
class Converters {

    @TypeConverter
    fun fromAlertType(value: AlertType): String = value.name

    @TypeConverter
    fun toAlertType(value: String): AlertType = AlertType.valueOf(value)

    @TypeConverter
    fun fromAlertStatus(value: AlertStatus): String = value.name

    @TypeConverter
    fun toAlertStatus(value: String): AlertStatus = AlertStatus.valueOf(value)
}
