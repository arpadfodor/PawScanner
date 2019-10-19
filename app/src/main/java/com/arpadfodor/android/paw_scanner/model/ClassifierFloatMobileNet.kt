package com.arpadfodor.android.paw_scanner.model

import android.content.res.AssetManager

/**
 * This TensorFlow Lite classifier works with the float MobileNet model
 */
class ClassifierFloatMobileNet(asset: AssetManager, device: Device, numThreads: Int) : Classifier(){

    /**
     * MobileNet requires additional normalization of the used input
     */
    private val IMAGE_MEAN = 127.5f
    private val IMAGE_STD = 127.5f

    /**
     * An array to hold inference results, to be feed into TensorFlow Lite as outputs
     * This isn't part of the super class, because a primitive array is needed here
     */
    private var labelProbArray : Array<FloatArray>

    init {
        this.create(asset, device, numThreads)
        labelProbArray = Array(1) { FloatArray(getNumLabels()) }
    }

    override fun getImageSizeX(): Int {
        return 224
    }

    override fun getImageSizeY(): Int {
        return 224
    }

    override fun getModelPath(): String {
        // you can download this file from see build.gradle for where to obtain this file
        // It should be auto downloaded into assets
        return "mobilenet_v1_1.0_224.tflite"
    }

    override fun getLabelPath(): String {
        return "labels.txt"
    }

    override fun getNumBytesPerChannel(): Int {
        return 4 // Float.SIZE / Byte.SIZE;
    }

    override fun addPixelValue(pixelValue: Int) {
        imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
    }

    override fun getProbability(labelIndex: Int): Float {
        return labelProbArray[0][labelIndex]
    }

    override fun setProbability(labelIndex: Int, value: Number) {
        labelProbArray[0][labelIndex] = value.toFloat()
    }

    override fun getNormalizedProbability(labelIndex: Int): Float {
        return labelProbArray[0][labelIndex]
    }

    override fun runInference() {
        tfLite?.run(imgData, labelProbArray)
    }

}