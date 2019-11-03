package com.arpadfodor.android.paw_scanner.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
import org.w3c.dom.Text
import kotlin.math.min

class RecognitionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_recognition)

        val toolbar = findViewById<Toolbar>(R.id.imageToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = ""

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val collapsingImage = findViewById<ImageView>(R.id.imageViewCollapsing)
        collapsingImage.setImageResource(R.drawable.paw)

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

    }

    fun showRecognitionDetails(duration: Long, result: List<Recognition>, image: Bitmap){

        supportActionBar?.title = result[0].title

        val textViewPredictionsTitle = findViewById<TextView>(R.id.tvPredictionsTitle)
        val textViewPredictions = findViewById<TextView>(R.id.tvPredictions)
        val textViewDuration = findViewById<TextView>(R.id.tvDuration)
        val textViewMainPrediction = findViewById<TextView>(R.id.tvMainPrediction)
        val imageViewCapture = findViewById<ImageView>(R.id.ivCapture)

        textViewPredictionsTitle.text = this.getString(R.string.predictions)
        textViewDuration.text = this.getString(R.string.inference_duration, duration)

        if(result[0].title == "human"){
            val looksLike = result[1]
            textViewMainPrediction.text = this.getString(R.string.human_but_text, result[0].toDetailedString() + "%", looksLike.toDetailedString())
        }
        else{
            textViewMainPrediction.text = this.getString(R.string.result_text, result[0].toDetailedString())
        }

        var predictions = ""

        //other results
        for(element in result){
            predictions += element.toString() + "\n"
        }

        textViewPredictions.text = predictions

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

        val colorSet = listOf(Color.GRAY, Color.GREEN, Color.DKGRAY, Color.RED, Color.BLACK, Color.BLUE, Color.YELLOW, Color.CYAN, Color.LTGRAY, Color.MAGENTA, Color.WHITE)

        for ((i, prediction) in predictions.withIndex()){
            predictionPieData.add(PieEntry(prediction.confidence, prediction.title))
            predictionColors.add(colorSet[min(i, colorSet.size-1)])
            dataLabels.add(prediction.title)
        }

        val set = PieDataSet(predictionPieData, getString(R.string.prediction_stats_chart_label, predictions.size))
        set.colors = predictionColors

        val data = PieData(set)
        data.setValueTextColor(resources.getColor(R.color.colorText))
        data.setValueTextSize(12f)

        chart.data = data
        chart.invalidate()

    }

}
