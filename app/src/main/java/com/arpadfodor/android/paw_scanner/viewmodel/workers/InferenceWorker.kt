package com.arpadfodor.android.paw_scanner.viewmodel.workers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arpadfodor.android.paw_scanner.model.BitmapProcessor
import com.arpadfodor.android.paw_scanner.model.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.model.Device
import com.arpadfodor.android.paw_scanner.model.Recognition
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel

class InferenceWorker(context: Context, params: WorkerParameters, model: MainViewModel) : Worker(context, params) {

    companion object{
        lateinit var viewModel: MainViewModel
    }

    var classifier: ClassifierFloatMobileNet

    init {
        viewModel = model
        classifier = ClassifierFloatMobileNet(applicationContext.assets, Device.CPU, 1)
    }

    override fun doWork(): Result {

        try {

            if(viewModel.liveImage.value == null){
                sendMessageToViewModel(List(1){
                    Recognition("0", "error", 1f, null)
                }, 0)
                return Result.failure()
            }

            val bitmap: Bitmap = BitmapProcessor.resizedBitmapToInferenceResolution(
                InferenceService.viewModel.liveImage.value!!,
                InferenceService.viewModel.classifierInputSize.value!!
            )

            //interestingly, without image resizing the results seem to be more accurate
            //bitmap = viewModel.loadedImage.value

            val startTime = SystemClock.uptimeMillis()
            val result = classifier.recognizeImage(bitmap)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            sendMessageToViewModel(result, inferenceTime)

            Result.success()

        } catch (throwable: Throwable){
            Result.failure()
        }
        return Result.failure()
    }

    private fun sendMessageToViewModel(result: List<Recognition>, inferenceTime: Long) {

        val intent = Intent("InferenceResult")

        intent.putExtra("inferenceTime", inferenceTime)
        intent.putExtra("numberOfRecognitions", result.size)

        for((index, recognition) in result.withIndex()){
            intent.putExtra("recognition-id-$index", recognition.id)
            intent.putExtra("recognition-title-$index", recognition.title)
            intent.putExtra("recognition-confidence-$index", recognition.confidence)
        }

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

    }

}