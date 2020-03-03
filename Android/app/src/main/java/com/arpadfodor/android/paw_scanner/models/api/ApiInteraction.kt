package com.arpadfodor.android.paw_scanner.models.api

import android.os.Handler
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

object ApiInteraction {

    const val HUMAN_PREFIX = 1
    const val CAT_PREFIX = 2
    const val DOG_PREFIX = 3

    var dogAPI: DogAPI
    var catAPI: CatAPI
    var factAPI: FactAPI

    init {

        val retrofitDogApi = Retrofit.Builder()
            .baseUrl(DogAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val retrofitCatApi = Retrofit.Builder()
            .baseUrl(CatAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val retrofitFactApi = Retrofit.Builder()
            .baseUrl(FactAPI.ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        this.dogAPI = retrofitDogApi.create(DogAPI::class.java)
        this.catAPI = retrofitCatApi.create(CatAPI::class.java)
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

    fun loadDogBreedInfo(name: String, onSuccess: (List<DogBreedInfo>) -> Unit, onError: (Throwable) -> Unit){

        if(name.isEmpty()){
            return
        }

        val getBreedInfoRequest = dogAPI.getBreedInfo(name)
        runCallOnBackgroundThread(getBreedInfoRequest, onSuccess, onError)

    }

    fun loadCatBreedInfo(name: String, onSuccess: (List<CatBreedInfo>) -> Unit, onError: (Throwable) -> Unit){

        if(name.isEmpty()){
            return
        }

        val getBreedInfoRequest = catAPI.getBreedInfo(name)
        runCallOnBackgroundThread(getBreedInfoRequest, onSuccess, onError)

    }

    fun loadDogBreedImage(id: String, onSuccess: (List<DogBreedImage>) -> Unit, onError: (Throwable) -> Unit){

        if(id.isEmpty()){
            return
        }

        val getBreedImageUrlRequest = dogAPI.getBreedImageURL(URLEncoder.encode(id, "utf-8"), "small")
        runCallOnBackgroundThread(getBreedImageUrlRequest, onSuccess, onError)

    }

    fun loadCatBreedImage(id: String, onSuccess: (List<CatBreedImage>) -> Unit, onError: (Throwable) -> Unit){

        if(id.isEmpty()){
            return
        }

        val getBreedImageUrlRequest = catAPI.getBreedImageURL(URLEncoder.encode(id, "utf-8"), "small")
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