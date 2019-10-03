package com.arpadfodor.android.paw_scanner.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is DashboardFragment"
    }
    val text: LiveData<String> = _text

}
