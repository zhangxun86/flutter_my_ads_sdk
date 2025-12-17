package com.zzsr.my_ads_plugin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

import xyz.adscope.amps.ad.banner.AMPSBannerAd
import xyz.adscope.amps.ad.banner.AMPSBannerLoadEventListener
import xyz.adscope.amps.common.AMPSError
import xyz.adscope.amps.config.AMPSRequestParameters

class AdBannerViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>?
        return AdBannerView(context, viewId, creationParams)
    }
}

class AdBannerView(
    private val context: Context,
    id: Int,
    creationParams: Map<String?, Any?>?
) : PlatformView {

    private val TAG = "AdBanner"
    private val container: FrameLayout = FrameLayout(context)
    private var ampsBannerAd: AMPSBannerAd? = null

    init {
        container.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        val posId = creationParams?.get("posId") as? String
        if (!posId.isNullOrEmpty()) {
            loadBannerAd(posId)
        } else {
            Log.e(TAG, "PosId is null")
        }
    }

    // 【新增】获取 Activity 的辅助方法
    private fun getActivity(context: Context?): Activity? {
        if (context == null) return null
        if (context is Activity) return context
        if (context is ContextWrapper) return getActivity(context.baseContext)
        return null
    }

    private fun loadBannerAd(posId: String) {
        Log.i(TAG, "开始加载 Banner: $posId")

        // 尝试获取 Activity，如果获取不到就用默认 context (虽然 GDT 可能会报错，但其他 SDK 可能没事)
        val act = getActivity(context)
        val contextToUse = act ?: context

        val parameter = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setWidth(context.resources.displayMetrics.widthPixels)
            .setHeight(0)
            .build()

        // 传入 contextToUse
        ampsBannerAd = AMPSBannerAd(contextToUse, parameter, object : AMPSBannerLoadEventListener {
            override fun onAmpsAdLoaded() {
                Log.d(TAG, "Banner Loaded Success")
                container.removeAllViews()
                ampsBannerAd?.show(container)
            }

            override fun onAmpsAdFailed(ampsError: AMPSError?) {
                Log.e(TAG, "Banner Failed: ${ampsError?.toString()}")
            }

            override fun onAmpsAdShow() { Log.d(TAG, "Banner Show") }
            override fun onAmpsAdClicked() { Log.d(TAG, "Banner Clicked") }
            override fun onAmpsAdDismiss() { Log.d(TAG, "Banner Dismiss") }
        })

        // 如果是 GDT，可能需要绑定 Activity
        if (act != null) {
            ampsBannerAd?.viewController = act
        }

        ampsBannerAd?.loadAd()
    }

    override fun getView(): View {
        return container
    }

    override fun dispose() {
        ampsBannerAd?.destroy()
        ampsBannerAd = null
    }
}