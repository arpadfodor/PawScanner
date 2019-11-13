package com.arpadfodor.android.paw_scanner.views

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.BreedViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BreedActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewModel: BreedViewModel

    lateinit var floatingActionButtonSpeakBreedInfo: FloatingActionButton
    lateinit var floatingActionButtonSelectBreed: FloatingActionButton
    lateinit var collapsingImage: ImageView
    lateinit var textViewMainBreedInfo: TextView

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

        collapsingToolbarLayout = findViewById(R.id.collapsing_app_bar_layout)
        collapsingImage = findViewById(R.id.imageViewCollapsing)
        textViewMainBreedInfo = findViewById(R.id.tvMainBreedInfo)
        floatingActionButtonSpeakBreedInfo = findViewById(R.id.fabSpeakBreedInfo)
        floatingActionButtonSelectBreed = findViewById(R.id.fabSelectBreed)

        viewModel = ViewModelProviders.of(this).get(BreedViewModel::class.java)
        viewModel.init(intent)
        subscribeToViewModel()
        viewModel.loadData()

    }

    private fun subscribeToViewModel() {

        // Create the text observer which updates the UI in case of an inference result
        val titleObserver = Observer<String> { result ->
            // Update the UI, in this case, the Toolbar
            collapsingToolbarLayout.title = result
            collapsingToolbarLayout.invalidate()
            viewModel.setTextToBeSpoken()

            viewModel.loadData()

            if(result.isNullOrBlank()){
                viewModel.isSelectorDisplayed.postValue(true)
            }
            else{
                viewModel.isSelectorDisplayed.postValue(false)
            }

        }

        // Create the text observer which updates the UI in case of an inference result
        val breedTextObserver = Observer<String> { result ->
            // Update the UI, in this case, the TextView
            textViewMainBreedInfo.text = result
            textViewMainBreedInfo.invalidate()
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

        // Observe the LiveData
        viewModel.breedName.observe(this, titleObserver)
        viewModel.breedInfo.observe(this, breedTextObserver)
        viewModel.image.observe(this, imageObserver)
        viewModel.isSelectorDisplayed.observe(this, isSelectorDisplayedObserver)

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

        if(viewModel.isSelectorDisplayed.value == true && !viewModel.breedName.value.isNullOrBlank()){
            viewModel.isSelectorDisplayed.postValue(false)
        }
        else{
            super.onBackPressed()
        }

    }

}
