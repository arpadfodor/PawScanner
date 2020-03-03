package com.arpadfodor.android.paw_scanner.models

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseInteraction{

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    /**
     * Initializes the whole object (Analytics, AdMob)
     *
     * @param    context            Context of the mobile ads to initialize with
     * @param    appAdMobId         AdMob Id of the app
     */
    fun init(context: Context, appAdMobId: String){
        initFirebaseAnalytics(context)
        initMobileAds(
            context,
            appAdMobId
        )
    }

    /**
     * Initializes Firebase Analytics
     *
     * @param    context            Context of the mobile ads to initialize with
     */
    private fun initFirebaseAnalytics(context: Context){
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    /**
     * Initializes the Mobile Ads SDK with an AdMob App ID
     *
     * @param    context            Context of the mobile ads to initialize with
     * @param    appAdMobId         AdMob Id of the app
     */
    private fun initMobileAds(context: Context, appAdMobId: String){
        //MobileAds.initialize(context, <your key>)
        MobileAds.initialize(context, appAdMobId)
    }

    /**
     * Loads the advertisement banner content
     *
     * @param    advertisement      AdView element to load
     */
    fun loadAdMobAd(advertisement: AdView){

        // Create an ad request. If you're running this on a physical device, check your logcat to
        // learn how to enable test ads for it. Look for a line like this one:
        // "Use AdRequest.Builder.addTestDevice("ABCDEF012345") to get test ads on this device."
        val adRequest = AdRequest.Builder()
            .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
            .build()

        // Start loading the ad in the background.
        advertisement.loadAd(adRequest)

        val adRequestMyPhone = AdRequest.Builder()
            .addTestDevice("AA90319700D9608A079CEB541F122F83")
            .build()

        advertisement.loadAd(adRequestMyPhone)

    }

}