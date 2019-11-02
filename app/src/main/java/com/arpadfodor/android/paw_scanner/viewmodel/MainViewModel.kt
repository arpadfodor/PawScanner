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
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.LENS_FACING
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraX
import androidx.core.content.ContextCompat.startActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.model.*
import com.arpadfodor.android.paw_scanner.view.RecognitionActivity
import com.arpadfodor.android.paw_scanner.viewmodel.workers.InferenceService
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import com.arpadfodor.android.paw_scanner.viewmodel.workers.InferenceWorker
import com.arpadfodor.android.paw_scanner.viewmodel.workers.LiveInferenceService
import java.io.ByteArrayOutputStream
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object{

        const val MAXIMUM_RECOGNITIONS_TO_SHOW = 3

        const val MINIMUM_PREVIEW_SIZE = 320
        const val MAINTAIN_ASPECT = true

        const val RECOGNITION_LIVE = 1
        const val RECOGNITION_LOAD = 2

        const val KEY_EVENT_ACTION = "key_event_action"
        const val KEY_EVENT_EXTRA = "key_event_extra"

        /**
         * Milliseconds used for UI animations
         */
        const val ANIMATION_FAST = 150L
        const val ANIMATION_SLOW = 200L

        const val shutterColor = Color.WHITE
        const val IMAGE_EXTENSION = ".jpg"

    }

    var app: Application = application

    /**
     * The base name of the image to save
     */
    val fileName = app.resources.getString(R.string.app_name)

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

    /*
     * List of available cameras
     */
    var availableCameras = arrayOfNulls<String>(0)

    lateinit var recognitionResultReceiver: BroadcastReceiver

    /*
     * The Classifier
     */
    lateinit var classifier: Classifier

    /*
     * The inferenceManager that manages inference work
     */
    var inferenceManager = WorkManager.getInstance()

    /*
     * Whether inference has finished or not
     */
    var isInferenceFinished: Boolean = true

    /**
     * The current selected camera preview size
     */
    val previewSize: MutableLiveData<Size> by lazy {
        MutableLiveData<Size>()
    }

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

    /*
     * The current camera orientation
     */
    val currentCameraOrientation: MutableLiveData<CameraX.LensFacing> by lazy {
        MutableLiveData<CameraX.LensFacing>()
    }

    fun init(parentScreenOrientation: Int){

        classifier = ClassifierFloatMobileNet(app.assets, Device.CPU, 1)
        classifierInputSize.value = Size(classifier.getImageSizeX(), classifier.getImageSizeY())
        rotation.value = parentScreenOrientation
        isInferenceFinished = true
        currentCameraIndex.value = 0
        currentCameraOrientation.value = CameraX.LensFacing.BACK

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
        InferenceWorker.viewModel = this
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


        // Attempting to use too large preview size could exceed camera bus bandwidth limitation
        // Can result in gorgeous previews but the storage of garbage capture data
        previewSize.value = chooseOptimalSize(map!!.getOutputSizes(SurfaceTexture::class.java), desiredPreviewSize)

        //Fit the aspect ratio of TextureView to the size of preview picked
        val orientation = app.resources.configuration.orientation

        val preview = previewSize.value?:return

        textureViewSize = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Size(preview.width, preview.height)
        } else {
            Size(preview.height, preview.width)
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

        // Pick the smallest of those, assuming there was a match
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

        val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(availableCameras[currentCameraIndex.value!!]!!)
        characteristics.get(LENS_FACING)

    }

    fun loadedImageInference(bitmap: Bitmap){

        loadedImage.value = bitmap

        if(!loadInferenceEnabled){
            return
        }

        recognizeImage(RECOGNITION_LOAD)

    }

    fun liveImageInference(bitmap: Bitmap?){

        bitmap?: return

        if(!liveInferenceEnabled){
            return
        }

        if(!isInferenceFinished){
            return
        }

        isInferenceFinished = false

        //val inference = OneTimeWorkRequestBuilder<InferenceWorker>().build()
        //inferenceManager.enqueue(inference)

        val intent = Intent(app.applicationContext, LiveInferenceService::class.java)
        val bs = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 99, bs)
        intent.putExtra("byteArray", bs.toByteArray())
        app.applicationContext.startService(intent)

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

        val showRecognitonFrom = result.size-1
        val showRecognitionTo = max(showRecognitonFrom - MAXIMUM_RECOGNITIONS_TO_SHOW, 0)

        //other results
        for(i in showRecognitonFrom downTo showRecognitionTo){
            predictions += result[i].toString() + "\n"
        }

        dataToInsert.add(predictions)

        currentDataToShow.value = dataToInsert

    }

    fun recognitionDetails(){

        val resultToSend = result.value!!

        val intent = Intent(app.applicationContext, RecognitionActivity::class.java).apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            putExtra("inferenceTime", inferenceDuration.value)
            putExtra("numberOfRecognitions", resultToSend.size)

            for((index, recognition) in resultToSend.withIndex()){
                putExtra("recognition-id-$index", recognition.id)
                putExtra("recognition-title-$index", recognition.title)
                putExtra("recognition-confidence-$index", recognition.confidence)
            }

        }
        startActivity(app.applicationContext, intent, null)

    }

    /**
     * Use external media if it is available, or app's file directory otherwise
     */
    fun getOutputDirectory(): File {

        val appContext = app.applicationContext
        val mediaDir = app.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }

        return if (mediaDir != null && mediaDir.exists()){
            mediaDir
        }
        else{
            appContext.filesDir
        }

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

    /**
     * Returns the orientation of the camera
     */
    fun getCameraFacing(): CameraX.LensFacing{
        return CameraX.LensFacing.BACK
    }

    fun activateLiveMode(){
        loadInferenceEnabled = false
        historyInferenceEnabled = false
        liveInferenceEnabled = true
        isInferenceFinished = true
        //notify the activity to show results
        //recognizeImage(RECOGNITION_LIVE)
    }

    fun activateLoadMode(){
        liveInferenceEnabled = false
        historyInferenceEnabled = false
        loadInferenceEnabled = true
        isInferenceFinished = true
        //notify the activity to show results
        recognizeImage(RECOGNITION_LOAD)
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
