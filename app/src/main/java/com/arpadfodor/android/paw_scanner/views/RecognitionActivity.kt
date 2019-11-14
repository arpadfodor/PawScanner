package com.arpadfodor.android.paw_scanner.views

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.RecognitionViewModel
import com.github.mikephil.charting.charts.PieChart
import com.google.android.material.floatingactionbutton.FloatingActionButton

class RecognitionActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewModel: RecognitionViewModel

    lateinit var floatingActionButtonSpeak: FloatingActionButton
    lateinit var collapsingImage: ImageView
    lateinit var mainPredictionLinearLayout: LinearLayout
    lateinit var topPredictionsListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_recognition)

        val toolbar = findViewById<Toolbar>(R.id.imageToolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            this.finish()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        collapsingImage = findViewById(R.id.imageViewCollapsing)
        floatingActionButtonSpeak = findViewById(R.id.fabSpeak)
        mainPredictionLinearLayout = findViewById(R.id.llMainPrediction)
        topPredictionsListView = findViewById(R.id.lvTopPredictions)

        floatingActionButtonSpeak.setOnClickListener {
            this.onClick(floatingActionButtonSpeak)
        }

        mainPredictionLinearLayout.setOnClickListener {
            this.onClick(mainPredictionLinearLayout)
        }

        val settings = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val onlineEnabled = settings.getBoolean(getString(R.string.PREFERENCE_KEY_ONLINE_IMAGE), false)

        viewModel = ViewModelProviders.of(this).get(RecognitionViewModel::class.java)
        viewModel.init(intent, onlineEnabled)
        viewModel.loadData()

        val textViewPredictionsChartTitle = findViewById<TextView>(R.id.tvPredictionsChartTitle)
        textViewPredictionsChartTitle.text = this.getString(R.string.prediction_chart_title)

        val chart = viewModel.buildStatisticsChart(findViewById<PieChart>(R.id.predictionStatsChart))
        chart.invalidate()

        supportActionBar?.title = viewModel.results[0].title

        val textViewTopPredictionsTitle = findViewById<TextView>(R.id.tvTopPredictionsTitle)
        val textViewDuration = findViewById<TextView>(R.id.tvDuration)
        val textViewMainPrediction = findViewById<TextView>(R.id.tvMainPrediction)
        val imageViewCapture = findViewById<ImageView>(R.id.ivCapture)

        textViewTopPredictionsTitle.text = this.getString(R.string.prediction_top_title, viewModel.sizeOfResults.toString())
        textViewDuration.text = this.getString(R.string.inference_duration, viewModel.inferenceDuration)
        textViewMainPrediction.text = viewModel.mainRecognitionText()
        imageViewCapture.setImageBitmap(viewModel.recognizedImage)

        val topPredictions = viewModel.resultsInString
        val adapter = ArrayAdapter<String>(this, R.layout.listview_row, R.id.tvItemName, topPredictions)
        topPredictionsListView.adapter = adapter

        topPredictionsListView.setOnItemClickListener{ parent, view, position, id ->

            val currentBreedId = viewModel.results[position].id
            val currentBreedTitle = viewModel.results[position].title
            viewModel.showBreedInfo(currentBreedId, currentBreedTitle)

        }

        subscribeToViewModel()

    }

    private fun subscribeToViewModel() {

        // Create the text observer which updates the UI in case of an inference result
        val imageObserver = Observer<Bitmap> { result ->
            // Update the UI, in this case, the ImageView
            collapsingImage.setImageBitmap(result)
            collapsingImage.invalidate()
        }

        // Observe the LiveData
        viewModel.image.observe(this, imageObserver)

    }

    public override fun onPause() {
        viewModel.pause()
        super.onPause()
    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.fabSpeak ->{
                viewModel.speakClicked()
            }

            R.id.llMainPrediction ->{
                viewModel.showBreedInfo(viewModel.results[0].id, viewModel.results[0].title)
            }

        }

    }

}
