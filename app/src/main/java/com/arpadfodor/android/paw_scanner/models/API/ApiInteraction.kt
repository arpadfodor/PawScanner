package com.arpadfodor.android.paw_scanner.models.API

import android.content.Context
import android.graphics.Bitmap
import com.arpadfodor.android.paw_scanner.R
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import okhttp3.Request

object ApiInteraction {

    fun loadBreedInfo(context: Context, name: String): String{

        if(name.isEmpty()){
            return ""
        }

        var breedText = ""

        try{

            val client = OkHttpClient()

            val request = Request.Builder()
                .url(context.getString(R.string.dog_api_breed_search, name))
                .get()
                .addHeader(context.getString(R.string.dog_api_key_header_name), context.getString(R.string.dog_api_key))
                .build()

            val response = client.newCall(request).execute()
            breedText = response.networkResponse.toString()

        }
        catch (e: Error){}

        return breedText

    }

    fun loadBreedImage(context: Context, name: String): Bitmap{

        lateinit var loadedImage: Bitmap

        //load breed data from API
        val loaderThread = Thread(Runnable {

            try{

                val client = OkHttpClient()

                val request = Request.Builder()
                    .url(context.getString(R.string.dog_api_breed_search, name))
                    .get()
                    .addHeader(context.getString(R.string.dog_api_key_header_name), context.getString(R.string.dog_api_key))
                    .build()

                val response = client.newCall(request).execute()
                val imageUrl = response.body.toString()

                loadedImage = Glide.with(context)
                    .asBitmap()
                    .load(imageUrl)
                    .submit()
                    .get()

            }
            catch (e: Error){}

        })
        loaderThread.start()
        loaderThread.join()

        return loadedImage

    }

}