package com.arpadfodor.android.paw_scanner.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import android.provider.MediaStore
import kotlin.math.max


/**
 * Utility class for manipulating images
 */
object BitmapProcessor {

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges are normalized to eight bits
    internal val kMaxChannelValue = 262143

    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image of the given dimensions
     */
    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2

        return ySize + uvSize
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap    The recognizedImage to save
     * @param filename  The location to save the recognizedImage to
     */
    @JvmOverloads
    fun saveBitmap(bitmap: Bitmap, filename: String = "preview.png") {
        val root =
            Environment.getExternalStorageDirectory().absolutePath + File.separator + "tensorflow"
        val myDir = File(root)

        if (!myDir.mkdirs()) {
        }

        val file = File(myDir, filename)
        if (file.exists()) {
            file.delete()
        }
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
        }

    }

    fun convertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {

        val frameSize = width * height
        var j = 0
        var yp = 0
        while (j < height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0

            var i = 0
            while (i < width) {
                val y = 0xff and input[yp].toInt()
                if (i and 1 == 0) {
                    v = 0xff and input[uvp++].toInt()
                    u = 0xff and input[uvp++].toInt()
                }

                output[yp] = YUV2RGB(y, u, v)
                i++
                yp++
            }
            j++
        }

    }

    private fun YUV2RGB(y: Int, u: Int, v: Int): Int {

        var y = y
        var u = u
        var v = v
        // Adjust and check YUV values
        y = if (y - 16 < 0) 0 else y - 16
        u -= 128
        v -= 128

        // The floating point equivalent
        // Conversion is conducted with integers because some Android devices do not have floating point in hardware
        /*
        var r = (1.164 * y + 2.018 * u).toInt()
        var g = (1.164 * y - 0.813 * v - 0.391 * u).toInt()
        var b = (1.164 * y + 1.596 * v).toInt()
        */

        val y1192 = 1192 * y
        var r = y1192 + 1634 * v
        var g = y1192 - 833 * v - 400 * u
        var b = y1192 + 2066 * u

        // Clipping RGB values to be inside boundaries [0, kMaxChannelValue]
        r = if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
        g = if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
        b = if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b

        return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)

    }

    fun convertYUV420ToARGB8888(
        yData: ByteArray,
        uData: ByteArray,
        vData: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        out: IntArray
    ) {

        var yp = 0
        for (j in 0 until height) {

            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)

            for (i in 0 until width) {

                val uvOffset = pUV + (i shr 1) * uvPixelStride

                out[yp++] = YUV2RGB(
                    0xff and yData[pY + i].toInt(),
                    0xff and uData[uvOffset].toInt(),
                    0xff and vData[uvOffset].toInt()
                )

            }

        }
    }

    /**
     * Returns a transformation matrix from one reference frame into another
     * Handles cropping (if maintaining aspect ratio is desired) and rotation
     *
     * @param srcWidth Width of source frame
     * @param srcHeight Height of source frame
     * @param dstWidth Width of destination frame
     * @param dstHeight Height of destination frame
     * @param applyRotation Amount of rotation to apply from one frame to another. Must be a multiple of 90
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant, cropping the image if necessary
     *
     * @return The transformation fulfilling the desired requirements
     */
    fun getTransformationMatrix(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        applyRotation: Int,
        maintainAspectRatio: Boolean
    ): Matrix {
        val matrix = Matrix()

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
            }

            // Translate so center of image is at origin
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin
            matrix.postRotate(applyRotation.toFloat())
        }

        // Account for the already applied rotation, if any, and then determine how much scaling is needed for each axis
        val transpose = (abs(applyRotation) + 90) % 180 == 0

        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary
        if (inWidth != dstWidth || inHeight != dstHeight) {

            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while maintaining the aspect ratio
                // Some image may fall off the edge
                val scaleFactor = max(scaleFactorX, scaleFactorY)
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src
                matrix.postScale(scaleFactorX, scaleFactorY)
            }

        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return matrix
    }

    fun bitmapToCroppedImage(selectedImageUri: Uri, sourceBitmap: Bitmap, context: Context): Bitmap{

        val bitmapRotation = getPhotoOrientation(context, selectedImageUri)

        val croppedBitmap: Bitmap?

        val matrix = Matrix()
        matrix.postRotate(bitmapRotation.toFloat())

        if (sourceBitmap.width >= sourceBitmap.height) {

            croppedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                sourceBitmap.width / 2 - sourceBitmap.height / 2,
                0,
                sourceBitmap.height,
                sourceBitmap.height,
                matrix,
                false
            )

        } else {

            croppedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                0,
                sourceBitmap.height / 2 - sourceBitmap.width / 2,
                sourceBitmap.width,
                sourceBitmap.width,
                matrix,
                false
            )
        }

        return croppedBitmap

    }

    /**
     * Returns the orientation of the inspected image from MediaStore
     * Thanks to some manufacturers (Samsung), Exif orientation read is not reliable
     *
     * @param context   Context
     * @param photoUri  URI of the image to get the orientation information for
     *
     * @return Int      Orientation of the image
     */
    private fun getPhotoOrientation(context: Context, photoUri: Uri): Int {

        val cursor = context.contentResolver.query(
            photoUri,
            arrayOf(MediaStore.Images.ImageColumns.ORIENTATION), null, null, null
        )

        cursor?: return 0

        if (cursor!!.count !== 1) {
            cursor.close()
            return 0
        }

        cursor.moveToFirst()
        val orientation = cursor.getInt(0)
        cursor.close()

        return orientation

    }

    /**
     * Returns the orientation of the inspected image from Exif metadata
     * Thanks to some manufacturers (Samsung), Exif orientation read is not reliable
     *
     * @param selectedImageUri  URI of the image to get the orientation information for
     *
     * @return Int              Orientation of the image
     */
    private fun getExifPhotoOrientation(selectedImageUri: Uri): Int{
        val exif = ExifInterface(selectedImageUri.path)
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    /**
     * Returns the orientation of the inspected image from Exif metadata
     * Thanks to some manufacturers (Samsung), Exif orientation read is not reliable
     *
     * @param croppedBitmap     The input image which has been cropped
     *
     * @return Bitmap           The Bitmap format required by the model
     */
    fun resizedBitmapToInferenceResolution(croppedBitmap: Bitmap, classifierInputSize: Size): Bitmap{

        val cropToFrameTransform = Matrix()

        val inferenceBitmap: Bitmap = Bitmap.createBitmap(
            classifierInputSize.width,
            classifierInputSize.height,
            Bitmap.Config.ARGB_8888
        )

        val frameToReScaleTransform = getTransformationMatrix(
            croppedBitmap.width,
            croppedBitmap.height,
            classifierInputSize.width,
            classifierInputSize.height,
            0,
            //maintain aspect ratio
            true
        )

        frameToReScaleTransform.invert(cropToFrameTransform)

        val canvas = Canvas(inferenceBitmap)
        canvas.drawBitmap(croppedBitmap, frameToReScaleTransform, null)

        return inferenceBitmap

    }

}
