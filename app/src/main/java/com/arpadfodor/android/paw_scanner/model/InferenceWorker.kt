package com.arpadfodor.android.paw_scanner.model

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel

class InferenceWorker(context: Context, params: WorkerParameters, viewModel: MainViewModel) : Worker(context, params) {

    val viewModel = viewModel
    var classifier: ClassifierFloatMobileNet

    init {
        classifier = ClassifierFloatMobileNet(applicationContext.assets, Device.CPU, 1)
    }

    override fun doWork(): Result {

        try {

            val startTime = SystemClock.uptimeMillis()
            //val result = classifier.recognizeImage(bitmap)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            //sendMessageToViewModel(result, inferenceTime)

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