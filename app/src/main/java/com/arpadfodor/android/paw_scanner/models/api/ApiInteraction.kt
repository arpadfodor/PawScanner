package com.arpadfodor.android.paw_scanner.models.api

import android.os.Handler
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

object ApiInteraction {

    var dogAPI: DogAPI
    var factAPI: FactAPI

    init {

        val retrofitDogApi = Retrofit.Builder()
            .baseUrl(DogAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val retrofitFactApi = Retrofit.Builder()
            .baseUrl(FactAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        this.dogAPI = retrofitDogApi.create(DogAPI::class.java)
        this.factAPI = retrofitFactApi.create(FactAPI::class.java)

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

    fun loadCatFact(onSuccess: (Fact) -> Unit, onError: (Throwable) -> Unit){
        val getCatFactRequest = factAPI.getCatFact()
        runCallOnBackgroundThread(getCatFactRequest, onSuccess, onError)
    }

    fun loadDogFact(onSuccess: (Fact) -> Unit, onError: (Throwable) -> Unit){
        val getDogFactRequest = factAPI.getDogFact()
        runCallOnBackgroundThread(getDogFactRequest, onSuccess, onError)
    }

}