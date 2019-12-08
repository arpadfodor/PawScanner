package com.arpadfodor.android.paw_scanner.models.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface CatAPI {

    companion object {
        const val CAT_API_KEY = "09a8bffc-21a8-4da0-b08a-051765989c5b"
        const val ENDPOINT_URL = "https://api.thecatapi.com/v1/"
        const val BREED_SEARCH = "breeds/search"
        const val IMAGE_SEARCH = "images/search"
    }

    @Headers("x-api-key: $CAT_API_KEY")
    @GET(BREED_SEARCH)
    fun getBreedInfo(@Query("q") name: String): Call<List<CatBreedInfo>>

    @Headers("x-api-key: $CAT_API_KEY")
    @GET(IMAGE_SEARCH)
    fun getBreedImageURL(@Query("breed_id") id: String, @Query("size") size: String): Call<List<CatBreedImage>>

}