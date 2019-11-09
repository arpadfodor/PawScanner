package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.*
import com.bumptech.glide.Glide

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    val labels = LabelsManager.getFormattedLabels()

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
        breedName.value = intent.getStringExtra("breed_name")?:""
    }

    fun isSelectorNecessary(): Boolean{
        return breedName.value?.isBlank()?:true
    }

    fun loadData(){

        //TODO: load the breed sample image here - API?

        val loaderThread = Thread(Runnable {

            val loadedImage = Glide.with(app.applicationContext)
                .asBitmap()
                .load(R.drawable.dog_example)
                .submit()
                .get()

            val breedText = "Blablablablaaaa bla blabla bla bla bla bla a aa aaa aaaa a aa aaaaaaaaaaaaaa aa aa " + breedName.value + ". "

            breedInfo.postValue(breedText)
            image.postValue(loadedImage)

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

    fun setBreedNameAndLoad(name: String){
        breedName.postValue(name)
        loadData()
    }

    fun pause() {
        TextToSpeechModel.stop()
    }

}
