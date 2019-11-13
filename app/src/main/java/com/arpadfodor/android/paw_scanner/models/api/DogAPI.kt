package com.arpadfodor.android.paw_scanner.models.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface DogAPI {

    companion object {
        const val DOG_API_KEY = "742e9a47-bd72-4fb1-9cd4-d7d26b88ce71"
        const val ENDPOINT_URL = "https://api.thedogapi.com/v1/"
        const val BREED_SEARCH = "breeds/search"
        const val IMAGE_SEARCH = "images/search"
    }

    @Headers("x-api-key: $DOG_API_KEY")
    @GET(BREED_SEARCH)
    fun getBreedInfo(@Query("q") name: String): Call<List<BreedInfo>>

    @Headers("x-api-key: $DOG_API_KEY")
    @GET(IMAGE_SEARCH)
    fun getBreedImageURL(@Query("breed_id") id: String, @Query("size") size: String): Call<List<BreedImage>>

}