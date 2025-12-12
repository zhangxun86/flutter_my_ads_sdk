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

    // 通信通道
    private lateinit var channel : MethodChannel

    // 上下文环境
    private var context: Context? = null
    private var activity: Activity? = null

    // 全局变量
    private val TAG = "AdPlugin"
    private var isSdkInitialized = false

    // --- 广告对象持有引用，防止被回收 ---
    // 开屏
    private var splashContainer: FrameLayout? = null
    private var mSplashAd: AMPSSplashAd? = null

    // 插屏
    private var mAMPSInterstitialAd: AMPSInterstitialAd? = null

    // 激励视频
    private var mAMPSRewardVideoAd: AMPSRewardVideoAd? = null


    // =========================================================
    // 1. Flutter 引擎绑定 (注册 ViewFactory 和 Channel)
    // =========================================================
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "my_ads_plugin") // 通道名
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext

        // 注册 PlatformView (Banner, Native, Unified)
        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_banner_view", AdBannerViewFactory())
        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_native_view", AdNativeViewFactory())
        flutterPluginBinding.platformViewRegistry.registerViewFactory("ad_unified_view", AdUnifiedViewFactory())
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // =========================================================
    // 2. Activity 生命周期绑定 (获取 Activity 句柄)
    // =========================================================
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    // =========================================================
    // 3. 处理 Flutter 方法调用
    // =========================================================
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val args = call.arguments as? Map<String, Any>
        val posId = args?.get("posId") as? String

        when (call.method) {
            // --- 初始化 SDK ---
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

            // --- 展示开屏广告 ---
            "showSplash" -> {
                if (activity == null) {
                    result.error("NO_ACTIVITY", "Activity is null, cannot show splash", null)
                    return
                }
                if (posId != null) {
                    startSplashFromPlugin(posId)
                    result.success(true)
                } else {
                    result.error("ARGS_ERROR", "PosId is null", null)
                }
            }

            // --- 展示插屏广告 ---
            "showInterstitial" -> {
                if (activity != null && posId != null) {
                    loadInterstitial(posId)
                    result.success(true)
                } else {
                    result.error("ERROR", "Activity or PosId is null", null)
                }
            }

            // --- 展示激励视频 ---
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

    // =========================================================
    // 4. 辅助方法：发送事件给 Flutter
    // =========================================================
    private fun sendEvent(adType: String, event: String, msg: String = "") {
        // 确保在主线程发送
        Handler(Looper.getMainLooper()).post {
            val params = mapOf(
                "adType" to adType,
                "event" to event,
                "msg" to msg
            )
            channel.invokeMethod("onAdEvent", params)
        }
    }

    // =========================================================
    // 5. SDK 初始化逻辑
    // =========================================================
    private fun initSdk(appId: String, appName: String) {
        Log.d(TAG, "Initializing SDK: $appId")

        val config = AMPSInitConfig.Builder()
            .setAppId(appId)
            .setAppName(appName)
            .openDebugLog(true)
            .setAMPSPrivacyConfig(object : AMPSPrivacyConfig() {
                override fun isSupportPersonalized(): Boolean = false
                override fun isCanUsePhoneState(): Boolean = super.isCanUsePhoneState()
                override fun isCanUseLocation(): Boolean = false
                override fun isCanUseWifiState(): Boolean = super.isCanUseWifiState()
                override fun isCanUseOaid(): Boolean = false
                override fun isCanUseAppList(): Boolean = false
                override fun isCanUseAndroidId(): Boolean = false
                override fun isCanUseMacAddress(): Boolean = false
                override fun isCanUseWriteExternal(): Boolean = super.isCanUseWriteExternal()
                override fun isCanUseShakeAd(): Boolean = super.isCanUseShakeAd()
                override fun isCanUseRecordAudio(): Boolean = false
                override fun isCanUseIP(): Boolean = false
                override fun isCanUseSimOperator(): Boolean = false
            })
            .build()

        AMPSSDK.init(context, config, object : IAMPSInitCallback {
            override fun successCallback() {
                isSdkInitialized = true
                sendEvent("init", "success")
                Log.d(TAG, "SDK Init Success")
            }
            override fun failCallback(e: AMPSError?) {
                sendEvent("init", "fail", e?.toString() ?: "")
                Log.e(TAG, "SDK Init Failed: ${e?.toString()}")
            }
        })
    }

    // =========================================================
    // 6. 开屏广告逻辑 (动态添加 View)
    // =========================================================
    private fun startSplashFromPlugin(posId: String) {
        val act = activity ?: return

        // 如果容器不存在，创建一个 FrameLayout 并添加到 Activity 根视图
        if (splashContainer == null) {
            splashContainer = FrameLayout(act)
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            act.addContentView(splashContainer, params)
        }

        val params = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setWidth(act.resources.displayMetrics.widthPixels)
            .setHeight(act.resources.displayMetrics.heightPixels)
            .build()

        // 销毁旧的
        mSplashAd?.destroy()

        mSplashAd = AMPSSplashAd(act, params, object: AMPSSplashLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("splash", "onLoaded")
                mSplashAd?.show(splashContainer)
            }

            override fun onAmpsAdDismiss() {
                sendEvent("splash", "onDismiss")
                removeSplashContainer()
            }

            override fun onAmpsAdFailed(e: AMPSError?) {
                sendEvent("splash", "onFailed", e?.toString() ?: "")
                removeSplashContainer()
            }

            override fun onAmpsAdShow() { sendEvent("splash", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("splash", "onClick") }
        })
        mSplashAd?.loadAd()
    }

    // 移除开屏容器
    private fun removeSplashContainer() {
        val act = activity ?: return
        act.runOnUiThread {
            if (splashContainer != null && splashContainer!!.parent != null) {
                (splashContainer!!.parent as ViewGroup).removeView(splashContainer)
                splashContainer = null
            }
        }
    }

    // =========================================================
    // 7. 插屏广告逻辑
    // =========================================================
    private fun loadInterstitial(posId: String) {
        val act = activity ?: return

        val params = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setWidth(600)
            .setHeight(600)
            .build()

        mAMPSInterstitialAd?.destroy()

        mAMPSInterstitialAd = AMPSInterstitialAd(act, params, object: AMPSInterstitialLoadEventListener {
            override fun onAmpsAdLoaded() {
                sendEvent("interstitial", "onLoaded")
                if (!act.isFinishing) {
                    mAMPSInterstitialAd?.show(act)
                }
            }
            override fun onAmpsAdFailed(e: AMPSError?) {
                sendEvent("interstitial", "onFailed", e?.toString() ?: "")
            }
            override fun onAmpsAdDismiss() { sendEvent("interstitial", "onDismiss") }
            override fun onAmpsAdShow() { sendEvent("interstitial", "onShow") }
            override fun onAmpsAdClicked() { sendEvent("interstitial", "onClick") }
            override fun onAmpsSkippedAd() {}
            override fun onAmpsVideoPlayStart() {}
            override fun onAmpsVideoPlayEnd() {}
        })
        mAMPSInterstitialAd?.loadAd()
    }

    // =========================================================
    // 8. 激励视频逻辑
    // =========================================================
    private fun loadRewardVideo(posId: String) {
        val act = activity ?: return

        val params = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setAdCount(1)
            .build()

        mAMPSRewardVideoAd?.destroy()

        mAMPSRewardVideoAd = AMPSRewardVideoAd(act, params, object: AMPSRewardVideoLoadEventListener {
            override fun onAmpsAdLoad() {
                sendEvent("reward", "onLoaded")
                // Load 完成即展示，不做 Cache 等待，提高展示率
                if (!act.isFinishing) {
                    mAMPSRewardVideoAd?.show(act)
                }
            }

            override fun onAmpsAdCached() { sendEvent("reward", "onCached") }

            override fun onAmpsAdFailed(e: AMPSError?) {
                sendEvent("reward", "onFailed", e?.toString() ?: "")
                act.runOnUiThread {
                    Toast.makeText(act, "广告加载失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAmpsAdRewardArrived(valid: Boolean, type: Int, extra: MutableMap<String, Any>?) {
                // 关键回调：发放奖励
                sendEvent("reward", "onReward", valid.toString())
            }

            override fun onAmpsAdDismiss() { sendEvent("reward", "onDismiss") }
            override fun onAmpsAdShow() { sendEvent("reward", "onShow") }
            override fun onAmpsAdVideoClick() { sendEvent("reward", "onClick") }
            override fun onAmpsAdVideoComplete() { sendEvent("reward", "onComplete") }
            override fun onAmpsAdVideoError() { sendEvent("reward", "onError") }
        })
        mAMPSRewardVideoAd?.loadAd()
    }
}