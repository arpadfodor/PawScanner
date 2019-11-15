package com.arpadfodor.android.paw_scanner.views

import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.api.ApiInteraction
import com.arpadfodor.android.paw_scanner.models.api.Fact
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.default_app_bar.*

class FactActivity : AppCompatActivity() {

    lateinit var factTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_fact)
        setSupportActionBar(toolbarNormal)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbarNormal.setNavigationOnClickListener {
            this.finish()
        }

        val fabCat = findViewById<FloatingActionButton>(R.id.fabCatFact)
        val fabDog = findViewById<FloatingActionButton>(R.id.fabDogFact)

        factTextView = findViewById(R.id.tvFact)
        factTextView.text = getString(R.string.fact, getString(R.string.fact_get_help))

        fabCat.setOnClickListener { view ->

            loadCatFact()

            Snackbar.make(view, getString(R.string.loading), Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()

        }

        fabDog.setOnClickListener { view ->

            loadDogFact()

            Snackbar.make(view, getString(R.string.loading), Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()

        }

    }

    fun loadCatFact(){
        ApiInteraction.loadCatFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    fun loadDogFact(){
        ApiInteraction.loadDogFact(onSuccess = this::showFact, onError = this::showFactLoadError)
    }

    private fun showFact(fact: Fact){
        factTextView.text = (getString(R.string.fact, fact.fact))
    }

    private fun showFactLoadError(e: Throwable) {
        e.printStackTrace()
        val fact: String = getString(R.string.fact, getString(R.string.internet_needed_to_fact))
        factTextView.text = (fact)
    }

}
