package com.arpadfodor.android.paw_scanner.view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Size
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.model.BitmapProcessor
import com.arpadfodor.android.paw_scanner.model.Recognition
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel
import kotlinx.coroutines.Runnable
import java.util.*

class ClassifierFragment : Fragment(), OnImageAvailableListener, Camera.PreviewCallback{

    companion object {

        fun newInstance() = ClassifierFragment()

        private val PERMISSIONS_REQUEST = 1
        private val PERMISSION_CAMERA = Manifest.permission.CAMERA
        private const val MAINTAIN_ASPECT = true
        private val DESIRED_PREVIEW_SIZE = Size(640, 480)

    }

    private lateinit var viewModel: MainViewModel

    private var classifierInputWidth = 0
    private var classifierInputHeight = 0

    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var lastProcessingTimeMs: Long = 0
    private var sensorOrientation: Int? = null
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    protected var previewWidth = 0
    protected var previewHeight = 0
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    protected var luminanceStride: Int = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null

    private lateinit var textView: TextView

    protected val luminance: ByteArray?
        get() = yuvBytes[0]

    protected val screenOrientation: Int
        get() {
            when (activity?.windowManager?.defaultDisplay?.rotation) {
                Surface.ROTATION_270 -> return 270
                Surface.ROTATION_180 -> return 180
                Surface.ROTATION_90 -> return 90
                else -> return 0
            }
        }

    val layoutId: Int
        get() = R.layout.camera_fragment

    val desiredPreviewFrameSize: Size
        get() = DESIRED_PREVIEW_SIZE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.classifier_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        viewModel.loadClassifier()
        subscribeUi()

        textView = view.findViewById(R.id.tvLiveRecognitionData)

        classifierInputWidth = viewModel.classifier.value!!.getImageSizeX()
        classifierInputHeight = viewModel.classifier.value!!.getImageSizeY()

    }

    private fun subscribeUi() {
        // Create the text observer which updates the UI in case of text change
        val recognitionObserver = Observer<List<Recognition>> { result ->
            // Update the UI, in this case, the TextView
            var resultText = ""
            for(recognition in result){
                resultText += recognition
                resultText += "\n"
            }
            textView.text = resultText
        }
        // Observe the LiveData, passing in this viewLifeCycleOwner as the LifecycleOwner and the observer
        viewModel.result.observe(viewLifecycleOwner, recognitionObserver)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {

        super.onActivityCreated(savedInstanceState)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }

    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }

    /**
     * Callback for android.hardware.Camera API
     */
    override fun onPreviewFrame(bytes: ByteArray, camera: Camera) {

        if (isProcessingFrame) {
            return
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known
            if (rgbBytes == null) {
                val previewSize = camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            return
        }

        isProcessingFrame = true
        yuvBytes[0] = bytes
        luminanceStride = previewWidth

        imageConverter = Runnable {
            BitmapProcessor.convertYUV420SPToARGB8888(
                bytes,
                previewWidth,
                previewHeight,
                rgbBytes!!
            )
        }

        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }

        processImage()

    }

    /**
     * Callback for Camera2 API
     */
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return

            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            luminanceStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            imageConverter = Runnable {
                BitmapProcessor.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    luminanceStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }

            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }

            processImage()

        } catch (e: Exception) {
            Trace.endSection()
            return
        }

        Trace.endSection()
    }

    @Synchronized
    override fun onStart() {
        super.onStart()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()

        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    override fun onPause() {

        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
        }

        super.onPause()
    }

    @Synchronized
    override fun onStop() {
        super.onStop()
    }

    @Synchronized
    override fun onDestroy() {
        super.onDestroy()
    }

    @Synchronized
    protected fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                setFragment()
            }
            else {
                requestPermission()
            }
        }
    }

    private fun hasPermission(): Boolean {
        return activity?.applicationContext?.checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    activity?.applicationContext,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    protected fun setFragment() {

        val manager = activity?.applicationContext?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val availableCameras = manager.cameraIdList

        val camera2Fragment = CameraFragment.newInstance(
            object : CameraFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size, rotation: Int) {
                    previewHeight = size.height
                    previewWidth = size.width
                    this@ClassifierFragment.onPreviewSizeChosen(size, rotation)
                }
            },
            this,
            layoutId,
            desiredPreviewFrameSize
        )
        camera2Fragment.setCameras(availableCameras)
        fragmentManager?.beginTransaction()?.replace(R.id.container, camera2Fragment)?.commit()

    }

    protected fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    fun onPreviewSizeChosen(size: Size, rotation: Int) {

        previewWidth = size.width
        previewHeight = size.height

        val classifierInputWidth = viewModel.classifier.value!!.getImageSizeX()
        val classifierInputHeight = viewModel.classifier.value!!.getImageSizeY()

        sensorOrientation = rotation - screenOrientation

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(classifierInputWidth, classifierInputHeight, Bitmap.Config.ARGB_8888)

        frameToCropTransform = BitmapProcessor.getTransformationMatrix(
            previewWidth,
            previewHeight,
            classifierInputWidth,
            classifierInputHeight,
            sensorOrientation!!,
            MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)

    }

    fun processImage() {

        rgbFrameBitmap!!.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)

        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)

        Thread.sleep(500)
        runInBackground(
            Runnable {
                val startTime = SystemClock.uptimeMillis()
                viewModel.recognizeLiveImage(croppedBitmap)
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
                readyForNextImage()
            })

    }

}
