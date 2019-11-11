package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.models.*
import com.arpadfodor.android.paw_scanner.models.AI.LabelsManager
import com.arpadfodor.android.paw_scanner.models.API.ApiInteraction

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    val labels = LabelsManager.getFormattedLabels()

    /*
     * Breed name
     */
    val isSelectorDisplayed: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    /*
     * Breed name
     */
    val breedName: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Breed info text
     */
    val breedInfo: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Breed image sample
     */
    val image: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    var textToBeSpoken = ""

    fun init(intent: Intent){
        TextToSpeechModel.init(app.applicationContext)
        val name = intent.getStringExtra("breed_name")?:""
        breedName.postValue(name)
    }

    fun loadData(){

        val loaderThread = Thread(Runnable {
            //load breed data from API
            breedInfo.postValue(ApiInteraction.loadBreedInfo(app.applicationContext, breedName.value?:""))
        })
        loaderThread.start()

    }

    fun setTextToBeSpoken(){
        var spokenText = ""
        spokenText += breedName.value + ". "
        spokenText += breedInfo.value
        textToBeSpoken = spokenText
    }

    fun speakClicked(){

        if(TextToSpeechModel.isSpeaking()){
            TextToSpeechModel.stop()
        }

        else{
            TextToSpeechModel.speak(textToBeSpoken)
        }

    }

    fun setBreedName(name: String){
        breedName.postValue(name)
    }

    fun pause() {
        TextToSpeechModel.stop()
    }

}
