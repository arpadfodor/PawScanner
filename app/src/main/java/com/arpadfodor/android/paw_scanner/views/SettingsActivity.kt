package com.arpadfodor.android.paw_scanner.views

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.arpadfodor.android.paw_scanner.R

/**
 * Activity of settings
 */
class SettingsActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {

        const val KEY_IMAGE_QUALITY = "image_quality"

        fun changeSettings(sharedPreferences: SharedPreferences) {
            with (sharedPreferences.edit()) {
                remove(KEY_IMAGE_QUALITY)
                putBoolean(KEY_IMAGE_QUALITY, sharedPreferences.getBoolean(KEY_IMAGE_QUALITY, false))
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
            KEY_IMAGE_QUALITY -> {
                changeSettings(sharedPreferences)
            }
        }

    }

}