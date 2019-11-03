package com.arpadfodor.android.paw_scanner.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.Recognition
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

class RecognitionActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var textToSpeech: TextToSpeech
    var textToSpeechRequestId = 0
    var textToBeSpoken = ""
    var minimumRecognitionPercentageToMention = 1

    lateinit var floatingActionButtonSpeak: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_recognition)

        //Set teyt to speech listeners
        textToSpeech = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.language = Locale.UK
                }
            })

        val toolbar = findViewById<Toolbar>(R.id.imageToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = ""

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val collapsingImage = findViewById<ImageView>(R.id.imageViewCollapsing)
        collapsingImage.setImageResource(R.drawable.paw)

        floatingActionButtonSpeak = findViewById(R.id.fabSpeak)

        floatingActionButtonSpeak.setOnClickListener {
            this.onClick(floatingActionButtonSpeak)
        }

        val bitmap = BitmapFactory.decodeByteArray(
            intent.getByteArrayExtra("byteArray"),
            0,
            intent.getByteArrayExtra("byteArray")!!.size)

        val inferenceDuration = intent.getLongExtra("inferenceTime", 0)
        val sizeOfResults = intent.getIntExtra("numberOfRecognitions", 0)

        val results = arrayListOf<Recognition>()

        for(index in 0 until sizeOfResults){

            val id = intent.getStringExtra("recognition-id-$index")
            val title = intent.getStringExtra("recognition-title-$index")
            val confidence = intent.getFloatExtra("recognition-confidence-$index", 0f)

            results.add(Recognition(id, title, confidence, null))

        }

        showRecognitionDetails(inferenceDuration, results, bitmap)
        displayStatisticsChart(results)
        setTextToBeSpoken(results)

    }

    fun showRecognitionDetails(duration: Long, result: List<Recognition>, image: Bitmap){

        supportActionBar?.title = result[0].title

        val textViewMixedBreedPredictionsTitle = findViewById<TextView>(R.id.tvMixedBreedPredictionsTitle)
        val textViewMixedBreedPredictions = findViewById<TextView>(R.id.tvMixedBreedPredictions)
        val textViewDuration = findViewById<TextView>(R.id.tvDuration)
        val textViewMainPrediction = findViewById<TextView>(R.id.tvMainPrediction)
        val imageViewCapture = findViewById<ImageView>(R.id.ivCapture)

        textViewMixedBreedPredictionsTitle.text = this.getString(R.string.prediction_mixed_breed_title)
        textViewDuration.text = this.getString(R.string.inference_duration, duration)

        if(result[0].title == "human"){
            textViewMainPrediction.text = this.getString(R.string.human_but_text, result[0].toString(), result[1].toString())
        }
        else{
            textViewMainPrediction.text = this.getString(R.string.result_text, result[0].toString())
        }

        var predictions = ""

        //other results
        for(element in result){
            predictions += element.toString() + "\n"
        }

        textViewMixedBreedPredictions.text = predictions

        imageViewCapture.setImageBitmap(image)

    }

    /**
     * Shows the recognition result chart
     *
     * @param     predictions       List of recognitions to show
     */
    private fun displayStatisticsChart(predictions: List<Recognition>){

        val textViewPredictionsChartTitle = findViewById<TextView>(R.id.tvPredictionsChartTitle)
        val chart = findViewById<PieChart>(R.id.predictionStatsChart)

        textViewPredictionsChartTitle.text = this.getString(R.string.prediction_stats_chart_title)

        val predictionPieData = ArrayList<PieEntry>(predictions.size)
        val predictionColors = ArrayList<Int>(predictions.size)
        val dataLabels = ArrayList<String>()

        chart.tag = getString(R.string.prediction_stats_chart_title)

        chart.description.isEnabled = false
        chart.setUsePercentValues(true)
        chart.setExtraOffsets(5.toFloat(), 10.toFloat(), 5.toFloat(), 5.toFloat())
        chart.dragDecelerationFrictionCoef = 0.95f
        chart.rotationAngle = 0.toFloat()
        // disable rotation of the chart by touch
        chart.isRotationEnabled = false
        chart.isHighlightPerTapEnabled = true
        chart.animateY(1000, Easing.EaseInOutQuad)

        chart.isDrawHoleEnabled = true
        chart.setHoleColor(resources.getColor(R.color.colorPrimary))
        chart.setTransparentCircleColor(resources.getColor(R.color.colorPrimary))
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
        legend.textColor = resources.getColor(R.color.colorText)
        legend.textSize = 12f
        legend.formSize = 10f
        legend.form = Legend.LegendForm.SQUARE

        val colorSet = listOf(Color.GRAY, Color.DKGRAY, Color.LTGRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE, Color.BLACK)

        var predictionsSum = 0f

        for ((i, prediction) in predictions.withIndex()){

            predictionPieData.add(PieEntry(prediction.confidence, prediction.title))
            predictionColors.add(colorSet[min(i, colorSet.size-1)])
            dataLabels.add(prediction.title)

            predictionsSum += prediction.confidence

        }

        //Add the others category as well to have the total 100% on the pie chart
        predictionPieData.add(PieEntry(1f - predictionsSum, getString(R.string.category_others)))
        predictionColors.add(colorSet[min(predictions.size, colorSet.size-1)])
        dataLabels.add(getString(R.string.category_others))

        val set = PieDataSet(predictionPieData, "")
        set.colors = predictionColors

        val data = PieData(set)
        data.setValueTextColor(resources.getColor(R.color.colorText))
        data.setValueTextSize(12f)

        chart.data = data
        chart.invalidate()

    }

    private fun setTextToBeSpoken(result: List<Recognition>){

        var spokenText = ""
        spokenText += "The results are the following: "

        for((i, recognition) in result.withIndex()){

            val currentPercentage = (recognition.confidence*100).toInt()

            //If the current percentage is greater than the threshold, it will be spoken
            if(currentPercentage > minimumRecognitionPercentageToMention){
                spokenText += ("Number " + (i+1).toString() + ": " + recognition.title + ". " + currentPercentage + " percent. ")
            }

        }

        spokenText += "If the recognized animal is a mixed breed, it is likely to be a mixture of the mentioned breeds."

        textToBeSpoken = spokenText

    }

    public override fun onPause() {

        //Stop text to speech
        textToSpeech.stop()
        textToSpeech.shutdown()

        super.onPause()
    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.fabSpeak ->{

                if(textToSpeech.isSpeaking){
                    textToSpeech.stop()
                }

                else{
                    textToSpeech.speak(textToBeSpoken, QUEUE_FLUSH, null, (textToSpeechRequestId++).toString())
                }
            }

        }
    }

}
