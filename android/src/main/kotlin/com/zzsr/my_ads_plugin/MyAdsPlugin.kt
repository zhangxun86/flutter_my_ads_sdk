package com.zzsr.my_ads_plugin

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import xyz.adscope.amps.AMPSSDK
import xyz.adscope.amps.common.AMPSError
import xyz.adscope.amps.config.AMPSPrivacyConfig
import xyz.adscope.amps.config.AMPSRequestParameters
import xyz.adscope.amps.init.AMPSInitConfig
import xyz.adscope.amps.init.inter.IAMPSInitCallback
import xyz.adscope.amps.ad.splash.AMPSSplashAd
import xyz.adscope.amps.ad.splash.AMPSSplashLoadEventListener
import xyz.adscope.amps.ad.interstitial.AMPSInterstitialAd
import xyz.adscope.amps.ad.interstitial.AMPSInterstitialLoadEventListener
import xyz.adscope.amps.ad.reward.AMPSRewardVideoAd
import xyz.adscope.amps.ad.reward.AMPSRewardVideoLoadEventListener

class MyAdsPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel : MethodChannel
    private var context: Context? = null
    private var activity: Activity? = null

    private val TAG = "AdPlugin"
    private var isSdkInitialized = false

    private var splashContainer: FrameLayout? = null
    private var mSplashAd: AMPSSplashAd? = null
    private var splashLoadsLeft = 0
    private var currentSplashPosId: String = ""

    private var mAMPSInterstitialAd: AMPSInterstitialAd? = null
    private var interstitialLoadsLeft = 0
    private var currentInterstitialPosId: String = ""

    private var mAMPSRewardVideoAd: AMPSRewardVideoAd? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "my_ads_plugin")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext

        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_banner_view", AdBannerViewFactory())
        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_native_view", AdNativeViewFactory())
        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_unified_view", AdUnifiedViewFactory())
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // =========================================================
    // 2. Activity 绑定 (在此处尝试隐藏标题栏)
    // =========================================================
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        // 【增强】一绑定就尝试隐藏
        hideSystemUI()
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        hideSystemUI()
    }
    override fun onDetachedFromActivity() { activity = null }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val args = call.arguments as? Map<String, Any>
        val posId = args?.get("posId") as? String
        val count = args?.get("count") as? Int ?: 1

        when (call.method) {
            "initSdk" -> {
                val appId = args?.get("appId") as? String
                val appName = args?.get("appName") as? String ?: "App"
                if (appId != null && context != null) {
                    initSdk(appId, appName)
                    result.success(true)
                } else {
                    result.error("INIT_ERROR", "AppId is null", null)
                }
            }
            "showSplash" -> {
                if (activity != null && posId != null) {
                    currentSplashPosId = posId
                    splashLoadsLeft = count
                    startSplashRecursively()
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity/PosId error", null)
                }
            }
            "showInterstitial" -> {
                if (activity != null && posId != null) {
                    currentInterstitialPosId = posId
                    interstitialLoadsLeft = count
                    loadInterstitialRecursively()
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity/PosId error", null)
                }
            }
            "showRewardVideo" -> {
                if (activity != null && posId != null) {
                    loadRewardVideo(posId)
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity/PosId error", null)
                }
            }
            else -> result.notImplemented()
        }
    }

    // =========================================================
    // 【核心修改】强力隐藏系统 UI 和 标题栏
    // =========================================================
    private fun hideSystemUI() {
        val act = activity ?: return
        act.runOnUiThread {
            try {
                // 1. 尝试隐藏原生 ActionBar
                act.actionBar?.hide()

                // 2. 尝试请求无标题特性 (必须在 setContentView 之前，但也试一下)
                act.requestWindowFeature(Window.FEATURE_NO_TITLE)
            } catch (e: Exception) {
                // 忽略错误
            }

            // 3. 沉浸式全屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                act.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                act.window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
            }
        }
    }

    // ... 辅助方法 ...
    private fun sendEvent(adType: String, event: String, msg: String = "") {
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("onAdEvent", mapOf("adType" to adType, "event" to event, "msg" to msg))
        }
    }

    private fun initSdk(appId: String, appName: String) {
        val config = AMPSInitConfig.Builder().setAppId(appId).setAppName(appName).openDebugLog(true)
            .setAMPSPrivacyConfig(object : AMPSPrivacyConfig() {
                override fun isSupportPersonalized(): Boolean = false
                override fun isCanUsePhoneState(): Boolean = super.isCanUsePhoneState()
                override fun isCanUseLocation(): Boolean = false
                override fun isCanUseWifiState(): Boolean = super.isCanUseWifiState()
                override fun isCanUseOaid(): Boolean = false
            }).build()

        AMPSSDK.init(context, config, object : IAMPSInitCallback {
            override fun successCallback() { sendEvent("init", "success") }
            override fun failCallback(e: AMPSError?) { sendEvent("init", "fail", e?.toString() ?: "") }
        })
    }

    private fun startSplashRecursively() {
        val act = activity ?: return
        if (splashLoadsLeft <= 0) { removeSplashContainer(); return }
        splashLoadsLeft--

        if (splashContainer == null) {
            splashContainer = FrameLayout(act)
            act.addContentView(splashContainer, ViewGroup.LayoutParams(-1, -1))
        } else if (splashContainer?.parent == null) {
            act.addContentView(splashContainer, ViewGroup.LayoutParams(-1, -1))
        }

        mSplashAd?.destroy()
        val params = AMPSRequestParameters.Builder().setSpaceId(currentSplashPosId).setTimeOut(5000)
            .setWidth(act.resources.displayMetrics.widthPixels).setHeight(act.resources.displayMetrics.heightPixels).build()

        mSplashAd = AMPSSplashAd(act, params, object: AMPSSplashLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("splash", "onLoaded")
                hideSystemUI() // 再次强制隐藏
                mSplashAd?.show(splashContainer)
            }
            override fun onAmpsAdDismiss() { sendEvent("splash", "onDismiss"); startSplashRecursively() }
            override fun onAmpsAdFailed(e: AMPSError?) { sendEvent("splash", "onFailed", e?.toString() ?: ""); startSplashRecursively() }
            override fun onAmpsAdShow() { sendEvent("splash", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("splash", "onClick") }
        })
        mSplashAd?.loadAd()
    }

    private fun removeSplashContainer() {
        activity?.runOnUiThread {
            if (splashContainer?.parent != null) {
                (splashContainer?.parent as ViewGroup).removeView(splashContainer)
                splashContainer = null
            }
        }
    }

    private fun loadInterstitialRecursively() {
        val act = activity ?: return
        if (interstitialLoadsLeft <= 0) return
        interstitialLoadsLeft--

        val params = AMPSRequestParameters.Builder().setSpaceId(currentInterstitialPosId).setTimeOut(5000)
            .setWidth(600).setHeight(600).build()
        mAMPSInterstitialAd?.destroy()
        mAMPSInterstitialAd = AMPSInterstitialAd(act, params, object: AMPSInterstitialLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("interstitial", "onLoaded")
                if (!act.isFinishing) {
                    hideSystemUI() // 再次强制隐藏
                    mAMPSInterstitialAd?.show(act)
                }
            }
            override fun onAmpsAdDismiss() { sendEvent("interstitial", "onDismiss"); loadInterstitialRecursively() }
            override fun onAmpsAdFailed(e: AMPSError?) { sendEvent("interstitial", "onFailed", e?.toString() ?: ""); loadInterstitialRecursively() }
            override fun onAmpsAdShow() { sendEvent("interstitial", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("interstitial", "onClick") }
            override fun onAmpsSkippedAd() {}
            override fun onAmpsVideoPlayStart() {}
            override fun onAmpsVideoPlayEnd() {}
        })
        mAMPSInterstitialAd?.loadAd()
    }

    private fun loadRewardVideo(posId: String) {
        val act = activity ?: return
        val params = AMPSRequestParameters.Builder().setSpaceId(posId).setAdCount(1).build()
        mAMPSRewardVideoAd?.destroy()
        mAMPSRewardVideoAd = AMPSRewardVideoAd(act, params, object: AMPSRewardVideoLoadEventListener {
            override fun onAmpsAdLoad() {
                sendEvent("reward", "onLoaded")
                if (!act.isFinishing) {
                    hideSystemUI() // 再次强制隐藏
                    mAMPSRewardVideoAd?.show(act)
                }
            }
            override fun onAmpsAdCached() {}
            override fun onAmpsAdFailed(e: AMPSError?) { sendEvent("reward", "onFailed", e?.toString() ?: "") }
            override fun onAmpsAdRewardArrived(valid: Boolean, type: Int, extra: MutableMap<String, Any>?) {
                sendEvent("reward", "onReward", valid.toString())
            }
            override fun onAmpsAdDismiss() { sendEvent("reward", "onDismiss") }
            override fun onAmpsAdShow() { sendEvent("reward", "onShow") }
            override fun onAmpsAdVideoClick() { sendEvent("reward", "onClick") }
            override fun onAmpsAdVideoComplete() {}
            override fun onAmpsAdVideoError() {}
        })
        mAMPSRewardVideoAd?.loadAd()
    }
}