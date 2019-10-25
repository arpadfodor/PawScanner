package com.arpadfodor.android.paw_scanner.view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.model.BitmapProcessor
import com.arpadfodor.android.paw_scanner.model.Recognition
import com.arpadfodor.android.paw_scanner.view.additional.AutoFitTextureView
import com.arpadfodor.android.paw_scanner.viewmodel.ImageSaver
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

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
         * Camera state: Waiting for the focus to be locked
         */
        private const val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken
         */
        private const val STATE_PICTURE_TAKEN = 4

    }

    var lastCameraChange = 0L
    var thresholdBetweenCameraChanges = 2000L

    /**
     * TextureView.SurfaceTextureListener handles several lifecycle events on a [ ]
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
     * The output file for the picture
     */
    private lateinit var file: File

    /**
     * This is a callback object for the ImageReader
     * onImageAvailable will be called when still image is ready to be saved
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        backgroundHandler?.post(ImageSaver(it.acquireNextImage(), file))
    }

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
                STATE_WAITING_LOCK -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }

        }

        private fun capturePicture(result: CaptureResult) {

            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null) {
                captureStillPicture()
            }
            if(afState == CaptureResult.CONTROL_AF_STATE_INACTIVE){
                //TODO - why stuck in this state?...
                unlockFocus()
            }
            else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                // CONTROL_AE_STATE can be null on some devices
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)

                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    state = STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
                    runPrecaptureSequence()
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

        activity?.let {
            /**
             *  create view model in activity scope
             */
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)
        }

        textureView = view.findViewById<TextureView>(R.id.textureView) as AutoFitTextureView
        floatingActionButtonSwitch = view.findViewById(R.id.fabSwitch)
        floatingActionButtonSave = view.findViewById(R.id.fabSave)

        floatingActionButtonSwitch.setOnClickListener {
            this.onClick(floatingActionButtonSwitch)
        }
        floatingActionButtonSave.setOnClickListener {
            this.onClick(floatingActionButtonSave)
        }

    }

    fun onPreviewSizeChosen() {

        val classifierInputSize = viewModel.classifierInputSize.value!!

        rgbFrameBitmap = Bitmap.createBitmap(viewModel.previewSize.width, viewModel.previewSize.height, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(classifierInputSize.width, classifierInputSize.height, Bitmap.Config.ARGB_8888)

        frameToCropTransform = BitmapProcessor.getTransformationMatrix(
            viewModel.previewSize.width,
            viewModel.previewSize.height,
            classifierInputSize.width,
            classifierInputSize.height,
            viewModel.sensorOrientation,
            MainViewModel.MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)

    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return RGBbytes
    }

    /**
     * Callback for Camera2 API
     */
    override fun onImageAvailable(reader: ImageReader) {

        if (RGBbytes == null) {
            RGBbytes = IntArray(viewModel.previewSize.width * viewModel.previewSize.height)
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
            fillBytes(planes, YUVbytes)
            luminanceStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            imageConverter = Runnable {
                BitmapProcessor.convertYUV420ToARGB8888(
                    YUVbytes[0]!!,
                    YUVbytes[1]!!,
                    YUVbytes[2]!!,
                    viewModel.previewSize.width,
                    viewModel.previewSize.height,
                    luminanceStride,
                    uvRowStride,
                    uvPixelStride,
                    RGBbytes!!
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

    protected fun fillBytes(planes: Array<Image.Plane>, YUVbytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (YUVbytes[i] == null) {
                YUVbytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(YUVbytes[i])
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

        // When the screen is turned off and turned back on, the SurfaceTexture is already available, and onSurfaceTextureAvailable will not be called
        // In that case, open a camera and start preview from here (otherwise, wait until the surface is ready in the SurfaceTextureListener)
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }

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

        closeCamera()
        stopBackgroundThread()
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
    fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
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

        val previewSize = viewModel.previewSize

        previewReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, /*maxImages*/ 2).apply {
            setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
        }

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

        try {

            val texture = textureView.surfaceTexture

            // Configures the size of default buffer to be the size of camera preview wanted
            texture.setDefaultBufferSize(viewModel.previewSize.width, viewModel.previewSize.height)

            //This is the output Surface we need to start preview
            val surface = Surface(texture)

            cameraDevice?: return

            // Sets up a CaptureRequest.Builder with the output Surface
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?: return
            previewRequestBuilder.addTarget(surface)

            // Creates the reader for the preview frames
            previewReader = ImageReader.newInstance(viewModel.previewSize.width, viewModel.previewSize.height, ImageFormat.YUV_420_888, 2)
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
                            //Auto focus should be continuous for camera preview
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
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

        val rotation = viewModel.rotation.value
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, viewModel.previewSize.height.toFloat(), viewModel.previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(viewHeight.toFloat() / viewModel.previewSize.height, viewWidth.toFloat() / viewModel.previewSize.width)
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
                if((System.currentTimeMillis() - lastCameraChange) > thresholdBetweenCameraChanges){
                    viewModel.changeCamera()
                    onPause()
                    onResume()
                    lastCameraChange = System.currentTimeMillis()
                }
            }

            R.id.fabSave ->{
                lockFocus()
            }

        }
    }

    fun processImage() {

        rgbFrameBitmap?.setPixels(getRgbBytes(), 0, viewModel.previewSize.width, 0, 0, viewModel.previewSize.width, viewModel.previewSize.height)
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        viewModel.recognizeLiveImage(croppedBitmap)
        readyForNextImage()

    }

    /**
     * Lock the focus as the first step for a still image capture
     */
    fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            // Tell captureCallback to wait for the lock
            state = STATE_WAITING_LOCK
            captureSession?.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
        }

    }

    /**
     * Runs the pre-capture sequence for capturing a still image
     * Should be called when a response is received in captureCallback from lockFocus
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            // Tell captureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRECAPTURE
            captureSession?.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

    /**
     * Captures a still picture
     * This method should be called when a response is received in captureCallback from both lockFocus
     */
    private fun captureStillPicture() {

        try {

            if (activity == null || cameraDevice == null) {
                return
            }

            val df = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss")
            val formattedDate = df.format(Calendar.getInstance().time)

            val imagesToSaveDir = Environment.DIRECTORY_DCIM + File.separator + viewModel.saveImageBasename
            val imageToSaveName = viewModel.saveImageBasename + "_" + formattedDate + "_" + Random(123).nextInt(3)

            file = File(imagesToSaveDir, imageToSaveName)

            // This is the CaptureRequest.Builder that is used to take a picture
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                addTarget(previewReader!!.surface)

                // Sensor orientation is 90 for most devices, or 270 for some devices
                // Take it into account and rotate JPEG properly
                // For devices with orientation of 90, returns mapping from ORIENTATIONS
                // For devices with orientation of 270, necessary to rotate the JPEG image 180 degrees
                set(CaptureRequest.JPEG_ORIENTATION,
                    (viewModel.getScreenOrientation() + viewModel.sensorOrientation + 270) % 360)

                // Use the same AE and AF modes as the preview
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    Toast.makeText(viewModel.app.applicationContext, "$imageToSaveName saved to $imagesToSaveDir", Toast.LENGTH_LONG).show()
                    unlockFocus()
                }

            }

            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder!!.build(), captureCallback, null)
            }
        } catch (e: CameraAccessException) {
        }

    }

    /**
     * Unlocks the focus; should be called when still image capture sequence is finished
     */
    private fun unlockFocus() {

        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            setAutoFlash(previewRequestBuilder)
            captureSession?.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler)
            // After this, the camera returns to the normal state of preview
            state = STATE_PREVIEW
            captureSession?.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
        }

    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (viewModel.flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

}
