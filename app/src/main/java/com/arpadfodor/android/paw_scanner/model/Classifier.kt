package com.arpadfodor.android.paw_scanner.model

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Trace
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.Comparator
import java.util.PriorityQueue
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import kotlin.math.min

/**
 * A classifier specialized to label images using TensorFlow Lite
 */
abstract class Classifier{

    /**
     * Number of results to show in the UI
     */
    private val MAX_RESULTS = 3

    /**
     * Dimensions of inputs
     */
    private val DIM_BATCH_SIZE = 1
    private val DIM_PIXEL_SIZE = 3

    /**
     * Pre-allocated buffers for storing image data in
     */
    private val intValues = IntArray(getImageSizeX() * getImageSizeY())

    /**
     * Options for configuring the Interpreter
     */
    private val tfLiteOptions = Interpreter.Options()

    /**
     * The loaded TensorFlow Lite model
     */
    private var tfLiteModel: MappedByteBuffer? = null

    /**
     * Labels corresponding to the output of the vision model
     */
    private var labels = arrayListOf<String>()

    /**
     * Optional GPU delegate for accleration
     */
    private var gpuDelegate: GpuDelegate? = null

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite
     */
    protected var tfLite: Interpreter? = null

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs
     */
    protected lateinit var imgData: ByteBuffer

    /**
     * Creates a classifier with the provided configuration.
     *
     * @param asset The AssetManager
     * @param device The device to use for classification
     * @param numThreads The number of threads to use for classification
     */
    @Throws(IOException::class)
    fun create(asset: AssetManager, device: Device, numThreads: Int){

        tfLiteModel = loadModelFile(asset)
        labels = loadLabelList(asset)

        when (device){
            Device.NNAPI -> {
                tfLiteOptions.setUseNNAPI(true)
            }
            Device.GPU -> {
                gpuDelegate = GpuDelegate()
                tfLiteOptions.addDelegate(gpuDelegate)
            }
            Device.CPU -> {
            }
        }

        imgData = ByteBuffer.allocateDirect(
            DIM_BATCH_SIZE
                    * getImageSizeX()
                    * getImageSizeY()
                    * DIM_PIXEL_SIZE
                    * getNumBytesPerChannel())

        tfLiteOptions.setNumThreads(numThreads)
        tfLite = Interpreter(tfLiteModel!!, tfLiteOptions)
        imgData.order(ByteOrder.nativeOrder())

    }

    /**
     * Reads label list from Assets
     */
    @Throws(IOException::class)
    private fun loadLabelList(asset: AssetManager): ArrayList<String> {
        val labels = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(asset.open(getLabelPath())))

        while(true){
            val line = reader.readLine() ?: break
            labels.add(line)
        }
        reader.close()
        return labels
    }

    /**
     * Memory-map the model file in Assets
     */
    @Throws(IOException::class)
    private fun loadModelFile(asset: AssetManager): MappedByteBuffer {
        val fileDescriptor = asset.openFd(getModelPath())
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Writes Image data into a ByteBuffer
     */
    private fun convertBitmapToByteBuffer(bitmapRaw: Bitmap) {

        val bitmap = Bitmap.createScaledBitmap(bitmapRaw, getImageSizeX(), getImageSizeY(), true)

        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // Convert the image to floating point
        var pixel = 0
        val startTime = SystemClock.uptimeMillis()

        for (i in 0 until getImageSizeX()) {
            for (j in 0 until getImageSizeY()) {
                val `val` = intValues[pixel++]
                addPixelValue(`val`)
            }
        }

        val endTime = SystemClock.uptimeMillis()

    }

    /**
     * Runs inference and returns the classification results
     */
    fun recognizeImage(bitmap: Bitmap): List<Recognition> {

        //Log this method so that it can be analyzed with systrace
        Trace.beginSection("recognize Image")
        Trace.beginSection("pre-process Bitmap")
        convertBitmapToByteBuffer(bitmap)
        Trace.endSection()

        //Run the inference
        Trace.beginSection("run Inference")
        val startTime = SystemClock.uptimeMillis()
        runInference()
        val endTime = SystemClock.uptimeMillis()
        Trace.endSection()

        //Find the best classifications
        val pq = PriorityQueue(
            3,
            Comparator<Recognition> { lhs, rhs ->
                //Intentionally reversed to put high confidence at the head of the queue
                (rhs.confidence).compareTo(lhs.confidence)
            })

        for (i in labels.indices){
            pq.add(
                Recognition(
                    "" + i,
                    if (labels.size > i){
                        labels[i]
                    } else {
                        "unknown"
                    },
                    getNormalizedProbability(i),
                    null
                )
            )
        }

        val recognitions = ArrayList<Recognition>()
        val recognitionsSize = min(pq.size, MAX_RESULTS)

        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll()!!)
        }

        Trace.endSection()
        return recognitions

    }

    /**
     * Closes the interpreter and model to release resources
     */
    fun close() {

        tfLite?.close()
        tfLite = null

        gpuDelegate?.close()
        gpuDelegate = null

        tfLiteModel = null

    }

    /**
     * Get the image size along the x axis.
     *
     * @return
     */
    abstract fun getImageSizeX(): Int

    /**
     * Get the image size along the y axis.
     *
     * @return
     */
    abstract fun getImageSizeY(): Int

    /**
     * Get the name of the model file stored in Assets.
     *
     * @return
     */
    protected abstract fun getModelPath(): String

    /**
     * Get the name of the label file stored in Assets.
     *
     * @return
     */
    protected abstract fun getLabelPath(): String

    /**
     * Get the number of bytes that is used to store a single color channel value.
     *
     * @return
     */
    protected abstract fun getNumBytesPerChannel(): Int

    /**
     * Add pixelValue to byteBuffer.
     *
     * @param pixelValue
     */
    protected abstract fun addPixelValue(pixelValue: Int)

    /**
     * Read the probability value for the specified label This is either the original value as it was
     * read from the net's output or the updated value after the filter was applied.
     *
     * @param labelIndex
     * @return
     */
    protected abstract fun getProbability(labelIndex: Int): Float

    /**
     * Set the probability value for the specified label.
     *
     * @param labelIndex
     * @param value
     */
    protected abstract fun setProbability(labelIndex: Int, value: Number)

    /**
     * Get the normalized probability value for the specified label. This is the final value as it
     * will be shown to the user.
     *
     * @return
     */
    protected abstract fun getNormalizedProbability(labelIndex: Int): Float

    /**
     * Run inference using the prepared input in [.imgData]. Afterwards, the result will be
     * provided by getProbability().
     *
     * This additional method is necessary, because we don't have a common base for different
     * primitive data types.
     */
    protected abstract fun runInference()

    /**
     * Get the total number of labels.
     *
     * @return
     */
    protected fun getNumLabels(): Int {
        return labels.size
    }

}