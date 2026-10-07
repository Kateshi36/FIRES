package com.example.fires.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Battery optimization for the responder alert service (F1.13). When Android "optimizes" an app
 * it can delay its background work, and alerts arrive late. The rules for WHEN to ask are in
 * [BatteryPromptRules]; this object is the part that touches Android.
 */
object BatteryOptimization {

    private const val TAG = "BatteryOptimization"
    private const val PREFS = "fires_prefs"
    private const val KEY_ASKED = "battery_prompt_asked"
    private const val KEY_ASKED_RESPONSE = "battery_prompt_asked_response" // H5e: its own flag

    /** True when Android already lets this app run in the background without restrictions. */
    fun isIgnoring(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Has the first-visit question been shown on this phone already? */
    fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Has the "Start response" question been shown on this phone already? Separate from [wasAsked]. */
    fun wasAskedForResponse(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_RESPONSE, false)

    fun markAskedForResponse(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED_RESPONSE, true).apply()
    }

    /**
     * Opens the system's "Allow F.I.R.E.S. to run in the background?" dialog. If this phone does
     * not offer that dialog, opens the battery optimization list instead, where the responder
     * can pick the app by hand. Returns false when neither screen could be opened.
     */
    @SuppressLint("BatteryLife") // Intended use: a foreground alert service that must not be delayed.
    fun requestAllow(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return tryStart(context, direct) || tryStart(context, list)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No screen for ${intent.action}", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to open ${intent.action}", e)
        false
    }
}
