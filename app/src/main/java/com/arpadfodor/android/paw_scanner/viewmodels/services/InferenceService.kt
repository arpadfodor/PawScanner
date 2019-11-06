package com.arpadfodor.android.paw_scanner.viewmodels.services

import android.app.IntentService
import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.models.BitmapProcessor.resizedBitmapToInferenceResolution
import com.arpadfodor.android.paw_scanner.models.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.models.Device
import com.arpadfodor.android.paw_scanner.models.Recognition
import com.arpadfodor.android.paw_scanner.viewmodels.MainViewModel

class InferenceService: IntentService("InferenceService") {

    companion object{
        lateinit var viewModel: MainViewModel
    }

    lateinit var classifier: ClassifierFloatMobileNet

    override fun onCreate() {

        super.onCreate()

        classifier = ClassifierFloatMobileNet(
            application.assets,
            Device.CPU,
            1
        )

    }

    override fun onHandleIntent(intent: Intent?) {

        intent?: return

        val type = intent.getIntExtra("type", MainViewModel.RECOGNITION_LOAD)

        lateinit var bitmap: Bitmap

        //interestingly, without image resizing the results seem to be more accurate
        //bitmap = viewModel.loadedImage.value
        if(type == MainViewModel.RECOGNITION_LOAD){
            bitmap = resizedBitmapToInferenceResolution(viewModel.loadedImage.value?: return, viewModel.classifierInputSize.value?: return)
        }
        else if(type == MainViewModel.RECOGNITION_LIVE){
            bitmap = resizedBitmapToInferenceResolution(viewModel.liveImage?: return, viewModel.classifierInputSize.value?: return)
        }

        val startTime = SystemClock.uptimeMillis()
        val result = classifier.recognizeImage(bitmap)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        sendMessageToViewModel(result, inferenceTime, type)

    }

    private fun sendMessageToViewModel(result: List<Recognition>, inferenceTime: Long, type: Int) {

        val intent = Intent("InferenceResult")

        intent.putExtra("type", type)
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