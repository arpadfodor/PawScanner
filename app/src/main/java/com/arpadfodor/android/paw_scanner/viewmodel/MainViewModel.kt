package com.arpadfodor.android.paw_scanner.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.arpadfodor.android.paw_scanner.model.Classifier
import com.arpadfodor.android.paw_scanner.model.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.model.Device
import com.arpadfodor.android.paw_scanner.model.Recognition
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    var app: Application = application

    /*
     * The Classifier
     */
    val classifier: MutableLiveData<Classifier> by lazy {
        MutableLiveData<Classifier>()
    }

    /*
     * Last classification result
     */
    val result: MutableLiveData<List<Recognition>> by lazy {
        MutableLiveData<List<Recognition>>()
    }

    /*
     * The loaded image
     */
    val loadedImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    fun loadClassifier(){
        classifier.value = ClassifierFloatMobileNet(app.assets, Device.CPU, 1)
    }

    fun recognizeLoadedImage(){
        loadedImage.value?: return
        recognizeImage(loadedImage.value!!)
    }

    fun recognizeLiveImage(bitmap: Bitmap?){
        bitmap?: return
        recognizeImage(bitmap)
    }

    private fun recognizeImage(bitmap: Bitmap){
        viewModelScope.launch {
            result.value = classifier.value?.recognizeImage(bitmap)
        }.onJoin
    }

}
