package com.arpadfodor.android.paw_scanner.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoadViewModel : ViewModel() {

    /*
     * The loaded image
     */
    val loadedImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

}
