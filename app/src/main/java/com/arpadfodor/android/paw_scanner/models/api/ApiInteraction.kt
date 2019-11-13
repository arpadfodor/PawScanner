package com.arpadfodor.android.paw_scanner.models.api

import android.net.Uri
import android.os.Handler
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

object ApiInteraction {

    var dogAPI: DogAPI

    init {

        val retrofit = Retrofit.Builder()
            .baseUrl(DogAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        this.dogAPI = retrofit.create(DogAPI::class.java)

    }

    private fun <T> runCallOnBackgroundThread(call: Call<T>, onSuccess: (T) -> Unit, onError: (Throwable) -> Unit){

        val handler = Handler()

        Thread {
            try {
                val response = call.execute().body()!!
                handler.post { onSuccess(response) }

            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { onError(e) }
            }
        }.start()

    }

    fun loadBreedInfo(name: String, onSuccess: (List<BreedInfo>) -> Unit, onError: (Throwable) -> Unit){

        if(name.isEmpty()){
            return
        }

        val getBreedInfoRequest = dogAPI.getBreedInfo(name)
        runCallOnBackgroundThread(getBreedInfoRequest, onSuccess, onError)

    }

    fun loadBreedImage(id: String, onSuccess: (List<BreedImage>) -> Unit, onError: (Throwable) -> Unit){

        if(id.isEmpty()){
            return
        }

        val getBreedImageUrlRequest = dogAPI.getBreedImageURL(URLEncoder.encode(id, "utf-8"), "small")
        runCallOnBackgroundThread(getBreedImageUrlRequest, onSuccess, onError)

    }

}