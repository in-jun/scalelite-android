package dev.injun.scalelite.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.injun.scalelite.data.db.DeviceDao
import dev.injun.scalelite.data.db.EventDao
import dev.injun.scalelite.data.db.MeasurementDao
import dev.injun.scalelite.data.db.ScaleLiteDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ScaleLiteDatabase =
        Room.databaseBuilder(context, ScaleLiteDatabase::class.java, "scalelite.db").build()

    @Provides fun deviceDao(db: ScaleLiteDatabase): DeviceDao = db.devices()

    @Provides fun measurementDao(db: ScaleLiteDatabase): MeasurementDao = db.measurements()

    @Provides fun eventDao(db: ScaleLiteDatabase): EventDao = db.events()
}
