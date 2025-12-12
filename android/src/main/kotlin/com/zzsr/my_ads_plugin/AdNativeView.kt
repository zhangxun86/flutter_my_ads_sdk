package com.zzsr.my_ads_plugin // 【重要】请确认包名

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

// 【重要】根据你提供的源码修正了包名，如果还报红，请按 Alt+Enter 自动导入
import xyz.adscope.amps.ad.nativead.AMPSNativeAd
import xyz.adscope.amps.ad.nativead.AMPSNativeLoadEventListener
import xyz.adscope.amps.ad.nativead.inter.AMPSNativeAdExpressInfo
import xyz.adscope.amps.ad.nativead.adapter.AMPSNativeAdExpressListener
import xyz.adscope.amps.common.AMPSError
import xyz.adscope.amps.config.AMPSRequestParameters

class AdNativeViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>?
        return AdNativeView(context, viewId, creationParams)
    }
}

class AdNativeView(
    private val context: Context,
    id: Int,
    creationParams: Map<String?, Any?>?
) : PlatformView {

    private val TAG = "AdNative"
    private val container: FrameLayout = FrameLayout(context)
    private var mNativeAd: AMPSNativeAd? = null

    init {
        // 设置容器参数
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val posId = creationParams?.get("posId") as? String
        if (!posId.isNullOrEmpty()) {
            loadNativeAd(posId)
        } else {
            Log.e(TAG, "PosId is null")
        }
    }

    private fun loadNativeAd(posId: String) {
        Log.i(TAG, "开始加载原生广告: $posId")

        val parameter = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setTimeOut(5000)
            .setWidth(context.resources.displayMetrics.widthPixels)
            .setHeight(0) // 0代表自适应
            .setAdCount(1)
            .build()

        // 【修正点 1】类名后面加了 ()
        mNativeAd = AMPSNativeAd(context, parameter, object : AMPSNativeLoadEventListener() {

            override fun onAmpsAdLoad(resultList: MutableList<AMPSNativeAdExpressInfo>?) {
                Log.d(TAG, "Native Ad Loaded. Count: ${resultList?.size}")

                if (resultList.isNullOrEmpty()) {
                    Log.e(TAG, "Ad list is empty")
                    return
                }

                val adInfo = resultList[0]
                bindNativeExpressListener(adInfo)

                // 必须调用 render 才能显示
                adInfo.render()
            }

            override fun onAmpsAdFailed(ampsError: AMPSError?) {
                Log.e(TAG, "Native Ad Failed: ${ampsError?.toString()}")
            }
        })

        mNativeAd?.loadAd()
    }

    private fun bindNativeExpressListener(adInfo: AMPSNativeAdExpressInfo) {
        // 【修正点 2】类名后面加了 ()
        adInfo.setAMPSNativeAdExpressListener(object : AMPSNativeAdExpressListener() {

            override fun onRenderSuccess(view: View?, width: Float, height: Float) {
                Log.d(TAG, "onRenderSuccess: w=$width, h=$height")
                if (view != null) {
                    container.removeAllViews()
                    if (view.parent != null) {
                        (view.parent as ViewGroup).removeView(view)
                    }
                    container.addView(view)
                }
            }

            override fun onRenderFail(view: View?, msg: String?, code: Int) {
                Log.e(TAG, "onRenderFail: $msg (code:$code)")
            }

            override fun onAdShow() { Log.d(TAG, "Native Show") }
            override fun onAdClicked() { Log.d(TAG, "Native Clicked") }
            override fun onAdClosed(view: View?) {
                Log.d(TAG, "Native Closed")
                container.removeAllViews()
            }
        })
    }

    override fun getView(): View {
        return container
    }

    override fun dispose() {
        mNativeAd?.destroy()
        mNativeAd = null
    }
}