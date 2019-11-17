package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.ai.Recognition
import com.arpadfodor.android.paw_scanner.models.TextToSpeechModel
import com.arpadfodor.android.paw_scanner.models.api.ApiInteraction
import com.arpadfodor.android.paw_scanner.models.api.BreedImage
import com.arpadfodor.android.paw_scanner.models.api.BreedInfo
import com.arpadfodor.android.paw_scanner.views.BreedActivity
import com.bumptech.glide.Glide
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

class RecognitionViewModel(application: Application) : AndroidViewModel(application){

    companion object{
        const val MIN_PREDICTION_PERCENTAGE_TO_PAY_ATTENTION = 5
    }

    var app: Application = application

    lateinit var recognizedImage: Bitmap

    var placeholderImage: Bitmap? = null

    var onlineImageEnabled = false

    /*
     * Breed image sample
     */
    val image: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    var inferenceDuration = 0L
    var sizeOfResults = 0

    lateinit var results: ArrayList<Recognition>
    lateinit var resultsInString: ArrayList<String>

    val colorSet = listOf(Color.GRAY, Color.DKGRAY, Color.LTGRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE, Color.BLACK)

    var textToBeSpoken = ""

    fun init(intent: Intent, onlineEnabled: Boolean){

        TextToSpeechModel.init(app.applicationContext)

        recognizedImage = BitmapFactory.decodeByteArray(
            intent.getByteArrayExtra(app.getString(R.string.KEY_IMAGE_BYTE_ARRAY)),
            0,
            intent.getByteArrayExtra(app.getString(R.string.KEY_IMAGE_BYTE_ARRAY))!!.size)

        inferenceDuration = intent.getLongExtra(app.getString(R.string.KEY_INFERENCE_TIME), 0)
        sizeOfResults = intent.getIntExtra(app.getString(R.string.KEY_NUM_OF_RECOGNITIONS), 0)

        results = arrayListOf()
        resultsInString = arrayListOf()

        for(index in 0 until sizeOfResults){

            val id = intent.getStringExtra(app.getString(R.string.KEY_RECOGNITION_ID, index))
            val title = intent.getStringExtra(app.getString(R.string.KEY_RECOGNITION_TITLE, index))
            val confidence = intent.getFloatExtra(app.getString(R.string.KEY_RECOGNITION_CONFIDENCE, index), 0f)

            val currentRecognitionToAdd = Recognition(
                id ?: "",
                title ?: "",
                confidence,
                null
            )

            results.add(currentRecognitionToAdd)
            resultsInString.add(currentRecognitionToAdd.toString())

        }

        loadPlaceholderImage(R.drawable.paw_scanner)
        setTextToBeSpoken()

        onlineImageEnabled = onlineEnabled

    }

    fun loadData(){

        val breedToLoad = results[0]

        if(onlineImageEnabled){
            ApiInteraction.loadBreedInfo(breedToLoad.title, onSuccess = this::loadBreedImage, onError = this::showError)
        }
        else{
            loadImageFromAssets(breedToLoad.id)
        }

    }

    private fun loadBreedImage(info: List<BreedInfo>) {

        if(info.isNotEmpty()){
            ApiInteraction.loadBreedImage(info[0].id.toString(), onSuccess = this::showBreedImage, onError = this::showError)
        }
        else{
            loadImageFromAssets(results[0].id)
        }

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
            loadImageFromAssets(results[0].id)
        }

    }

    private fun showError(e: Throwable) {
        e.printStackTrace()
        loadImageFromAssets(results[0].id)

    }

    /**
     * Returns the recognition result chart
     *
     * @param     chart             PieChart to build up
     *
     * @return    PieChart          PieChart to show
     */
    fun buildStatisticsChart(chart: PieChart): PieChart{

        val predictionPieData = ArrayList<PieEntry>(results.size)
        val predictionColors = ArrayList<Int>(results.size)
        val dataLabels = ArrayList<String>()

        chart.tag = app.getString(R.string.prediction_chart_title)

        chart.description.isEnabled = false
        chart.setUsePercentValues(true)
        chart.setExtraOffsets(5.toFloat(), 10.toFloat(), 5.toFloat(), 5.toFloat())
        chart.dragDecelerationFrictionCoef = 0.95f
        chart.rotationAngle = 0.toFloat()
        //enable rotation of the chart by touch
        chart.isRotationEnabled = true
        chart.isHighlightPerTapEnabled = true
        chart.animateY(1000, Easing.EaseInOutQuad)

        chart.isDrawHoleEnabled = true
        chart.setHoleColor(app.resources.getColor(R.color.colorPrimary))
        chart.setTransparentCircleColor(app.resources.getColor(R.color.colorPrimary))
        chart.setTransparentCircleAlpha(110)
        chart.holeRadius = 58f
        chart.transparentCircleRadius = 60f

        val legend = chart.legend
        legend.isEnabled = true
        legend.isWordWrapEnabled = true
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.xEntrySpace = 5f
        legend.yEntrySpace = 0f
        legend.yOffset = 0f
        legend.textColor = app.resources.getColor(R.color.colorText)
        legend.textSize = 12f
        legend.formSize = 10f
        legend.form = Legend.LegendForm.SQUARE

        var predictionsSum = 0f

        for ((i, prediction) in results.withIndex()){

            val currentPercentage = prediction.confidence*100

            if(currentPercentage >= MIN_PREDICTION_PERCENTAGE_TO_PAY_ATTENTION){

                predictionPieData.add(PieEntry(prediction.confidence, prediction.title))
                predictionColors.add(colorSet[min(i, colorSet.size-1)])
                dataLabels.add(prediction.title)

                predictionsSum += prediction.confidence

            }

        }

        //Add the others category as well to have the total 100% on the pie chart
        predictionPieData.add(PieEntry(1f - predictionsSum, app.getString(R.string.category_others)))
        predictionColors.add(colorSet[min(results.size, colorSet.size-1)])
        dataLabels.add(app.getString(R.string.category_others))

        val set = PieDataSet(predictionPieData, "")
        set.colors = predictionColors

        val data = PieData(set)
        data.setValueTextColor(app.resources.getColor(R.color.colorText))
        data.setValueTextSize(12f)

        chart.data = data

        return chart

    }

    private fun setTextToBeSpoken(){

        var spokenText = ""
        var counter = 0
        var recognitionsToMention = emptyArray<String>()

        for(recognition in results){

            val currentPercentage = (recognition.confidence*100).toInt()

            //If the current percentage is greater than the threshold, it will be spoken
            if(currentPercentage >= MIN_PREDICTION_PERCENTAGE_TO_PAY_ATTENTION){
                counter++
                recognitionsToMention = recognitionsToMention.plus(app.getString(R.string.tts_recognition_element, recognition.title, currentPercentage.toString()))
            }

        }

        when (counter) {

            //Every prediction was weak, mention the highest one to tell something
            0 -> {
                spokenText += app.getString(R.string.tts_too_weak_recognition_start_text, MIN_PREDICTION_PERCENTAGE_TO_PAY_ATTENTION.toString())
                spokenText += app.getString(R.string.tts_recognition_element, results[0].title, results[0].toString())
            }

            //Only one prediction to mention
            1 -> {

                spokenText += app.getString(R.string.tts_singular_recognition_start_text)

                for(recognition in recognitionsToMention){
                    spokenText += recognition
                }

            }

            //More predictions to mention
            else -> {

                spokenText += app.getString(R.string.tts_plural_recognitions_start_text)

                for((i, recognition) in recognitionsToMention.withIndex()){
                    spokenText += app.getString(R.string.tts_element_order, (i+1).toString(), recognition)
                }

                spokenText += app.getString(R.string.tts_if_mixed_text)

            }

        }

        textToBeSpoken = spokenText

    }

    fun mainRecognitionText(): String{

        return if(results[0].title == app.getString(R.string.human)){
            app.getString(R.string.human_but_text, results[0].toString(), results[1].toString())
        } else{
            app.getString(R.string.result_text, results[0].toString())
        }

    }

    fun speakClicked(){

        if(TextToSpeechModel.isSpeaking()){
            TextToSpeechModel.stop()
        }

        else{
            TextToSpeechModel.speak(textToBeSpoken)
        }

    }

    fun showBreedInfo(id: String, title: String){

        val intent = Intent(app.applicationContext, BreedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(app.getString(R.string.KEY_BREED_ID), id)
            putExtra(app.getString(R.string.KEY_BREED_TITLE), title)
        }
        ContextCompat.startActivity(app.applicationContext, intent, null)

    }

    fun pause() {
        TextToSpeechModel.stop()
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

}
