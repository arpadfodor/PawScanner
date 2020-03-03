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
import com.arpadfodor.android.paw_scanner.models.api.*
import com.bumptech.glide.Glide

class BreedViewModel(application: Application) : AndroidViewModel(application){

    var app: Application = application

    val labels = LabelsManager.getIdWithNames()

    var placeholderImage: Bitmap? = null

    var onlineImageEnabled = false

    /*
     * Is selector displayed flag
     */
    val isSelectorDisplayed: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    /*
     * Breed Id and name pairs
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

        val currentBreedLocal = currentBreed.value

        if(currentBreedLocal == null || currentBreedLocal.first.isEmpty() || currentBreedLocal.second.isEmpty()){
            return
        }

        when(getAnimalTypePrefixFromId(currentBreedLocal.first)) {

            ApiInteraction.HUMAN_PREFIX -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Human"))
                generalInfo.postValue(app.getString(R.string.human_info_text))
                loadImageFromAssets(currentBreedLocal.first)

                isBreedTextViewContainerGone.postValue(true)
                isFactTextViewContainerGone.postValue(true)

            }
            ApiInteraction.CAT_PREFIX -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Cat"))
                generalInfo.postValue(app.getString(R.string.cat_info_text))
                ApiInteraction.loadCatBreedInfo(currentBreedLocal.second, onSuccess = this::showCatBreedInfo, onError = this::showTextLoadError)
                loadCatFact()
                breedInfo.postValue(app.getString(R.string.loading))

                isBreedTextViewContainerGone.postValue(false)
                isFactTextViewContainerGone.postValue(false)

            }
            ApiInteraction.DOG_PREFIX -> {

                generalInfoTitle.postValue(app.getString(R.string.title_general, "Dog"))
                generalInfo.postValue(app.getString(R.string.dog_info_text))
                ApiInteraction.loadDogBreedInfo(currentBreedLocal.second, onSuccess = this::showDogBreedInfo, onError = this::showTextLoadError)
                loadDogFact()
                breedInfo.postValue(app.getString(R.string.loading))

                isBreedTextViewContainerGone.postValue(false)
                isFactTextViewContainerGone.postValue(false)

            }

        }

    }

    private fun loadImageByBreedApiId(animalPrefix: Int, apiId: String){

        when(animalPrefix){

            ApiInteraction.DOG_PREFIX -> {
                ApiInteraction.loadDogBreedImage(apiId, onSuccess = this::showDogBreedImage, onError = this::showImageLoadError)
            }
            ApiInteraction.CAT_PREFIX -> {
                ApiInteraction.loadCatBreedImage(apiId, onSuccess = this::showCatBreedImage, onError = this::showImageLoadError)
            }

        }

    }

    fun loadCatFact(){
        ApiInteraction.loadCatFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    fun loadDogFact(){
        ApiInteraction.loadDogFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    private fun showDogBreedInfo(info: List<DogBreedInfo>) {

        val currentBreedLocal = currentBreed.value?:return
        val breedInfoText: String

        if(info.isNotEmpty()){

            breedInfoText = app.getString(R.string.breed_info_dog,
                info[0].name,
                info[0].breed_group,
                info[0].weight.metric,
                info[0].height.metric,
                info[0].life_span,
                info[0].temperament,
                info[0].bred_for)

            if(onlineImageEnabled){
                loadImageByBreedApiId(getAnimalTypePrefixFromId(currentBreedLocal.first), info[0].id.toString())
            }
            else{
                loadImageFromAssets(currentBreedLocal.first)
            }

        }
        else{
            breedInfoText = app.getString(R.string.api_data_empty, currentBreedLocal.second)
            loadImageFromAssets(currentBreedLocal.first)
        }

        breedInfoTitle.postValue(app.getString(R.string.title_breed_specific))
        breedInfo.postValue(breedInfoText)

    }

    private fun showCatBreedInfo(info: List<CatBreedInfo>) {

        val currentBreedLocal = currentBreed.value?:return
        val breedInfoText: String

        if(info.isNotEmpty()){

            breedInfoText = app.getString(R.string.breed_info_cat,
                info[0].name,
                info[0].origin,
                info[0].weight.metric,
                info[0].life_span,
                convertNumberScaleToText(info[0].adaptability),
                convertNumberScaleToText(info[0].energy_level),
                convertNumberScaleToText(info[0].social_needs),
                convertNumberScaleToText(info[0].child_friendly),
                convertNumberScaleToText(info[0].stranger_friendly),
                convertNumberScaleToText(info[0].dog_friendly),
                info[0].description,
                info[0].temperament)

            if(onlineImageEnabled){
                loadImageByBreedApiId(getAnimalTypePrefixFromId(currentBreedLocal.first), info[0].id.toString())
            }
            else{
                loadImageFromAssets(currentBreedLocal.first)
            }

        }
        else{
            breedInfoText = app.getString(R.string.api_data_empty, currentBreedLocal.second)
            loadImageFromAssets(currentBreedLocal.first)
        }

        breedInfoTitle.postValue(app.getString(R.string.title_breed_specific))
        breedInfo.postValue(breedInfoText)

    }

    private fun showDogBreedImage(data: List<DogBreedImage>) {

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

    private fun showCatBreedImage(data: List<CatBreedImage>) {

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

    fun getAnimalTypePrefixFromId(id: String): Int{
        return id[2].toString().toInt()
    }

    fun convertNumberScaleToText(value: Int): String{

        var result = ""

        when(value){

            1 ->{
                result = "low"
            }
            2 ->{
                result = "below average"
            }
            3 ->{
                result = "normal"
            }
            4 ->{
                result = "above average"
            }
            5 ->{
                result = "high"
            }

        }

        return result

    }

    fun pause() {
        TextToSpeechModel.stop()
    }

}
