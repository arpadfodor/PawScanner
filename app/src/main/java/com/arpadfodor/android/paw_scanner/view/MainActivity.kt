package com.arpadfodor.android.paw_scanner.view

import android.app.ActivityOptions
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.view.additional.CustomDialog
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener{

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        supportActionBar?.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.custom_app_bar)

        setContentView(R.layout.main_activity)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        viewModel.loadClassifier()

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_history, R.id.navigation_live, R.id.navigation_load
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val toggle = ActionBarDrawerToggle(this, mainActivityDrawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        mainActivityDrawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        dashboard_navigation.setNavigationItemSelectedListener(this)
        val navigationView = findViewById<NavigationView>(R.id.dashboard_navigation)
        val header = navigationView?.getHeaderView(0)

    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        val fragmentToStart: Fragment

        when (item.itemId) {
            R.id.nav_breeds -> {
                fragmentToStart = BreedsFragment.newInstance()
            }
            R.id.nav_tips -> {
                fragmentToStart = TipsFragment.newInstance()
            }
            R.id.nav_about -> {
                fragmentToStart = AboutFragment.newInstance()
            }
            R.id.nav_settings -> {
                fragmentToStart = SettingsFragment.newInstance()
            }
            else ->{
                return false
            }
        }

        val transaction = supportFragmentManager.beginTransaction().apply {
            replace(R.id.nav_host_fragment, fragmentToStart)
        }
        // Commit the transaction
        transaction.commit()
        mainActivityDrawerLayout.closeDrawer(GravityCompat.START)
        return true

    }

    override fun onBackPressed() {

        if(mainActivityDrawerLayout.isDrawerOpen(Gravity.LEFT)){
            mainActivityDrawerLayout.closeDrawer(GravityCompat.START)
        }
        else{
            exitDialog()
        }

    }

    /**
     * Asks for exit confirmation
     */
    private fun exitDialog(){

        val exitDialog = CustomDialog(this, getString(R.string.confirm_exit), getString(R.string.confirm_exit_description), resources.getDrawable(R.drawable.warning))
        exitDialog.setPositiveButton {
            //showing the home screen - app is not visible but running
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
        }
        exitDialog.show()

    }

}
