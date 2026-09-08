package dev.injun.scalelite

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.injun.scalelite.service.Notifications
import javax.inject.Inject

@HiltAndroidApp
class ScaleLiteApp : Application() {

    @Inject lateinit var notifications: Notifications

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }
}
