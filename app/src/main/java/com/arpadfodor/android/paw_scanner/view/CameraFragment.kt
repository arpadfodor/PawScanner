package com.arpadfodor.android.paw_scanner.view

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.view.additional.AutoFitTextureView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraFragment
private constructor(
    private val cameraConnectionCallback: ConnectionCallback,
    /** A [OnImageAvailableListener] to receive frames as they are available.  */
    private val imageListener: OnImageAvailableListener,
    /** The layout identifier to inflate for this Fragment.  */
    private val layout: Int,
    /** The input size in pixels desired by TensorFlow (width and height of a square bitmap).  */
    private val inputSize: Size
) : Fragment(), View.OnClickListener {

    companion object {
        /**
         * The camera preview size will be chosen to be the smallest frame by pixel size capable of
         * containing a DESIRED_SIZE x DESIRED_SIZE square.
         */
        private val MINIMUM_PREVIEW_SIZE = 320

        /** Conversion from screen rotation to JPEG orientation.  */
        private val ORIENTATIONS = SparseIntArray()

        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Given choices of Sizes supported by a camera, chooses the smallest one whose width and height are at least as large as the minimum of both, or an exact match if possible
         *
         * @param choices The list of sizes that the camera supports for the intended output class
         * @param width The minimum desired width
         * @param height The minimum desired height
         *
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        protected fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
            val minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE)
            val desiredSize = Size(width, height)

            // Collect the supported resolutions that are at least as big as the preview Surface
            var exactSizeFound = false
            val bigEnough = ArrayList<Size>()
            val tooSmall = ArrayList<Size>()
            for (option in choices) {
                if (option == desiredSize) {
                    // Set the size but don't return yet so that remaining sizes will still be logged.
                    exactSizeFound = true
                }

                if (option.height >= minSize && option.width >= minSize) {
                    bigEnough.add(option)
                } else {
                    tooSmall.add(option)
                }
            }

            if (exactSizeFound) {
                return desiredSize
            }

            // Pick the smallest of those, assuming we found any
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else {
                choices[0]
            }
        }

        fun newInstance(
            callback: ConnectionCallback,
            imageListener: OnImageAvailableListener,
            layout: Int,
            inputSize: Size
        ): CameraFragment {
            return CameraFragment(callback, imageListener, layout, inputSize)
        }
    }

    /**
     * A Semaphore to prevent the app from exiting before closing the camera
     */
    private val cameraOpenCloseLock = Semaphore(1)

    var availableCameras = arrayOfNulls<String>(0)
    var currentCameraIndex = 0

    private lateinit var floatingActionButtonSwitch: FloatingActionButton

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
        }
    }

    /** An AutoFitTextureView for camera preview.  */
    private var textureView: AutoFitTextureView? = null
    /** A CameraCaptureSession for camera preview.  */
    private var captureSession: CameraCaptureSession? = null
    /** A reference to the opened CameraDevice  */
    private var cameraDevice: CameraDevice? = null
    /** The rotation in degrees of the camera sensor from the display.  */
    private var sensorOrientation: Int? = null
    /** The Size of camera preview.  */
    private var previewSize: Size? = null
    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null
    /** A Handler for running tasks in the background.  */
    private var backgroundHandler: Handler? = null
    /**
     * TextureView.SurfaceTextureListener handles several lifecycle events on a [ ]
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }
    /**
     * An ImageReader that handles preview frame capture
     */
    private var previewReader: ImageReader? = null
    /**
     * CaptureRequest.Builder] for the camera preview
     */
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    /**
     * CaptureRequest generated by previewRequestBuilder
     */
    private var previewRequest: CaptureRequest? = null
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
     * Shows a Toast on the UI thread
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        textureView = view.findViewById<View>(R.id.textureView) as AutoFitTextureView
        floatingActionButtonSwitch = view.findViewById(R.id.fabSwitch)
        floatingActionButtonSwitch.setOnClickListener {
            this.onClick(floatingActionButtonSwitch)
        }

    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener)
        if (textureView!!.isAvailable) {
            openCamera(textureView!!.width, textureView!!.height)
        } else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    fun setCameras(availableCameras: Array<String?>) {
        this.availableCameras = availableCameras
    }

    /**
     * Sets up member variables related to camera
     */
    private fun setUpCameraOutputs() {
        val activity = activity
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(availableCameras[currentCameraIndex]!!)

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            previewSize = chooseOptimalSize(
                map!!.getOutputSizes(SurfaceTexture::class.java),
                inputSize.width,
                inputSize.height
            )

            //Fit the aspect ratio of TextureView to the size of preview picked
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView!!.setAspectRatio(previewSize!!.width, previewSize!!.height)
            } else {
                textureView!!.setAspectRatio(previewSize!!.height, previewSize!!.width)
            }
        } catch (e: CameraAccessException) {
        } catch (e: NullPointerException) {
            ErrorDialog.newInstance(getString(R.string.camera_error))
            throw RuntimeException(getString(R.string.camera_error))
        }

        previewSize?.let { cameraConnectionCallback.onPreviewSizeChosen(it, sensorOrientation!!) }
    }

    /**
     * Opens the camera specified by CameraConnectionFragment.cameraId
     */
    private fun openCamera(width: Int, height: Int) {

        setUpCameraOutputs()
        configureTransform(width, height)
        val activity = activity
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(4000, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening")
            }
            if(activity.applicationContext?.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                manager.openCamera(availableCameras[currentCameraIndex]!!, stateCallback, backgroundHandler)
            }

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
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its Handler
     */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
        }

    }

    /**
     * Creates a new CameraCaptureSession for camera preview
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView!!.surfaceTexture!!

            //Configures the size of default buffer to be the size of camera preview wanted
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

            //This is the output Surface we need to start preview
            val surface = Surface(texture)

            //Sets up a CaptureRequest.Builder with the output Surface
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)

            //Creates the reader for the preview frames
            previewReader = ImageReader.newInstance(previewSize!!.width, previewSize!!.height, ImageFormat.YUV_420_888, 2)

            previewReader!!.setOnImageAvailableListener(imageListener, backgroundHandler)
            previewRequestBuilder!!.addTarget(previewReader!!.surface)

            //Creates a CameraCaptureSession for camera preview
            cameraDevice!!.createCaptureSession(listOf(surface, previewReader!!.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        //The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }

                        //When the session is ready, start displaying the preview
                        captureSession = cameraCaptureSession
                        try {
                            //Auto focus should be continuous for camera preview
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            //Flash is automatically enabled when necessary
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

                            //Start displaying the camera preview
                            previewRequest = previewRequestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(
                                previewRequest!!, captureCallback, backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                        }

                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        showToast("Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
        }

    }

    /**
     * Configures the necessary [Matrix] transformation to mTextureView
     * This method should be called after the camera preview size is determined in setUpCameraOutputs and also the size of mTextureView is fixed
     *
     * @param viewWidth The width of mTextureView
     * @param viewHeight The height of mTextureView
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
            RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
    }

    private fun changeCamera(){

        if(availableCameras.isEmpty()){
            return
        }

        if(availableCameras.size > currentCameraIndex){
            currentCameraIndex++
            if(availableCameras.size <= currentCameraIndex){
                currentCameraIndex = 0
            }
        }

        stopBackgroundThread()
        closeCamera()
        openCamera(textureView!!.width, textureView!!.height)
        startBackgroundThread()

    }

    override fun onClick(v: View) {
        when(v.id){
            R.id.fabSwitch ->{
                changeCamera()
            }
        }
    }

    /**
     * Callback for Activities to use to initialize their data once the selected preview size is known
     */
    interface ConnectionCallback {
        fun onPreviewSizeChosen(size: Size, cameraRotation: Int)
    }

    /**
     * Compares two Sizes based on their areas
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            //Cast to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }

    /**
     * Shows an error message dialog
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val activity = activity
            return AlertDialog.Builder(activity)
                .setMessage(arguments.getString(ARG_MESSAGE))
                .setPositiveButton(
                    android.R.string.ok
                ) { dialogInterface, i -> activity.finish() }
                .create()
        }

        companion object {
            private val ARG_MESSAGE = "message"

            fun newInstance(message: String): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }
    }

}
