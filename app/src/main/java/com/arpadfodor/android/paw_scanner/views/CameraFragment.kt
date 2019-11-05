package com.arpadfodor.android.paw_scanner.views

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Rational
import android.view.*
import android.webkit.MimeTypeMap
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.models.BitmapProcessor
import com.arpadfodor.android.paw_scanner.views.additional.AutoFitTextureView
import com.arpadfodor.android.paw_scanner.viewmodels.MainViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CameraFragment: Fragment(), ImageReader.OnImageAvailableListener, View.OnClickListener {

    companion object {

        fun newInstance(): CameraFragment {
            return CameraFragment()
        }

        /**
         * Camera state: Showing camera preview
         */
        private const val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the exposure to be pre-capture state
         */
        private const val STATE_WAITING_PRECAPTURE = 1

        /**
         * Camera state: Waiting for the exposure state to be something other than pre-capture
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 2

    }

    private lateinit var broadcastManager: LocalBroadcastManager

    var lastCameraChange = 0L
    var minTimeBetweenCameraChanges = 2000L

    private var imageCapture: ImageCapture? = null

    /**
     * Volume down button receiver used to trigger taking a photo
     */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(MainViewModel.KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    takePhoto()
                }
            }
        }
    }

    /**
     * TextureView.SurfaceTextureListener handles several lifecycle events
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    }

    /**
     * A CameraCaptureSession for camera preview
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened CameraDevice
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * CameraDevice.StateCallback is called when CameraDevice changes its state
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cd: CameraDevice) {
            //This method is called when the camera is opened; camera preview starts here
            cameraOpenCloseLock.release()
            cameraDevice = cd
            createCameraPreviewSession()
        }

        override fun onDisconnected(cd: CameraDevice) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
        }

        override fun onError(cd: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
            val activity = activity
            activity?.finish()
        }

    }

    /** Define callback that will be triggered after a photo has been taken and saved to disk */
    private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(
            error: ImageCapture.ImageCaptureError, message: String, exc: Throwable?) {
            Log.e(TAG, "Photo capture failed: $message")
            exc?.printStackTrace()
        }

        override fun onImageSaved(photoFile: File) {

            Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")

            // Implicit broadcasts will be ignored for devices running API level >= 24
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                requireActivity().sendBroadcast(
                    Intent(Camera.ACTION_NEW_PICTURE, Uri.fromFile(photoFile))
                )
            }

            // If the folder selected is an external media directory, this is unnecessary
            // Otherwise, other apps will not be able to access the images unless scanning them using [MediaScannerConnection]
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(photoFile.extension)
            MediaScannerConnection.scanFile(context, arrayOf(photoFile.absolutePath), arrayOf(mimeType), null)

        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A Handler for running tasks in the background
     */
    private var backgroundHandler: Handler? = null

    /**
     * An ImageReader that handles preview frame capture
     */
    private var previewReader: ImageReader? = null

    /**
     * The current state of camera for taking pictures
     */
    private var state = STATE_PREVIEW

    /**
     * CaptureRequest.Builder for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    /**
     * CaptureRequest generated by previewRequestBuilder
     */
    private lateinit var previewRequest: CaptureRequest

    /**
     * A Semaphore to prevent the app from exiting before closing the camera
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * A CameraCaptureSession.CaptureCallback that handles events related to JPEG image capture
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {

            when (state) {

                STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRECAPTURE
                    }
                }

            }

        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            process(result)
        }

    }

    private lateinit var viewModel: MainViewModel

    /**
     * Parent layout of the fragment
     */
    private lateinit var container: ConstraintLayout

    /**
     * An AutoFitTextureView for camera preview
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * A FloatingActionButton to switch camera
     */
    private lateinit var floatingActionButtonSwitch: FloatingActionButton
    /**
     * A FloatingActionButton to save image
     */
    private lateinit var floatingActionButtonSave: FloatingActionButton
    /**
     * A FloatingActionButton to show detailed recognition info
     */
    private lateinit var floatingActionButtonPaw: FloatingActionButton

    private var isProcessingFrame = false
    protected var luminanceStride: Int = 0
    private var postInferenceCallback: Runnable? = null

    private var imageConverter: Runnable? = null

    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private val YUVbytes = arrayOfNulls<ByteArray>(3)
    private var RGBbytes: IntArray? = null

    protected val luminance: ByteArray?
        get() = YUVbytes[0]

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.camera_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        container = view as ConstraintLayout

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        activity?.let {
            /**
             *  create view model in activity scope
             */
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)
        }

        textureView = view.findViewById<TextureView>(R.id.textureView) as AutoFitTextureView
        floatingActionButtonSwitch = view.findViewById<FloatingActionButton>(R.id.fabSwitch)
        floatingActionButtonSave = view.findViewById<FloatingActionButton>(R.id.fabSave)
        floatingActionButtonPaw = view.findViewById<FloatingActionButton>(R.id.fabPaw)

        floatingActionButtonSwitch.setOnClickListener {
            this.onClick(floatingActionButtonSwitch)
        }
        floatingActionButtonSave.setOnClickListener {
            this.onClick(floatingActionButtonSave)
        }
        floatingActionButtonPaw.setOnClickListener {
            this.onClick(floatingActionButtonPaw)
        }

    }

    fun onPreviewSizeChosen() {

        val classifierInputSize = viewModel.classifierInputSize.value!!

        val preview = viewModel.previewSize.value?: return

        rgbFrameBitmap = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(classifierInputSize.width, classifierInputSize.height, Bitmap.Config.ARGB_8888)

        frameToCropTransform = BitmapProcessor.getTransformationMatrix(
            preview.width,
            preview.height,
            classifierInputSize.width,
            classifierInputSize.height,
            viewModel.sensorOrientation,
            MainViewModel.MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)

        isProcessingFrame = false

    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return RGBbytes
    }

    /**
     * Callback for Camera2 API
     */
    override fun onImageAvailable(reader: ImageReader) {

        val preview = viewModel.previewSize.value?: return

        if (RGBbytes == null) {
            RGBbytes = IntArray(preview.width * preview.height)
        }

        if(RGBbytes!!.size != (preview.width * preview.height)){
            RGBbytes = IntArray(preview.width * preview.height)
        }

        try {
            val readImage = reader.acquireLatestImage() ?: return

            if (isProcessingFrame) {
                readImage.close()
                return
            }

            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = readImage.planes
            fillBytes(planes, YUVbytes)
            luminanceStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            imageConverter = Runnable {
                BitmapProcessor.convertYUV420ToARGB8888(
                    YUVbytes[0]!!,
                    YUVbytes[1]!!,
                    YUVbytes[2]!!,
                    preview.width,
                    preview.height,
                    luminanceStride,
                    uvRowStride,
                    uvPixelStride,
                    RGBbytes!!
                )
            }

            postInferenceCallback = Runnable {
                readImage.close()
                isProcessingFrame = false
            }

            processImage()

        } catch (e: Exception) {
            Trace.endSection()
            return
        }

        Trace.endSection()
    }

    protected fun fillBytes(planes: Array<Image.Plane>, YUVbytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in advance the actual necessary dimensions of the yuv planes
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (YUVbytes[i] == null) {
                YUVbytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(YUVbytes[i]!!)
        }
    }

    @Synchronized
    override fun onStart() {
        super.onStart()
    }

    @Synchronized
    override fun onResume() {

        super.onResume()
        startBackgroundThread()

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(MainViewModel.KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // When the screen is turned off and turned back on, the SurfaceTexture is already available, and onSurfaceTextureAvailable will not be called
        // In that case, open a camera and start preview from here (otherwise, wait until the surface is ready in the SurfaceTextureListener)
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }

    }

    @Synchronized
    override fun onPause() {

        closeCamera()
        stopBackgroundThread()
        // Unregister the broadcast receiver
        broadcastManager.unregisterReceiver(volumeDownReceiver)

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

    /**
     * Open the camera specified by cameraId
     */
    private fun openCamera(width: Int, height: Int){

        if(viewModel.cameraOpened){
            return
        }

        viewModel.setUpCameras()
        textureView.setAspectRatio(viewModel.textureViewSize)
        configureTransform(width, height)

        val preview = viewModel.previewSize.value?: return

        previewReader = ImageReader.newInstance(preview.width, preview.height, ImageFormat.JPEG, /*maxImages*/ 2)

        val manager = viewModel.app.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            if (!cameraOpenCloseLock.tryAcquire(4000, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening")
            }
            if(viewModel.app.applicationContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                manager.openCamera(viewModel.availableCameras[viewModel.currentCameraIndex.value!!]!!, stateCallback, backgroundHandler)
            }

            viewModel.cameraOpened = true

        } catch (e: CameraAccessException) {
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening", e)
        }

    }

    /**
     * Closes the current CameraDevice
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            if (null != previewReader) {
                previewReader!!.close()
                previewReader = null
            }

            viewModel.cameraOpened = false

        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its Handler
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("ImageListener")
        backgroundThread?: return
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its Handler
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
        }

    }

    /**
     * Creates a new CameraCaptureSession for camera preview
     */
    private fun createCameraPreviewSession() {

        onPreviewSizeChosen()

        val preview = viewModel.previewSize.value?: return

        try {

            val texture = textureView.surfaceTexture

            // Configures the size of default buffer to be the size of camera preview wanted
            texture.setDefaultBufferSize(preview.width, preview.height)

            //This is the output Surface we need to start preview
            val surface = Surface(texture)

            cameraDevice?: return

            // Sets up a CaptureRequest.Builder with the output Surface
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            // Creates the reader for the preview frames
            previewReader = ImageReader.newInstance(preview.width, preview.height, ImageFormat.YUV_420_888, 2)
            previewReader?: return

            previewReader?.setOnImageAvailableListener(this, backgroundHandler)
            previewRequestBuilder.addTarget(previewReader!!.surface)

            // Creates a CameraCaptureSession for camera preview
            cameraDevice!!.createCaptureSession(
                listOf(surface, previewReader?.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {

                        //The camera is already closed
                        cameraDevice?: return
                        //When the session is ready, start displaying the preview
                        captureSession = cameraCaptureSession

                        try {

                            //needed to prevent too dark preview images
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, viewModel.getFpsRange())

                            //Auto focus should be continuous for camera preview
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false)
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
                            previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)

                            //Start displaying the camera preview
                            previewRequest = previewRequestBuilder.build()
                            captureSession!!.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler)

                        } catch (e: CameraAccessException) {
                        }

                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    }
                }, null
            )

        } catch (e: CameraAccessException) {
        }

    }

    /**
     * Configures the necessary Matrix transformation to the TextureView
     * Should be called after the camera preview size is determined in setUpCameraOutputs and also the size of the TextureView is fixed
     *
     * @param viewWidth The width of the TextureView
     * @param viewHeight The height of the TextureView
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

        val preview = viewModel.previewSize.value?: return

        val rotation = viewModel.rotation.value
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, preview.height.toFloat(), preview.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(viewHeight.toFloat() / preview.height, viewWidth.toFloat() / preview.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        textureView.setTransform(matrix)

    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.fabSwitch ->{
                if((System.currentTimeMillis() - lastCameraChange) > minTimeBetweenCameraChanges){
                    viewModel.changeCamera()
                    onPause()
                    createCameraPreviewSession()
                    onResume()
                    lastCameraChange = System.currentTimeMillis()
                }
            }

            R.id.fabSave ->{
                takePhoto()
            }

            R.id.fabPaw ->{
                viewModel.recognitionDetails()
            }

        }
    }

    fun processImage() {

        val preview = viewModel.previewSize.value?: return

        rgbFrameBitmap?.setPixels(getRgbBytes(), 0, preview.width, 0, 0, preview.width, preview.height)
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        viewModel.recognizeLiveImage(croppedBitmap)
        readyForNextImage()

    }

    fun takePhoto(){

        val previewSize = viewModel.previewSize.value?: return

        // Set up the capture use case to allow users to take photos
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            // Request 4:3 aspect ratio
            setTargetAspectRatio(Rational(previewSize.width, previewSize.height))
            // Set initial target rotation, necessary to call this again if rotation changes during the lifecycle of this use case
            setTargetRotation(viewModel.getScreenOrientation())
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)

        imageCapture?.let { imageCapture ->

            val rawDate = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss")
            val formattedDate = rawDate.format(Calendar.getInstance().time)
            val outputDirectory = viewModel.getOutputDirectory()
            val imageName = viewModel.fileName + "-" + formattedDate

            val file = File(outputDirectory, imageName + MainViewModel.IMAGE_EXTENSION)

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {
                // Mirror image when using the front camera
                //isReversedHorizontal = lensFacing == CameraX.LensFacing.FRONT
            }

            // Setup image capture listener which is triggered after photo has been taken
            //imageCapture.takePicture(file, imageSavedListener, metadata)
            imageCapture.takePicture(file, imageSavedListener)

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Display flash animation to indicate that photo was captured
                container.postDelayed({
                    container.foreground = ColorDrawable(MainViewModel.shutterColor)
                    container.postDelayed(
                        { container.foreground = null }, MainViewModel.ANIMATION_FAST)
                }, MainViewModel.ANIMATION_SLOW)

            }
        }

    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

}
