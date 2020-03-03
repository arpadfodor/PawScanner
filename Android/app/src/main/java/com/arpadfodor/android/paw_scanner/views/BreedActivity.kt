package com.arpadfodor.android.paw_scanner.views

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.BreedViewModel
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BreedActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewModel: BreedViewModel

    lateinit var textViewMainBreedInfoContainer: LinearLayout
    lateinit var textViewFactContainer: LinearLayout

    lateinit var floatingActionButtonSpeakBreedInfo: FloatingActionButton
    lateinit var floatingActionButtonSelectBreed: FloatingActionButton
    lateinit var collapsingImage: ImageView
    lateinit var textViewMainBreedInfo: TextView
    lateinit var textViewGeneralInfo: TextView
    lateinit var textViewFact: TextView
    lateinit var textViewMainBreedInfoTitle: TextView
    lateinit var textViewGeneralInfoTitle: TextView
    lateinit var textViewFactTitle: TextView

    lateinit var toolbar: Toolbar
    lateinit var collapsingToolbarLayout: CollapsingToolbarLayout

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_breed)

        toolbar = findViewById(R.id.imageToolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            this.finish()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        textViewMainBreedInfoContainer = findViewById(R.id.tvMainBreedInfoContainer)
        textViewFactContainer = findViewById(R.id.tvFactContainer)

        collapsingToolbarLayout = findViewById(R.id.collapsing_app_bar_layout)
        collapsingImage = findViewById(R.id.imageViewCollapsing)
        textViewMainBreedInfo = findViewById(R.id.tvMainBreedInfo)
        textViewGeneralInfo = findViewById(R.id.tvGeneralInfo)
        textViewFact = findViewById(R.id.tvFact)
        textViewMainBreedInfoTitle = findViewById(R.id.tvMainBreedInfoTitle)
        textViewGeneralInfoTitle = findViewById(R.id.tvGeneralInfoTitle)
        textViewFactTitle = findViewById(R.id.tvFactTitle)
        floatingActionButtonSpeakBreedInfo = findViewById(R.id.fabSpeakBreedInfo)
        floatingActionButtonSelectBreed = findViewById(R.id.fabSelectBreed)

        viewModel = ViewModelProviders.of(this).get(BreedViewModel::class.java)
        viewModel.init(intent)
        subscribeToViewModel()
        viewModel.loadData()

    }

    private fun subscribeToViewModel() {

        // Create the text observer which updates the UI in case of an inference result
        val titleObserver = Observer<Pair<String, String>> { result ->
            // Update the UI, in this case, the Toolbar
            collapsingToolbarLayout.title = result.second
            collapsingToolbarLayout.invalidate()
            viewModel.setTextToBeSpoken()

            viewModel.loadData()

            if(result.second.isBlank()){
                viewModel.isSelectorDisplayed.postValue(true)
            }
            else{
                viewModel.isSelectorDisplayed.postValue(false)
            }

        }

        // Create the text observer which updates the UI in case of an inference result
        val breedTextTitleObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewMainBreedInfoTitle.text = result
            textViewMainBreedInfoTitle.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the text observer which updates the UI in case of an inference result
        val breedTextObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewMainBreedInfo.text = result
            textViewMainBreedInfo.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the text observer which updates the UI
        val generalTextTitleObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewGeneralInfoTitle.text = result
            textViewGeneralInfoTitle.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the text observer which updates the UI
        val generalTextObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewGeneralInfo.text = result
            textViewGeneralInfo.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the text observer which updates the UI in case of fact message change
        val factTitleObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewFactTitle.text = result
            textViewFactTitle.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the text observer which updates the UI in case of fact message change
        val factObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewFact.text = result
            textViewFact.invalidate()
            viewModel.setTextToBeSpoken()
        }

        // Create the image observer which updates the UI in case of an inference result
        val imageObserver = Observer<Bitmap> { result ->
            // Update the UI, in this case, the ImageView
            collapsingImage.setImageBitmap(result)
            collapsingImage.invalidate()
        }

        // Create the image observer which updates the UI in case of an inference result
        val isSelectorDisplayedObserver = Observer<Boolean> { result ->

            if(result == true){
                supportFragmentManager.beginTransaction().add(R.id.breed_activity_layout, SelectorFragment.newInstance()).commit()
                floatingActionButtonSpeakBreedInfo.setOnClickListener {}
                floatingActionButtonSelectBreed.setOnClickListener {}
            }
            else{

                floatingActionButtonSpeakBreedInfo.setOnClickListener {
                    this.onClick(floatingActionButtonSpeakBreedInfo)
                }

                floatingActionButtonSelectBreed.setOnClickListener {
                    this.onClick(floatingActionButtonSelectBreed)
                }

            }

        }

        // Create the observer which hides/shows breed text view
        val breedTextViewContainerVisibilityObserver = Observer<Boolean> { result ->

            // Update the UI, in this case, the TextView
            if(result){
                textViewMainBreedInfoContainer.visibility = View.GONE
                textViewMainBreedInfoContainer.invalidate()
            }
            else{
                textViewMainBreedInfoContainer.visibility = View.VISIBLE
                textViewMainBreedInfoContainer.invalidate()
            }

        }

        // Create the observer which hides/shows fact text view
        val factTextViewContainerVisibilityObserver = Observer<Boolean> { result ->

            // Update the UI, in this case, the TextView
            if(result){
                textViewFactContainer.visibility = View.GONE
                textViewFactContainer.invalidate()
            }
            else{
                textViewFactContainer.visibility = View.VISIBLE
                textViewFactContainer.invalidate()
            }

        }

        // Observe the LiveData
        viewModel.currentBreed.observe(this, titleObserver)
        viewModel.breedInfoTitle.observe(this, breedTextTitleObserver)
        viewModel.breedInfo.observe(this, breedTextObserver)
        viewModel.generalInfoTitle.observe(this, generalTextTitleObserver)
        viewModel.generalInfo.observe(this, generalTextObserver)
        viewModel.factTextTitle.observe(this, factTitleObserver)
        viewModel.factText.observe(this, factObserver)
        viewModel.image.observe(this, imageObserver)
        viewModel.isSelectorDisplayed.observe(this, isSelectorDisplayedObserver)
        viewModel.isBreedTextViewContainerGone.observe(this, breedTextViewContainerVisibilityObserver)
        viewModel.isFactTextViewContainerGone.observe(this, factTextViewContainerVisibilityObserver)

    }

    fun showSelector(){
        viewModel.isSelectorDisplayed.postValue(true)
    }

    public override fun onPause() {
        viewModel.pause()
        super.onPause()
    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.fabSpeakBreedInfo ->{
                viewModel.speakClicked()
            }

            R.id.fabSelectBreed ->{
                if(!viewModel.isSelectorDisplayed.value!!){
                    showSelector()
                }
            }

        }

    }

    override fun onBackPressed() {

        if(viewModel.isSelectorDisplayed.value == true && !viewModel.currentBreed.value?.second.isNullOrBlank()){
            viewModel.isSelectorDisplayed.postValue(false)
        }
        else{
            super.onBackPressed()
        }

    }

    override fun onResume() {

        super.onResume()

        val settings = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        viewModel.onlineImageEnabled = settings.getBoolean(getString(R.string.PREFERENCE_KEY_ONLINE_IMAGE), false)

    }

}
