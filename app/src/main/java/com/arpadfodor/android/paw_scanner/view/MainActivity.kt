package com.arpadfodor.android.paw_scanner.view

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.MenuItem
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val PERMISSIONS_REQUEST = 1
        private val PERMISSION_CAMERA = Manifest.permission.CAMERA
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        supportActionBar?.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.custom_app_bar)

        setContentView(R.layout.main_activity)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        viewModel.init(windowManager.defaultDisplay.rotation)
        //subscribeUi()

        requestPermission()

        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        //Passing each menu Id as a set of Ids because each menu should be considered as top level destinations
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_history, R.id.navigation_live, R.id.navigation_load)
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

    private fun subscribeUi() {

        // Create the camera index observer
        val cameraIndexObserver = Observer<Int> { result ->

            val newFragment = CameraFragment()
            val transaction = supportFragmentManager.beginTransaction()
            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack if needed
            transaction.replace(R.id.nav_host_fragment, newFragment)
            // Commit the transaction
            transaction.commit()

        }
        // Observe the LiveData, passing in this viewLifeCycleOwner as the LifecycleOwner and the observer
        viewModel.currentCameraIndex.observe(this, cameraIndexObserver)

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (!hasPermission()) {
                requestPermission()
            }
        }
    }

    private fun hasPermission(): Boolean {
        return applicationContext.checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(applicationContext, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
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
        if(mainActivityDrawerLayout.isDrawerOpen(GravityCompat.START)){
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

        val exitDialog = CustomDialog(this, getString(R.string.confirm_exit), getString(R.string.confirm_exit_description), resources.getDrawable(R.drawable.paw_icon))
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
