package com.zzsr.my_ads_plugin

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
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

// SDK Imports
import xyz.adscope.amps.AMPSSDK
import xyz.adscope.amps.common.AMPSError
import xyz.adscope.amps.config.AMPSPrivacyConfig
import xyz.adscope.amps.config.AMPSRequestParameters
import xyz.adscope.amps.init.AMPSInitConfig
import xyz.adscope.amps.init.inter.IAMPSInitCallback

// Ads Imports
import xyz.adscope.amps.ad.splash.AMPSSplashAd
import xyz.adscope.amps.ad.splash.AMPSSplashLoadEventListener
import xyz.adscope.amps.ad.interstitial.AMPSInterstitialAd
import xyz.adscope.amps.ad.interstitial.AMPSInterstitialLoadEventListener
import xyz.adscope.amps.ad.reward.AMPSRewardVideoAd
import xyz.adscope.amps.ad.reward.AMPSRewardVideoLoadEventListener

/** MyAdsPlugin */
class MyAdsPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel : MethodChannel
    private var context: Context? = null
    private var activity: Activity? = null

    private val TAG = "AdPlugin"
    private var isSdkInitialized = false

    // --- 开屏广告变量 ---
    private var splashContainer: FrameLayout? = null
    private var mSplashAd: AMPSSplashAd? = null
    private var splashLoadsLeft = 0 // 剩余展示次数
    private var currentSplashPosId: String = ""

    // --- 插屏广告变量 ---
    private var mAMPSInterstitialAd: AMPSInterstitialAd? = null
    private var interstitialLoadsLeft = 0 // 剩余展示次数
    private var currentInterstitialPosId: String = ""

    // --- 激励视频变量 ---
    private var mAMPSRewardVideoAd: AMPSRewardVideoAd? = null


    // =========================================================
    // 1. Flutter 引擎绑定
    // =========================================================
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
    // 2. Activity 生命周期绑定
    // =========================================================
    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    // =========================================================
    // 3. 处理 Flutter 方法调用
    // =========================================================
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val args = call.arguments as? Map<String, Any>
        val posId = args?.get("posId") as? String
        val count = args?.get("count") as? Int ?: 1 // 获取展示次数，默认为 1

        when (call.method) {
            "initSdk" -> {
                val appId = args?.get("appId") as? String
                val appName = args?.get("appName") as? String ?: "App"
                if (appId != null && context != null) {
                    initSdk(appId, appName)
                    result.success(true)
                } else {
                    result.error("INIT_ERROR", "AppId or Context is null", null)
                }
            }

            // --- 开屏广告 (支持连续展示) ---
            "showSplash" -> {
                if (activity == null) {
                    result.error("NO_ACTIVITY", "Activity is null", null)
                    return
                }
                if (posId != null) {
                    // 设置 ID 和 总次数
                    currentSplashPosId = posId
                    splashLoadsLeft = count
                    startSplashRecursively()
                    result.success(true)
                } else {
                    result.error("ARGS_ERROR", "PosId is null", null)
                }
            }

            // --- 插屏广告 (支持连续展示) ---
            "showInterstitial" -> {
                if (activity != null && posId != null) {
                    currentInterstitialPosId = posId
                    interstitialLoadsLeft = count
                    loadInterstitialRecursively()
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity or PosId is null", null)
                }
            }

            "showRewardVideo" -> {
                if (activity != null && posId != null) {
                    loadRewardVideo(posId)
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity or PosId is null", null)
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun sendEvent(adType: String, event: String, msg: String = "") {
        Handler(Looper.getMainLooper()).post {
            val params = mapOf("adType" to adType, "event" to event, "msg" to msg)
            channel.invokeMethod("onAdEvent", params)
        }
    }

    // =========================================================
    // SDK 初始化
    // =========================================================
    private fun initSdk(appId: String, appName: String) {
        val config = AMPSInitConfig.Builder().setAppId(appId).setAppName(appName).openDebugLog(true)
            .setAMPSPrivacyConfig(object : AMPSPrivacyConfig() {
                override fun isSupportPersonalized(): Boolean = false
                override fun isCanUseLocation(): Boolean = false
                override fun isCanUseOaid(): Boolean = false
            }).build()

        AMPSSDK.init(context, config, object : IAMPSInitCallback {
            override fun successCallback() { sendEvent("init", "success") }
            override fun failCallback(e: AMPSError?) { sendEvent("init", "fail", e?.toString() ?: "") }
        })
    }

    // =========================================================
    // 开屏广告 (递归逻辑)
    // =========================================================
    private fun startSplashRecursively() {
        val act = activity ?: return

        // 1. 检查剩余次数
        if (splashLoadsLeft <= 0) {
            removeSplashContainer() // 全部展示完毕，移除容器
            return
        }
        splashLoadsLeft-- // 消耗一次机会

        // 2. 准备容器
        if (splashContainer == null) {
            splashContainer = FrameLayout(act)
            val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            act.addContentView(splashContainer, params)
        } else if (splashContainer?.parent == null) {
            act.addContentView(splashContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // 3. 加载广告
        mSplashAd?.destroy()
        val params = AMPSRequestParameters.Builder()
            .setSpaceId(currentSplashPosId)
            .setTimeOut(5000)
            .setWidth(act.resources.displayMetrics.widthPixels)
            .setHeight(act.resources.displayMetrics.heightPixels)
            .build()

        mSplashAd = AMPSSplashAd(act, params, object: AMPSSplashLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("splash", "onLoaded")
                mSplashAd?.show(splashContainer)
            }

            override fun onAmpsAdDismiss() {
                sendEvent("splash", "onDismiss")
                // 【核心】当前广告关闭后，递归调用自己，加载下一条
                startSplashRecursively()
            }

            override fun onAmpsAdFailed(e: AMPSError?) {
                sendEvent("splash", "onFailed", e?.toString() ?: "")
                // 失败也要继续尝试下一条，或者直接结束（这里选择继续）
                startSplashRecursively()
            }

            override fun onAmpsAdShow() { sendEvent("splash", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("splash", "onClick") }
        })
        mSplashAd?.loadAd()
    }

    private fun removeSplashContainer() {
        activity?.runOnUiThread {
            if (splashContainer != null && splashContainer!!.parent != null) {
                (splashContainer!!.parent as ViewGroup).removeView(splashContainer)
                splashContainer = null
            }
        }
    }

    // =========================================================
    // 插屏广告 (递归逻辑)
    // =========================================================
    private fun loadInterstitialRecursively() {
        val act = activity ?: return

        // 1. 检查剩余次数
        if (interstitialLoadsLeft <= 0) {
            return // 结束
        }
        interstitialLoadsLeft--

        // 2. 加载广告
        val params = AMPSRequestParameters.Builder()
            .setSpaceId(currentInterstitialPosId)
            .setTimeOut(5000)
            .setWidth(600).setHeight(600)
            .build()

        mAMPSInterstitialAd?.destroy()

        mAMPSInterstitialAd = AMPSInterstitialAd(act, params, object: AMPSInterstitialLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("interstitial", "onLoaded")
                if (!act.isFinishing) {
                    mAMPSInterstitialAd?.show(act)
                }
            }

            override fun onAmpsAdDismiss() {
                sendEvent("interstitial", "onDismiss")
                // 【核心】关闭后，加载下一条
                loadInterstitialRecursively()
            }

            override fun onAmpsAdFailed(e: AMPSError?) {
                sendEvent("interstitial", "onFailed", e?.toString() ?: "")
                // 失败尝试下一条
                loadInterstitialRecursively()
            }

            override fun onAmpsAdShow() { sendEvent("interstitial", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("interstitial", "onClick") }
            override fun onAmpsSkippedAd() {}
            override fun onAmpsVideoPlayStart() {}
            override fun onAmpsVideoPlayEnd() {}
        })
        mAMPSInterstitialAd?.loadAd()
    }

    // =========================================================
    // 激励视频 (无需改动)
    // =========================================================
    private fun loadRewardVideo(posId: String) {
        val act = activity ?: return
        val params = AMPSRequestParameters.Builder().setSpaceId(posId).setAdCount(1).build()
        mAMPSRewardVideoAd?.destroy()
        mAMPSRewardVideoAd = AMPSRewardVideoAd(act, params, object: AMPSRewardVideoLoadEventListener {
            override fun onAmpsAdLoad() {
                sendEvent("reward", "onLoaded")
                if (!act.isFinishing) mAMPSRewardVideoAd?.show(act)
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