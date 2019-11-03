package com.arpadfodor.android.paw_scanner.views

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.Recognition

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

        val inferenceDuration = intent.getLongExtra("inferenceTime", 0)
        val sizeOfResults = intent.getIntExtra("numberOfRecognitions", 0)

        val results = arrayListOf<Recognition>()

        for(index in 0 until sizeOfResults){

            val id = intent.getStringExtra("recognition-id-$index")
            val title = intent.getStringExtra("recognition-title-$index")
            val confidence = intent.getFloatExtra("recognition-confidence-$index", 0f)

            results.add(Recognition(id, title, confidence, null))

        }

        showRecognitionDetails(inferenceDuration, results)

    }

    fun showRecognitionDetails(duration: Long, result: List<Recognition>){

        supportActionBar?.title = result[0].title

        val textViewPredictionsTitle = findViewById<TextView>(R.id.tvPredictionsTitle)
        val textViewPredictions = findViewById<TextView>(R.id.tvPredictions)
        val textViewDuration = findViewById<TextView>(R.id.tvDuration)

        textViewPredictionsTitle.text = this.getString(R.string.predictions)
        textViewDuration.text = this.getString(R.string.inference_duration, duration)

        var predictions = ""

        //other results
        for(element in result){
            predictions += element.toString() + "\n"
        }

        textViewPredictions.text = predictions



    }

}
