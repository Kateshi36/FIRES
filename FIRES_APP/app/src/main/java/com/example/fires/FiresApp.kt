package com.example.fires

import android.app.Application
import com.example.fires.util.AlertChannels
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application class. Runs once when the app process starts.
 * Configures osmdroid (OpenStreetMap), which is required before any map is shown, and creates the
 * notification channels.
 */
class FiresApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = Configuration.getInstance()

        // OpenStreetMap's tile servers (tile.openstreetmap.org) block generic user agents.
        // The old value, the bare package name "com.example.fires", looks like every sample app
        // and is what got this app blocked. The user agent must name THIS app and, ideally,
        // say how to reach its developers. See https://operations.osmfoundation.org/policies/tiles/
        config.userAgentValue = buildUserAgent()

        // The tile policy allows at most 2 download connections per client.
        config.tileDownloadThreads = 2
        config.tileFileSystemThreads = 2

        // Keep the map cache in app-private storage (no storage permission needed). filesDir, not
        // cacheDir, so Android does not wipe it and the app re-downloads fewer tiles.
        config.osmdroidBasePath = File(filesDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
        config.tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        config.tileFileSystemCacheTrimBytes = 80L * 1024 * 1024

        // Responder alert notification channels (F1.4). Safe to run on every start.
        AlertChannels.createAll(this)
    }

    private fun buildUserAgent(): String {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        val contact = if (CONTACT.isBlank()) "" else " ($CONTACT)"
        return "FIRES-Bagumbayan-Android/$version$contact"
    }

    private companion object {
        /**
         * Put a contact the OpenStreetMap operators can reach here, for example an email address
         * or your project's website. Optional, but the tile policy asks for it, and it is what lets
         * them contact you instead of blocking you.
         */
        const val CONTACT = ""
    }
}
