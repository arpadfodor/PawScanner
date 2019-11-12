package com.arpadfodor.android.paw_scanner.viewmodels

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
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.LENS_FACING
import android.hardware.camera2.CameraManager
import android.util.Range
import androidx.camera.core.CameraX
import androidx.core.content.ContextCompat.startActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.*
import com.arpadfodor.android.paw_scanner.models.ai.*
import com.arpadfodor.android.paw_scanner.models.BitmapProcessor.resizedBitmapToInferenceResolution
import com.arpadfodor.android.paw_scanner.viewmodels.services.InferenceService
import com.arpadfodor.android.paw_scanner.views.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import java.io.ByteArrayOutputStream
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object{

        const val MAXIMUM_RECOGNITIONS_TO_SHOW = 3

        const val MINIMUM_PREVIEW_SIZE = 320
        const val MAINTAIN_ASPECT = true

        const val RECOGNITION_DISABLED = 0
        const val RECOGNITION_LIVE = 1
        const val RECOGNITION_LOAD = 2
        const val RECOGNITION_HISTORY = 3

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
     * List of available cameras
     */
    var availableCameras = arrayOfNulls<String>(0)

    /*
     * Recognition result broadcast receiver
     */
    lateinit var recognitionResultReceiver: BroadcastReceiver

    /*
     * The Classifier
     */
    lateinit var classifier: Classifier

    /*
     * Whether inference has finished or not
     */
    var isInferenceFinished: Boolean = true

    /*
     * Whether currentDataToShow contains empty strings or not
     */
    var isCurrentDataToShowEmpty: Boolean = true

    /*
     * Current enabled inference type
     */
    val currentRecognitionEnabled: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

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
    var liveImage: Bitmap? = null

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

        FirebaseInteraction.init(app.applicationContext, app.getString(R.string.app_ad_id))

        LabelsManager.loadLabelList(app.assets)
        classifier = ClassifierFloatMobileNet(
            app.assets,
            Device.CPU,
            1
        )
        classifierInputSize.value = Size(classifier.getImageSizeX(), classifier.getImageSizeY())
        rotation.value = parentScreenOrientation
        isInferenceFinished = true
        currentCameraIndex.value = 0
        currentCameraOrientation.value = CameraX.LensFacing.BACK
        currentRecognitionEnabled.value = RECOGNITION_LIVE

        InferenceService.viewModel = this

        recognitionResultReceiver = object : BroadcastReceiver(){

            override fun onReceive(context: Context, intent: Intent) {

                val resultType = intent.getIntExtra("type", RECOGNITION_LIVE)

                if(resultType != currentRecognitionEnabled.value){
                    return
                }

                inferenceDuration.value = intent.getLongExtra("inferenceTime", 0)
                val sizeOfResults = intent.getIntExtra("numberOfRecognitions", 0)

                val results = arrayListOf<Recognition>()

                for(index in 0 until sizeOfResults){

                    val id = intent.getStringExtra("recognition-id-$index")
                    val title = intent.getStringExtra("recognition-title-$index")
                    val confidence = intent.getFloatExtra("recognition-confidence-$index", 0f)

                    results.add(
                        Recognition(
                            id,
                            title,
                            confidence,
                            null
                        )
                    )

                }

                result.value = results
                isInferenceFinished = true

                //notify the activity to show results
                updateCurrentInfo(inferenceDuration.value?: 0, result.value?: emptyList())

            }

        }

        LocalBroadcastManager.getInstance(app.applicationContext).registerReceiver(
            recognitionResultReceiver, IntentFilter("InferenceResult")
        )

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
     * FPS range of the current camera device
     *
     * @return Range<Int>?      Possible range FPS values
     */
    fun getFpsRange(): Range<Int>? {

        val chars: CameraCharacteristics?

        try {

            val manager = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            chars = manager.getCameraCharacteristics(availableCameras[currentCameraIndex.value!!]!!)

            val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            var result: Range<Int>? = null

            for (range in ranges!!) {
                val upper = range.upper
                //10 - min range upper for needs
                if (upper >= 10) {
                    if (result == null || upper < result.upper.toInt()) {
                        result = range
                    }
                }
            }
            if (result == null) {
                result = ranges[0]
            }
            return result

        } catch (e: CameraAccessException) {
            e.printStackTrace()
            return null
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

    fun setLoadedImage(bitmap: Bitmap){
        loadedImage.value = bitmap
        recognizeLoadedImage()
    }

    fun recognizeLiveImage(bitmap: Bitmap?){

        bitmap?: return
        liveImage = bitmap

        if(!isInferenceFinished){
            return
        }

        if(currentRecognitionEnabled.value != RECOGNITION_LIVE){
            return
        }

        isInferenceFinished = false

        val intent = Intent(app.applicationContext, InferenceService::class.java)
        intent.putExtra("type", RECOGNITION_LIVE)
        app.applicationContext.startService(intent)

    }

    private fun recognizeLoadedImage(){

        if(!isInferenceFinished || loadedImage.value == null){
            return
        }

        if(currentRecognitionEnabled.value != RECOGNITION_LOAD){
            return
        }

        isInferenceFinished = false

        val intent = Intent(app.applicationContext, InferenceService::class.java)
        intent.putExtra("type", RECOGNITION_LOAD)
        app.applicationContext.startService(intent)

    }

    private fun updateCurrentInfo(duration: Long, result: List<Recognition>){

        val dataToInsert = arrayListOf<String>()

        if(result.isEmpty()){

            dataToInsert.add("")
            dataToInsert.add("")
            dataToInsert.add("")
            currentDataToShow.value = dataToInsert

            isCurrentDataToShowEmpty = true

            return

        }

        //inference duration
        dataToInsert.add(app.getString(R.string.inference_duration, duration))

        //most possible result
        dataToInsert.add(result[0].toString())

        var predictions = app.getString(R.string.predictions)

        val showRecognitionFrom = min(MAXIMUM_RECOGNITIONS_TO_SHOW, result.size) -1
        val showRecognitionTo = 0

        //other results
        for(i in showRecognitionFrom downTo showRecognitionTo){
            predictions += result[i].toString() + "\n"
        }

        dataToInsert.add(predictions)

        currentDataToShow.value = dataToInsert

        isCurrentDataToShowEmpty = false

    }

    fun recognitionDetails(){

        if(isCurrentDataToShowEmpty){
            return
        }

        var bitmap = when {
            currentRecognitionEnabled.value == RECOGNITION_LIVE -> liveImage
            currentRecognitionEnabled.value == RECOGNITION_LOAD -> loadedImage.value?: return
            else -> return
        }

        bitmap = resizedBitmapToInferenceResolution(bitmap?: return, classifierInputSize.value?: return)

        val resultToSend = result.value?: return

        val intent = Intent(app.applicationContext, RecognitionActivity::class.java).apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            putExtra("inferenceTime", inferenceDuration.value)
            putExtra("numberOfRecognitions", resultToSend.size)

            val bs = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, bs)
            putExtra("byteArray", bs.toByteArray())

            for((index, recognition) in resultToSend.withIndex()){
                putExtra("recognition-id-$index", recognition.id)
                putExtra("recognition-title-$index", recognition.title)
                putExtra("recognition-confidence-$index", recognition.confidence)
            }

        }
        startActivity(app.applicationContext, intent, null)

    }

    fun showBreeds(){

        val intent = Intent(app.applicationContext, BreedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(app.applicationContext, intent, null)

    }

    fun showTips(){

        val intent = Intent(app.applicationContext, TipsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(app.applicationContext, intent, null)

    }

    fun showAbout(){

        val intent = Intent(app.applicationContext, AboutActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(app.applicationContext, intent, null)

    }

    fun showSettings(){

        val intent = Intent(app.applicationContext, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    /*
    * Notifies the activity to delete previous recognitions and start live mode
    */
    fun activateLiveMode(){
        currentRecognitionEnabled.value = RECOGNITION_LIVE
        isInferenceFinished = true
        updateCurrentInfo(0, emptyList())
    }

    /*
    * Notifies the activity to delete previous recognitions and start load mode
    */
    fun activateLoadMode(){
        currentRecognitionEnabled.value = RECOGNITION_LOAD
        isInferenceFinished = true
        updateCurrentInfo(0, emptyList())
        recognizeLoadedImage()
    }

    /*
    * Notifies the activity to delete previous recognitions and start history mode
    */
    fun activateHistoryMode(){
        currentRecognitionEnabled.value = RECOGNITION_HISTORY
        isInferenceFinished = true
        updateCurrentInfo(0, emptyList())
    }

    /*
    * Notifies the activity to delete previous recognitions and disable inference
    */
    fun disableInference(){
        currentRecognitionEnabled.value = RECOGNITION_DISABLED
        isInferenceFinished = false
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
