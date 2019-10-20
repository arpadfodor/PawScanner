package com.arpadfodor.android.paw_scanner.viewmodel

import android.app.IntentService
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.arpadfodor.android.paw_scanner.model.ClassifierFloatMobileNet
import com.arpadfodor.android.paw_scanner.model.Device
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.arpadfodor.android.paw_scanner.model.Recognition

class InferenceService : IntentService("InferenceService") {

    lateinit var classifier: ClassifierFloatMobileNet

    override fun onCreate() {
        super.onCreate()
        classifier = ClassifierFloatMobileNet(application.assets, Device.CPU, 1)
    }

    override fun onHandleIntent(intent: Intent?) {

        intent?: return

        if (intent.hasExtra("byteArray")) {

            val bitmap = BitmapFactory.decodeByteArray(
                intent.getByteArrayExtra("byteArray"),
                0,
                intent.getByteArrayExtra("byteArray")!!.size
            )

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