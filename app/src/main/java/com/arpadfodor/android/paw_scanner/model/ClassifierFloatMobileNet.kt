package com.arpadfodor.android.paw_scanner.model

import android.content.res.AssetManager

/**
 * TensorFlow Lite classifier with the float MobileNet model
 */
class ClassifierFloatMobileNet(asset: AssetManager, device: Device, numThreads: Int) : Classifier(){

    companion object{

        /**
         * MobileNet requires additional normalization of the used input
         */
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f

        /**
         * Input image size required by the model
         */
        private const val IMAGE_SIZE_X = 224
        private const val IMAGE_SIZE_Y = 224

        /**
         * Model and label paths
         */
        private const val MODEL_PATH = "model.tflite"
        private const val LABEL_PATH = "labels.txt"

    }

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
        return IMAGE_SIZE_X
    }

    override fun getImageSizeY(): Int {
        return IMAGE_SIZE_Y
    }

    override fun getModelPath(): String {
        // you can download this file from see build.gradle for where to obtain this file
        // It should be auto downloaded into assets
        return MODEL_PATH
    }

    override fun getLabelPath(): String {
        return LABEL_PATH
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