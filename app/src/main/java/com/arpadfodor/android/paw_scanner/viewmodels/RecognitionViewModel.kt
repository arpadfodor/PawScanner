package com.arpadfodor.android.paw_scanner.viewmodels

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.Recognition
import com.arpadfodor.android.paw_scanner.models.TextToSpeechModel
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlin.math.min

class RecognitionViewModel(application: Application) : AndroidViewModel(application){

    companion object{
        const val MIN_PREDICTION_PERCENTAGE_TO_PAY_ATTENTION = 5
    }

    var app: Application = application

    lateinit var bitmap: Bitmap

    var inferenceDuration = 0L
    var sizeOfResults = 0

    lateinit var results: ArrayList<Recognition>

    val colorSet = listOf(Color.GRAY, Color.DKGRAY, Color.LTGRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE, Color.BLACK)

    var textToBeSpoken = ""

    fun init(intent: Intent){

        TextToSpeechModel.init(app.applicationContext)

        bitmap = BitmapFactory.decodeByteArray(
            intent.getByteArrayExtra("byteArray"),
            0,
            intent.getByteArrayExtra("byteArray")!!.size)

        inferenceDuration = intent.getLongExtra("inferenceTime", 0)
        sizeOfResults = intent.getIntExtra("numberOfRecognitions", 0)

        results = arrayListOf()

        for(index in 0 until sizeOfResults){

            val id = intent.getStringExtra("recognition-id-$index")
            val title = intent.getStringExtra("recognition-title-$index")
            val confidence = intent.getFloatExtra("recognition-confidence-$index", 0f)

            results.add(Recognition(id?:"", title?:"", confidence, null))

        }

        setTextToBeSpoken()

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
        // disable rotation of the chart by touch
        chart.isRotationEnabled = false
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
                recognitionsToMention = recognitionsToMention.plus((recognition.title + ". " + currentPercentage + " percent. "))
            }

        }

        //Only one prediction exists to mention
        if(counter == 1){

            spokenText += "The following creature recognized: "

            for(recognition in recognitionsToMention){
                spokenText += recognition
            }

        }
        //More predictions exist to mention
        else{

            spokenText += "The predictions are the following. "

            for((i, recognition) in recognitionsToMention.withIndex()){
                spokenText += "Number " + (i+1).toString() + ": " + recognition
            }

            spokenText += "If the recognized creature is a mixed breed, it is likely to be a mixture of the mentioned breeds."

        }

        textToBeSpoken = spokenText

    }

    fun mainRecognitionText(): String{

        return if(results[0].title == "human"){
            app.getString(R.string.human_but_text, results[0].toString(), results[1].toString())
        } else{
            app.getString(R.string.result_text, results[0].toString())
        }

    }

    fun predictionsToText(): String{

        var predictions = ""

        //other results
        for(element in results){
            predictions += element.toString() + "\n"
        }

        return predictions

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
