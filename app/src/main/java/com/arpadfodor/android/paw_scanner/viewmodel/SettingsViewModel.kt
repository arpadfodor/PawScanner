package com.arpadfodor.android.paw_scanner.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is SettingsFragment"
    }
    val text: LiveData<String> = _text

}
