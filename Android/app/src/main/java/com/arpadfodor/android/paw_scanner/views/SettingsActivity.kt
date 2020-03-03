package com.arpadfodor.android.paw_scanner.views

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.WindowManager
import com.arpadfodor.android.paw_scanner.R

/**
 * Activity of settings
 */
class SettingsActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {

        const val KEY_IMAGE_PREFERENCE = "image_preference"
        const val KEY_ONLINE_IMAGE = "online_image"
        const val KEY_SHUTTER_COLOR = "shutter_color"

        fun changeSettings(sharedPreferences: SharedPreferences) {

            with (sharedPreferences.edit()) {
                remove(KEY_IMAGE_PREFERENCE)
                putBoolean(KEY_IMAGE_PREFERENCE, sharedPreferences.getBoolean(KEY_IMAGE_PREFERENCE, false))
                remove(KEY_SHUTTER_COLOR)
                putBoolean(KEY_SHUTTER_COLOR, sharedPreferences.getBoolean(KEY_SHUTTER_COLOR, false))
                remove(KEY_ONLINE_IMAGE)
                putBoolean(KEY_ONLINE_IMAGE, sharedPreferences.getBoolean(KEY_ONLINE_IMAGE, false))
                apply()
            }

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {

        when(key) {

            KEY_IMAGE_PREFERENCE -> {
                changeSettings(sharedPreferences)
            }

            KEY_SHUTTER_COLOR -> {
                changeSettings(sharedPreferences)
            }

            KEY_ONLINE_IMAGE -> {
                changeSettings(sharedPreferences)
            }

        }

    }

}