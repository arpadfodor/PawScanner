package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.TextToSpeechModel
import com.arpadfodor.android.paw_scanner.models.ai.LabelsManager
import com.arpadfodor.android.paw_scanner.models.api.ApiInteraction
import com.arpadfodor.android.paw_scanner.models.api.BreedImage
import com.arpadfodor.android.paw_scanner.models.api.BreedInfo
import com.bumptech.glide.Glide

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
        ApiInteraction.loadBreedInfo(breedName.value?:"", onSuccess = this::showBreedInfo, onError = this::showError)
    }

    private fun loadImageByBreedId(id: String){
        ApiInteraction.loadBreedImage(id, onSuccess = this::showBreedImage, onError = this::showError)
    }

    private fun showBreedInfo(info: List<BreedInfo>) {

        var breedInfoText = ""

        if(info.isNotEmpty()){

            breedInfoText = app.getString(R.string.breed_info, info[0].name, info[0].breed_group,
                info[0].weight.metric, info[0].height.metric, info[0].life_span,
                info[0].temperament, info[0].bred_for)

            loadImageByBreedId(info[0].id.toString())

        }

        breedInfo.postValue(breedInfoText)

    }

    private fun showBreedImage(data: List<BreedImage>) {

        if(data.isNotEmpty()){

            //load breed data from API
            val loaderThread = Thread(Runnable {

                try{

                    val loadedImage = Glide.with(app.applicationContext)
                        .asBitmap()
                        .load(data[0].url)
                        .submit()
                        .get()

                    image.postValue(loadedImage)

                }
                catch (e: Error){}

            })
            loaderThread.start()

        }

    }

    private fun showError(e: Throwable) {
        e.printStackTrace()
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
