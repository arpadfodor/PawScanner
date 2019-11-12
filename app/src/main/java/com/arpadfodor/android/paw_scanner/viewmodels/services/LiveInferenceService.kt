package com.arpadfodor.android.paw_scanner.viewmodels.services

import android.app.IntentService
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.models.ai.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.models.ai.Device
import com.arpadfodor.android.paw_scanner.models.ai.Recognition
import com.arpadfodor.android.paw_scanner.viewmodels.MainViewModel

class LiveInferenceService : IntentService("LiveInferenceService") {

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

        val type = intent.getIntExtra("type", MainViewModel.RECOGNITION_LIVE)

        if (intent.hasExtra("byteArray")) {

            val bitmap = BitmapFactory.decodeByteArray(
                intent.getByteArrayExtra("byteArray"),
                0,
                intent.getByteArrayExtra("byteArray")!!.size
            )

            val startTime = SystemClock.uptimeMillis()
            val result = classifier.recognizeImage(bitmap)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            sendMessageToViewModel(result, inferenceTime, type)

        }

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