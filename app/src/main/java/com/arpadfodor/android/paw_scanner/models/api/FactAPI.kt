package com.arpadfodor.android.paw_scanner.models.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface FactAPI {

    companion object {
        const val ENDPOINT_URL = "https://some-random-api.ml/facts/"
        const val CAT = "cat"
        const val DOG = "dog"
    }

    @GET(CAT)
    fun getCatFact(): Call<Fact>

    @GET(DOG)
    fun getDogFact(): Call<Fact>

}