package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.TextToSpeechModel
import com.arpadfodor.android.paw_scanner.models.ai.LabelsManager
import com.arpadfodor.android.paw_scanner.models.api.ApiInteraction
import com.arpadfodor.android.paw_scanner.models.api.BreedImage
import com.arpadfodor.android.paw_scanner.models.api.BreedInfo
import com.bumptech.glide.Glide
import java.util.*

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    val labels = LabelsManager.getFormattedLabels()

    var placeholderImage: Bitmap? = null

    var onlineImageEnabled = false

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

    private var textToBeSpoken = ""

    fun init(intent: Intent){

        TextToSpeechModel.init(app.applicationContext)
        val name = intent.getStringExtra("breed_name")?:""
        breedName.postValue(name)
        loadPlaceholderImage(R.drawable.paw_scanner)

    }

    fun loadData(){

        image.postValue(placeholderImage)

        val name = breedName.value?:return

        when {
            name.toLowerCase(Locale.getDefault()) == "human" -> {
                breedInfo.postValue(app.getString(R.string.human_info_text))
                loadImageFromAssets(name)
            }
            name.toLowerCase(Locale.getDefault()) == "cat" -> {
                breedInfo.postValue(app.getString(R.string.cat_info_text))
                loadImageFromAssets(name)
            }
            else -> {
                ApiInteraction.loadBreedInfo(name, onSuccess = this::showBreedInfo, onError = this::showTextLoadError)
                breedInfo.postValue(app.getString(R.string.loading))
            }
        }

    }

    private fun loadImageByBreedId(id: String){
        ApiInteraction.loadBreedImage(id, onSuccess = this::showBreedImage, onError = this::showImageLoadError)
    }

    private fun showBreedInfo(info: List<BreedInfo>) {

        val breedInfoText: String

        if(info.isNotEmpty()){

            breedInfoText = app.getString(R.string.breed_info, info[0].name, info[0].breed_group,
                info[0].weight.metric, info[0].height.metric, info[0].life_span,
                info[0].temperament, info[0].bred_for)

            if(onlineImageEnabled){
                loadImageByBreedId(info[0].id.toString())
            }
            else{
                loadImageFromAssets(breedName.value?:return)
            }

        }
        else{
            breedInfoText = app.getString(R.string.api_data_empty, breedName.value)
            loadImageFromAssets(breedName.value?:return)
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
                        .placeholder(R.drawable.paw_scanner)
                        .error(R.drawable.paw_scanner)
                        .submit()
                        .get()

                    image.postValue(loadedImage)

                }
                catch (e: Error){}

            })
            loaderThread.start()

        }

    }

    private fun showTextLoadError(e: Throwable) {
        e.printStackTrace()
        breedInfo.postValue(app.getString(R.string.internet_needed, breedName.value))
        loadImageFromAssets(breedName.value?:return)
    }

    private fun showImageLoadError(e: Throwable) {
        e.printStackTrace()
        loadImageFromAssets(breedName.value?:return)
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

    private fun loadPlaceholderImage(imageId: Int){

        //load breed data from API
        val loaderThread = Thread(Runnable {

            try{

                val loadedImage = Glide.with(app.applicationContext)
                    .asBitmap()
                    .load(imageId)
                    .placeholder(R.drawable.dog_friend)
                    .error(R.drawable.dog_friend)
                    .submit()
                    .get()

                placeholderImage = loadedImage
                image.postValue(placeholderImage)

            }
            catch (e: Error){}

        })
        loaderThread.start()

    }

    private fun loadImageFromAssets(imageName: String){

        //load breed data from API
        val loaderThread = Thread(Runnable {

            try{

                val loadedImage = Glide.with(app.applicationContext)
                    .asBitmap()
                    .load(Uri.parse("file:///android_asset/breeds/$imageName.jpg"))
                    .placeholder(R.drawable.dog_friend)
                    .error(R.drawable.dog_friend)
                    .submit()
                    .get()

                image.postValue(loadedImage)

            }
            catch (e: Exception){
                image.postValue(placeholderImage)
            }

        })
        loaderThread.start()

    }

    fun setBreedName(name: String){
        breedName.postValue(name)
    }

    fun pause() {
        TextToSpeechModel.stop()
    }

}
