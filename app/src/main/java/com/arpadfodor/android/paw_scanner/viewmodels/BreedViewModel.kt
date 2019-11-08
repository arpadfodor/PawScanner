package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.Recognition
import com.arpadfodor.android.paw_scanner.models.TextToSpeechModel
import com.bumptech.glide.Glide
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlin.math.min

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    var breedName = ""

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
        breedName = intent.getStringExtra("breed_name")?:""
    }

    fun loadData(){

        //TODO: load the breed sample image here - API?

        val loaderThread = Thread(Runnable {

            val loadedImage = Glide.with(app.applicationContext)
                .asBitmap()
                .load(R.drawable.dog_example)
                .submit()
                .get()

            val breedText = "Blablabla"

            image.postValue(loadedImage)
            breedInfo.postValue(breedText)

        })
        loaderThread.start()

    }

    fun setTextToBeSpoken(){
        var spokenText = ""
        spokenText += "$breedName. "
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

    fun pause() {
        TextToSpeechModel.stop()
    }

}
