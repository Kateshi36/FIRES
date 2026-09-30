package com.example.fires

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application class. Runs once when the app process starts.
 * Configures osmdroid (OpenStreetMap), which is required before any map is shown.
 */
class FiresApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = Configuration.getInstance()
        // OpenStreetMap tile servers require an identifying user-agent.
        config.userAgentValue = packageName
        // Keep map cache in app-private storage so no storage permission is needed.
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
    }
}
