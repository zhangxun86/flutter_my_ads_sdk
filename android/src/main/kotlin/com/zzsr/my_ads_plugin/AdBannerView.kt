package com.zzsr.my_ads_plugin // 【确认包名】

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
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
        // 1. 设置容器的布局参数，确保它能撑开
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 2. 【调试用】给个红色背景。如果能看到红块但没广告，说明 View 创建成功但广告没加载出来。
        // 如果广告加载成功，广告会挡住这个红色。
        // container.setBackgroundColor(Color.parseColor("#FFEEEE"))

        val posId = creationParams?.get("posId") as? String
        if (posId != null) {
            loadBannerAd(posId)
        } else {
            Log.e(TAG, "PosId is null")
        }
    }

    private fun loadBannerAd(posId: String) {
        Log.i(TAG, "开始加载 Banner: $posId")

        val parameter = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setWidth(context.resources.displayMetrics.widthPixels)
            .setHeight(0) // 0 表示自适应
            .build()

        ampsBannerAd = AMPSBannerAd(context, parameter, object : AMPSBannerLoadEventListener {
            override fun onAmpsAdLoaded() {
                Log.d(TAG, "Banner Loaded Success")
                // 将广告添加到容器
                container.removeAllViews() // 清理旧的
                ampsBannerAd?.show(container)
            }

            override fun onAmpsAdFailed(ampsError: AMPSError?) {
                Log.e(TAG, "Banner Failed: ${ampsError?.toString()}")
                // 如果失败，可以在这里加个 TextView 提示（仅调试用）
                /*
                val tv = TextView(context)
                tv.text = "Banner加载失败"
                container.addView(tv)
                */
            }

            override fun onAmpsAdShow() { Log.d(TAG, "Banner Show") }
            override fun onAmpsAdClicked() { Log.d(TAG, "Banner Clicked") }
            override fun onAmpsAdDismiss() { Log.d(TAG, "Banner Dismiss") }
        })
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