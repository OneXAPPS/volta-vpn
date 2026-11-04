package com.voltavpn.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.voltavpn.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One rewarded ad per day. With AdMob mediation enabled in the dashboard, the
 * highest-paying network (short or long format) is chosen automatically.
 */
object AdsManager {
    private val started = AtomicBoolean(false)
    private var rewarded: RewardedAd? = null
    private var loading = false
    private lateinit var consent: ConsentInformation

    /** Shows the GDPR/consent form where required, then initializes the SDK. */
    fun start(activity: Activity) {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) initSdk(activity)
                }
            },
            { if (consent.canRequestAds()) initSdk(activity) },
        )
        if (consent.canRequestAds()) initSdk(activity)
    }

    private fun initSdk(context: Context) {
        if (!started.compareAndSet(false, true)) return
        // Family-safe ads only: no adult, gambling-style or mature content.
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
        MobileAds.initialize(context.applicationContext) { preload(context) }
    }

    fun preload(context: Context) {
        if (!started.get() || rewarded != null || loading) return
        loading = true
        RewardedAd.load(
            context.applicationContext, BuildConfig.REWARDED_AD_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad; loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null; loading = false
                }
            },
        )
    }

    /**
     * [onResult] gets true when the user earned the reward. If no ad is available
     * (no fill / offline) it gets null so the caller can let the user through once.
     */
    fun showRewarded(activity: Activity, onResult: (Boolean?) -> Unit) {
        val ad = rewarded
        if (ad == null) {
            preload(activity)
            onResult(null)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                preload(activity)
                onResult(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                preload(activity)
                onResult(null)
            }
        }
        ad.show(activity) { earned = true }
    }
}
