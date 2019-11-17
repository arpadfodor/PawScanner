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
import com.arpadfodor.android.paw_scanner.models.api.Fact
import com.bumptech.glide.Glide
import java.util.*

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    val labels = LabelsManager.getIdWithNames()

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
    val currentBreed: MutableLiveData<Pair<String, String>> by lazy {
        MutableLiveData<Pair<String, String>>()
    }

    /*
     * Breed info title text
     */
    val breedInfoTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Breed info text
     */
    val breedInfo: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * General info title text
     */
    val generalInfoTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * General info text
     */
    val generalInfo: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Fact text title
     */
    val factTextTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Fact text
     */
    val factText: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    /*
     * Breed image sample
     */
    val image: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    /*
     * Whether breed text view is visible or not
     */
    val isBreedTextViewContainerGone: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    /*
     * Whether fact text view is visible or not
     */
    val isFactTextViewContainerGone: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    private var textToBeSpoken = ""

    fun init(intent: Intent){

        TextToSpeechModel.init(app.applicationContext)
        val id = intent.getStringExtra(app.getString(R.string.KEY_BREED_ID))?:""
        val title = intent.getStringExtra(app.getString(R.string.KEY_BREED_TITLE))?:""

        currentBreed.postValue(Pair(id, title))
        isBreedTextViewContainerGone.postValue(true)
        isFactTextViewContainerGone.postValue(true)

        loadPlaceholderImage(R.drawable.paw_scanner)

    }

    fun loadData(){

        breedInfoTitle.postValue("")
        breedInfo.postValue("")
        generalInfoTitle.postValue("")
        generalInfo.postValue("")
        factTextTitle.postValue("")
        factText.postValue("")
        image.postValue(placeholderImage)

        val currentBreed = currentBreed.value?:return

        when {
            currentBreed.second.toLowerCase(Locale.getDefault()) == "human" -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Human"))
                generalInfo.postValue(app.getString(R.string.human_info_text))
                loadImageFromAssets(currentBreed.first)

                isBreedTextViewContainerGone.postValue(true)
                isFactTextViewContainerGone.postValue(true)

            }
            currentBreed.second.toLowerCase(Locale.getDefault()) == "cat" -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Cat"))
                generalInfo.postValue(app.getString(R.string.cat_info_text))
                loadImageFromAssets(currentBreed.first)
                loadCatFact()

                isBreedTextViewContainerGone.postValue(true)
                isFactTextViewContainerGone.postValue(false)

            }
            else -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Dog"))
                generalInfo.postValue(app.getString(R.string.dog_info_text))
                ApiInteraction.loadBreedInfo(currentBreed.second, onSuccess = this::showBreedInfo, onError = this::showTextLoadError)
                loadDogFact()
                breedInfo.postValue(app.getString(R.string.loading))

                isBreedTextViewContainerGone.postValue(false)
                isFactTextViewContainerGone.postValue(false)

            }
        }

    }

    private fun loadImageByBreedApiId(id: String){
        ApiInteraction.loadBreedImage(id, onSuccess = this::showBreedImage, onError = this::showImageLoadError)
    }

    fun loadCatFact(){
        ApiInteraction.loadCatFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    fun loadDogFact(){
        ApiInteraction.loadDogFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    private fun showBreedInfo(info: List<BreedInfo>) {

        val breedInfoText: String

        if(info.isNotEmpty()){

            breedInfoText = app.getString(R.string.breed_info, info[0].name, info[0].breed_group,
                info[0].weight.metric, info[0].height.metric, info[0].life_span,
                info[0].temperament, info[0].bred_for)

            if(onlineImageEnabled){
                loadImageByBreedApiId(info[0].id.toString())
            }
            else{
                loadImageFromAssets(currentBreed.value?.first?:return)
            }

        }
        else{
            breedInfoText = app.getString(R.string.api_data_empty, currentBreed.value?.second)
            loadImageFromAssets(currentBreed.value?.first?:return)
        }

        breedInfoTitle.postValue(app.getString(R.string.title_breed_specific))
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
        else{
            loadImageFromAssets(currentBreed.value?.first?:return)
        }

    }

    private fun showFact(fact: Fact){
        factTextTitle.postValue(app.getString(R.string.did_you_know))
        factText.postValue(app.getString(R.string.fact, fact.fact))
    }

    private fun showTextLoadError(e: Throwable) {
        e.printStackTrace()

        val breedInfoText: String = app.getString(R.string.internet_needed, currentBreed.value?.second)

        breedInfoTitle.postValue(app.getString(R.string.title_breed_specific))
        breedInfo.postValue(breedInfoText)
        loadImageFromAssets(currentBreed.value?.first?:return)
    }

    private fun showImageLoadError(e: Throwable) {
        e.printStackTrace()
        loadImageFromAssets(currentBreed.value?.first?:return)
    }

    private fun showFactLoadError(e: Throwable) {
        e.printStackTrace()
        val fact: String = app.getString(R.string.fact, app.getString(R.string.internet_needed_to_fact))
        factTextTitle.postValue(app.getString(R.string.did_you_know))
        factText.postValue(fact)
    }

    fun setTextToBeSpoken(){

        var spokenText = ""
        spokenText += currentBreed.value?.second
        spokenText += "\n"
        spokenText += breedInfoTitle.value
        spokenText += "\n"
        spokenText += breedInfo.value
        spokenText += "\n"
        spokenText += generalInfoTitle.value
        spokenText += "\n"
        spokenText += generalInfo.value
        spokenText += "\n"
        spokenText += factTextTitle.value
        spokenText += "\n"
        spokenText += factText.value

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

    private fun loadImageFromAssets(imageId: String){

        //load breed data from API
        val loaderThread = Thread(Runnable {

            try{

                val loadedImage = Glide.with(app.applicationContext)
                    .asBitmap()
                    .load(Uri.parse("file:///android_asset/breeds/$imageId.jpg"))
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

    fun setCurrentBreed(element: Pair<String, String>){
        currentBreed.postValue(element)
    }

    fun pause() {
        TextToSpeechModel.stop()
    }

}
