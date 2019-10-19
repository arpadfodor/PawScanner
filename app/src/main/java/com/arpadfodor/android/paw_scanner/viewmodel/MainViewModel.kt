package com.arpadfodor.android.paw_scanner.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Size
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arpadfodor.android.paw_scanner.model.Classifier
import com.arpadfodor.android.paw_scanner.model.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.model.Device
import com.arpadfodor.android.paw_scanner.model.Recognition
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {

    var app: Application = application

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of containing a DESIRED_SIZE x DESIRED_SIZE square
     */
    val MINIMUM_PREVIEW_SIZE = 320
    val DESIRED_PREVIEW_SIZE = Size(640, 480)
    var textureViewSize = Size(0,0)
    val MAINTAIN_ASPECT = true

    /**
     * The rotation in degrees of the camera sensor from the display
     */
    var sensorOrientation = 0

    /**
     * Is camera opened flag
     */
    var cameraOpened = false

    /*
     * Whether inference has finished or not
     */
    var isInferenceFinished = true

    var availableCameras = arrayOfNulls<String>(0)
    var previewSize = Size(0,0)

    lateinit var recognitionResultReceiver: BroadcastReceiver

    /*
     * The Classifier
     */
    lateinit var classifier: Classifier

    /*
     * The classifier input size
     */
    val classifierInputSize: MutableLiveData<Size> by lazy {
        MutableLiveData<Size>()
    }

    /*
     * Last classification result
     */
    val result: MutableLiveData<List<Recognition>> by lazy {
        MutableLiveData<List<Recognition>>()
    }

    /*
     * Last inference time in milliseconds
     */
    val inferenceTime: MutableLiveData<Long> by lazy {
        MutableLiveData<Long>()
    }

    /*
     * The loaded image
     */
    val loadedImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>()
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
        classifierInputSize.value = Size(classifier .getImageSizeX(), classifier. getImageSizeY())
        rotation.value = parentScreenOrientation
        isInferenceFinished = true
        currentCameraIndex.value = 0

        recognitionResultReceiver = object : BroadcastReceiver(){

            override fun onReceive(context: Context, intent: Intent) {

                inferenceTime.value = intent.getLongExtra("inferenceTime", 0)
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

    fun recognizeLoadedImage(){
        loadedImage.value?: return
        recognizeImage(loadedImage.value!!)
    }

    fun recognizeLiveImage(bitmap: Bitmap?){
        bitmap?: return
        recognizeImage(bitmap)
    }

    private fun recognizeImage(bitmap: Bitmap){

        if(isInferenceFinished == false){
            return
        }

        isInferenceFinished = false

        val intent = Intent(app.applicationContext, InferenceService::class.java)
        val bs = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 50, bs)
        intent.putExtra("byteArray", bs.toByteArray())
        app.applicationContext.startService(intent)

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
