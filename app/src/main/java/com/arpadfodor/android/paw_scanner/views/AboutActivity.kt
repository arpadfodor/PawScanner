package com.arpadfodor.android.paw_scanner.views

import android.os.Bundle
import android.view.WindowManager
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.arpadfodor.android.paw_scanner.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.default_app_bar.*

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_about)
        setSupportActionBar(toolbarNormal)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbarNormal.setNavigationOnClickListener {
            this.finish()
        }

        val fabMessage = findViewById<FloatingActionButton>(R.id.fabMessage)
        val fabBugReport = findViewById<FloatingActionButton>(R.id.fabBugReport)

        fabMessage.setOnClickListener { view ->
            Snackbar.make(view, "Send message to developer", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        fabBugReport.setOnClickListener { view ->
            Snackbar.make(view, "Report a bug", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

    }
}
