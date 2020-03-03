package com.arpadfodor.android.paw_scanner.models

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

object TextToSpeechModel{

    var textToSpeech: TextToSpeech? = null
    var requestCounter = 0
    var textToSpeechRequestId = System.currentTimeMillis() + requestCounter

    /*
    * Initialize text to speech object if not initialized yet
    * Set text to speech listener
    */
    fun init(context: Context){

        textToSpeech?.let{
            return
        }

        textToSpeech = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech?.language = Locale.UK
            }
        })

    }

    /**
     * Start text to speech
     *
     * @param textToBeSpoken
     */
    fun speak(textToBeSpoken: String){
        requestCounter++
        textToSpeech?.speak(textToBeSpoken, TextToSpeech.QUEUE_FLUSH, null, (textToSpeechRequestId).toString())
    }

    /*
     * Stop text to speech
     */
    fun stop(){
        textToSpeech?.stop()
    }

    fun isSpeaking(): Boolean{
        return textToSpeech?. let{textToSpeech!!.isSpeaking} ?: false
    }

}