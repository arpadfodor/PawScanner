package com.arpadfodor.android.paw_scanner.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Size
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.model.*
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object{
        const val MINIMUM_PREVIEW_SIZE = 320
        const val MAINTAIN_ASPECT = true
    }

    var app: Application = application

    /**
     * The rotation in degrees of the camera sensor from the display
     */
    val saveImageBasename = app.resources.getString(R.string.app_name)

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of containing a DESIRED_SIZE x DESIRED_SIZE square
     */
    var textureViewSize = Size(0,0)

    /**
     * The desired camera preview size
     */
    val desiredPreviewSize = Size(640, 480)

    /**
     * The rotation in degrees of the camera sensor from the display
     */
    var sensorOrientation = 0

    /**
     * Whether the current camera device supports Flash or not
     */
    var flashSupported = false

    /**
     * Is camera opened flag
     */
    var cameraOpened = false

    /*
     * Whether inference has finished or not
     */
    var isInferenceFinished = true

    /*
     * Whether live inference is enabled or not
     */
    var liveInferenceEnabled = false

    /*
     * Whether load inference is enabled or not
     */
    var loadInferenceEnabled = false

    /*
     * Whether history inference is enabled or not
     */
    var historyInferenceEnabled = false

    var availableCameras = arrayOfNulls<String>(0)
    var previewSize = Size(0,0)

    lateinit var recognitionResultReceiver: BroadcastReceiver

    /*
     * The Classifier
     */
    lateinit var classifier: Classifier

    /*
     * The workManager that calculates the results
     */
    private val inferenceManager = WorkManager.getInstance(application)

    /*
     * Current data to show
     * [0]: inference duration
     * [1]: most probable recognition info
     * [2]: whole prediction info
     */
    val currentDataToShow: MutableLiveData<List<String>> by lazy {
        MutableLiveData<List<String>>()
    }

    /*
     * Last classification result
     */
    val result: MutableLiveData<List<Recognition>> by lazy {
        MutableLiveData<List<Recognition>>()
    }

    /*
     * Last live inference time in milliseconds
     */
    val inferenceDuration: MutableLiveData<Long> by lazy {
        MutableLiveData<Long>()
    }

    /*
     * The loaded image to feed
     */
    val loadedImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    /*
     * The live image
     */
    val liveImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
    }

    /*
     * The classifier input size
     */
    val classifierInputSize: MutableLiveData<Size> by lazy {
        MutableLiveData<Size>()
    }

    /*
     * The screen rotation of the parent Activity
     */
    val rotation: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    /*
     * The current camera index
     */
    val currentCameraIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    fun init(parentScreenOrientation: Int){

        classifier = ClassifierFloatMobileNet(app.assets, Device.CPU, 1)
        classifierInputSize.value = Size(classifier.getImageSizeX(), classifier.getImageSizeY())
        rotation.value = parentScreenOrientation
        isInferenceFinished = true
        currentCameraIndex.value = 0

        recognitionResultReceiver = object : BroadcastReceiver(){

            override fun onReceive(context: Context, intent: Intent) {

                inferenceDuration.value = intent.getLongExtra("inferenceTime", 0)
                val sizeOfResults = intent.getIntExtra("numberOfRecognitions", 0)

                val results = arrayListOf<Recognition>()

                for(index in 0 until sizeOfResults){

                    val id = intent.getStringExtra("recognition-id-$index")
                    val title = intent.getStringExtra("recognition-title-$index")
                    val confidence = intent.getFloatExtra("recognition-confidence-$index", 0f)

                    results.add(Recognition(id, title, confidence, null))

                }

                result.value = results
                isInferenceFinished = true

                //notify the activity to show results
                updateCurrentInfo(inferenceDuration.value, result.value)

            }

        }

        LocalBroadcastManager.getInstance(app.applicationContext).registerReceiver(
            recognitionResultReceiver, IntentFilter("InferenceResult")
        )

        InferenceService.viewModel = this

    }

    /**
     * Sets up member variables related to camera
     */
    fun setUpCameras() {

        val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        availableCameras = manager.cameraIdList

        val characteristics = manager.getCameraCharacteristics(availableCameras[currentCameraIndex.value!!]!!)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        // Check if the flash is supported
        flashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true


        // Attempting to use too large a preview size could exceed camera bus bandwidth limitation
        // Can result in gorgeous previews but the storage of garbage capture data
        previewSize = chooseOptimalSize(map!!.getOutputSizes(SurfaceTexture::class.java), classifierInputSize.value!!)

        //Fit the aspect ratio of TextureView to the size of preview picked
        val orientation = app.resources.configuration.orientation

        textureViewSize = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Size(previewSize.width, previewSize.height)
        } else {
            Size(previewSize.height, previewSize.width)
        }

    }

    /**
     * Given choices of Sizes supported by a camera, chooses the smallest one whose width and height are at least as large as the minimum of both, or an exact match if possible
     *
     * @param choices       The list of sizes that the camera supports for the intended output class
     * @param dimensions    The minimum desired width & height
     *
     * @return Size         The optimal Size, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(choices: Array<Size>, dimensions: Size): Size {

        val minSize = max(min(dimensions.width, dimensions.height), MINIMUM_PREVIEW_SIZE)

        // Collect the supported resolutions that are at least as big as the preview Surface
        var exactSizeFound = false
        val bigEnough = ArrayList<Size>()
        val tooSmall = ArrayList<Size>()
        for (option in choices) {

            if (option == dimensions) {
                // Set the size but don't return yet so that remaining sizes will still be logged
                exactSizeFound = true
            }
            if (option.height >= minSize && option.width >= minSize) {
                bigEnough.add(option)
            } else {
                tooSmall.add(option)
            }

        }

        if (exactSizeFound) {
            return dimensions
        }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.size > 0) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }

    }

    fun changeCamera(){

        if(availableCameras.isEmpty()){
            return
        }

        if(availableCameras.size > currentCameraIndex.value!!){
            currentCameraIndex.value = currentCameraIndex.value!!.inc()
            if(availableCameras.size <= currentCameraIndex.value!!){
                currentCameraIndex.value = 0
            }
        }

        currentCameraIndex.value = currentCameraIndex.value!!

    }

    fun setLoadedImage(bitmap: Bitmap){

        loadedImage.value = bitmap

        if(!loadInferenceEnabled){
            return
        }

        recognizeImage(2)

    }

    fun setLiveImage(bitmap: Bitmap?){

        bitmap?: return

        if(!liveInferenceEnabled){
            return
        }

        liveImage.value = bitmap
        recognizeImage(1)

    }

    private fun recognizeImage(recognitionTypeId: Int){

        if(!isInferenceFinished){
            return
        }

        isInferenceFinished = false

        val intent = Intent(app.applicationContext, InferenceService::class.java)
        intent.putExtra("type", recognitionTypeId)
        app.applicationContext.startService(intent)

    }

    private fun updateCurrentInfo(duration: Long?, result: List<Recognition>?){

        val dataToInsert = arrayListOf<String>()

        if(duration == null || result == null || result.isEmpty()){
            dataToInsert.add("")
            dataToInsert.add("")
            dataToInsert.add("")
            currentDataToShow.value = dataToInsert
            return
        }

        //inference duration
        dataToInsert.add(app.getString(R.string.inference_duration, duration))

        //most possible result
        dataToInsert.add(result[0].toString())

        var predictions = app.getString(R.string.predictions)

        //other results
        for(i in (result.size-1) downTo 0){
            predictions += result[i].toString() + "\n"
        }

        dataToInsert.add(predictions)

        currentDataToShow.value = dataToInsert

    }

    /**
     * Conversion from screen rotation to JPEG orientation
     */
    fun getScreenOrientation(): Int{
        return when (rotation.value) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    fun activateLiveMode(){
        loadInferenceEnabled = false
        historyInferenceEnabled = false
        liveInferenceEnabled = true
        isInferenceFinished = true
        //notify the activity to show results
        //recognizeImage(1)
    }

    fun activateLoadMode(){
        liveInferenceEnabled = false
        historyInferenceEnabled = false
        loadInferenceEnabled = true
        isInferenceFinished = true
        //notify the activity to show results
        recognizeImage(2)
    }

    fun activateHistoryMode(){
        liveInferenceEnabled = false
        loadInferenceEnabled = false
        historyInferenceEnabled = true
        isInferenceFinished = true
        //notify the activity to show results
        updateCurrentInfo(0, emptyList())
    }

    fun disableInference(){
        liveInferenceEnabled = false
        loadInferenceEnabled = false
        historyInferenceEnabled = false
        isInferenceFinished = false
        //notify the activity to show results
        updateCurrentInfo(0, emptyList())
    }

}

/**
 * Compares two Sizes based on their areas
 */
internal class CompareSizesByArea : Comparator<Size> {

    override fun compare(lhs: Size, rhs: Size): Int {
        //Cast to ensure the multiplications won't overflow
        return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
    }

}
