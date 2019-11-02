package com.arpadfodor.android.paw_scanner.viewmodel.workers

import android.app.IntentService
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.model.BitmapProcessor.resizedBitmapToInferenceResolution
import com.arpadfodor.android.paw_scanner.model.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.model.Device
import com.arpadfodor.android.paw_scanner.model.Recognition
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel

class InferenceService : IntentService("InferenceService") {

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

        if (intent.hasExtra("type")) {

            val type = intent.getIntExtra("type", MainViewModel.RECOGNITION_LIVE)

            lateinit var bitmap: Bitmap

            if(type == MainViewModel.RECOGNITION_LIVE){

                if(viewModel.liveImage.value == null){
                    sendMessageToViewModel(List(1){
                        Recognition(
                            "0",
                            "error",
                            1f,
                            null
                        )
                    }, 0)
                    return
                }

                bitmap = viewModel.liveImage.value!!

            }
            else if(type == MainViewModel.RECOGNITION_LOAD){

                if(viewModel.loadedImage.value == null || viewModel.classifierInputSize.value == null){
                    sendMessageToViewModel(List(1){
                        Recognition(
                            "0",
                            "error",
                            1f,
                            null
                        )
                    }, 0)
                    return
                }

                //interestingly, without image resizing the results seem to be more accurate
                //bitmap = viewModel.loadedImage.value
                bitmap = resizedBitmapToInferenceResolution(viewModel.loadedImage.value!!, viewModel.classifierInputSize.value!!)
            }

            val startTime = SystemClock.uptimeMillis()
            val result = classifier.recognizeImage(bitmap)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            sendMessageToViewModel(result, inferenceTime)

        }

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