package com.zzsr.untitled4 // 【重要】确认包名

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bumptech.glide.Glide
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

import com.example.untitled4.R

import xyz.adscope.amps.ad.unified.AMPSUnifiedNativeAd
import xyz.adscope.amps.ad.unified.AMPSUnifiedNativeLoadEventListener
import xyz.adscope.amps.ad.unified.inter.AMPSUnifiedNativeItem
import xyz.adscope.amps.ad.unified.view.AMPSUnifiedRootContainer
import xyz.adscope.amps.ad.nativead.adapter.AMPSNativeAdExpressListener
import xyz.adscope.amps.common.AMPSError
import xyz.adscope.amps.config.AMPSRequestParameters
import java.util.ArrayList

class AdUnifiedViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>?
        return AdUnifiedView(context, viewId, creationParams)
    }
}

class AdUnifiedView(
    private val context: Context,
    id: Int,
    creationParams: Map<String?, Any?>?
) : PlatformView {

    private val TAG = "AdUnified"
    private val container: FrameLayout = FrameLayout(context)
    private var mAMPSUnifiedNativeAd: AMPSUnifiedNativeAd? = null
    private var currentNativeItem: AMPSUnifiedNativeItem? = null

    init {
        // 1. 设置全屏参数
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // 【调试】设置绿色背景。如果你看到绿色块但无广告，说明广告没填入。
        // 如果看到广告挡住了绿色，说明成功。
        // container.setBackgroundColor(Color.GREEN)

        val posId = creationParams?.get("posId") as? String
        if (!posId.isNullOrEmpty()) {
            loadUnifiedAd(posId)
        }
    }

    private fun getActivity(context: Context?): Activity? {
        if (context == null) return null
        if (context is Activity) return context
        if (context is ContextWrapper) return getActivity(context.baseContext)
        return null
    }

    private fun loadUnifiedAd(posId: String) {
        Log.i(TAG, "Start loading Unified Ad: $posId")

        val parameter = AMPSRequestParameters.Builder()
            .setSpaceId(posId)
            .setWidth(context.resources.displayMetrics.widthPixels)
            .setHeight(0)
            .setTimeOut(5000)
            .setAdCount(1)
            .build()

        mAMPSUnifiedNativeAd = AMPSUnifiedNativeAd(context, parameter, object : AMPSUnifiedNativeLoadEventListener() {
            override fun onAmpsAdLoad(nativeItems: List<AMPSUnifiedNativeItem>?) {
                if (nativeItems.isNullOrEmpty()) {
                    Log.e(TAG, "Ad list is empty")
                    return
                }
                Log.e(TAG, "Unified Ad Loaded Success! Count: ${nativeItems.size}")

                val item = nativeItems[0]
                currentNativeItem = item

                showUnified(item)
            }

            override fun onAmpsAdFailed(error: AMPSError?) {
                Log.e(TAG, "Unified Ad Failed: ${error?.toString()}")
            }
        })
        mAMPSUnifiedNativeAd?.loadAd()
    }

    private fun showUnified(unifiedItem: AMPSUnifiedNativeItem?) {
        if (unifiedItem == null || !unifiedItem.isValid) return

        container.post {
            if (unifiedItem.isExpressAd) {
                Log.d(TAG, "Type: Express Ad (模板)")
                renderNativeExpressAd(unifiedItem)
            } else {
                Log.d(TAG, "Type: Self Render Ad (自渲染)")
                // 强制渲染，不区分 Pattern，先尝试显示出来
                renderUnifiedNativeAd(unifiedItem)
            }
        }
    }

    private fun renderNativeExpressAd(unifiedItem: AMPSUnifiedNativeItem) {
        unifiedItem.setNativeAdExpressListener(object : AMPSNativeAdExpressListener() {
            override fun onRenderSuccess(view: View?, width: Float, height: Float) {
                Log.d(TAG, "Express Render Success")
                container.removeAllViews()
                if (view != null) {
                    if (view.parent != null) (view.parent as ViewGroup).removeView(view)
                    container.addView(view)
                }
            }
            override fun onRenderFail(view: View?, msg: String?, code: Int) {
                Log.e(TAG, "Express Render Fail: $msg")
            }
            override fun onAdShow() {}
            override fun onAdClicked() {}
            override fun onAdClosed(view: View?) { container.removeAllViews() }
        })
        unifiedItem.render()
    }

    private fun renderUnifiedNativeAd(unifiedItem: AMPSUnifiedNativeItem) {
        // 打印广告类型
        val pattern = unifiedItem.adPattern
        Log.e(TAG, "Current Ad Pattern: $pattern")

        // 无论什么类型，都尝试用通用的 XML 布局填充
        val itemView = inflateImageText(unifiedItem)

        container.removeAllViews()
        container.addView(itemView)
    }

    private fun inflateImageText(unifiedItem: AMPSUnifiedNativeItem): View {
        // 加载布局
        val itemView = LayoutInflater.from(context).inflate(R.layout.native_unified_layout, null)

        // 【关键】确保 itemView 有宽高
        itemView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val rootContainer = itemView.findViewById<AMPSUnifiedRootContainer>(R.id.ad_unified_container)
        val mainImageContainer = itemView.findViewById<FrameLayout>(R.id.ad_main_image_container)
        val adLogoRl = itemView.findViewById<RelativeLayout>(R.id.ad_logo_rl)
        val actionRl = itemView.findViewById<RelativeLayout>(R.id.ad_action_rl)
        val titleTv = itemView.findViewById<TextView>(R.id.ad_title)
        val descTv = itemView.findViewById<TextView>(R.id.ad_desc)

        val clickViews = ArrayList<View>()
        clickViews.add(mainImageContainer)
        val actionViews = ArrayList<View>()
        actionViews.add(actionRl)

        // 1. 标题
        if (!unifiedItem.title.isNullOrEmpty()) {
            titleTv.text = unifiedItem.title
            clickViews.add(titleTv)
            Log.d(TAG, "Title set: ${unifiedItem.title}")
        }

        // 2. 描述
        if (!unifiedItem.desc.isNullOrEmpty()) {
            descTv.text = unifiedItem.desc
            clickViews.add(descTv)
        }

        // 3. Logo
        if (!unifiedItem.adSourceLogoUrl.isNullOrEmpty()) {
            val logoIv = ImageView(context)
            Glide.with(context).load(unifiedItem.adSourceLogoUrl).into(logoIv)
            adLogoRl.addView(logoIv)
        } else if (unifiedItem.adSourceLogo != null) {
            adLogoRl.addView(unifiedItem.adSourceLogo)
        }

        // 4. 按钮
        if (!unifiedItem.actionButtonText.isNullOrEmpty()) {
            val btnText = unifiedItem.actionButtonText
            val textView = TextView(context)
            textView.text = btnText
            textView.setTextColor(Color.WHITE)
            textView.textSize = 10f
            textView.gravity = Gravity.CENTER
            textView.setBackgroundColor(Color.parseColor("#2196F3"))
            actionRl.addView(textView, RelativeLayout.LayoutParams(-1, -1))
        }

        // 5. 主图/视频
        if (unifiedItem.isViewObject) {
            // 如果是视频或特殊视图
            val unifiedView = unifiedItem.mainImageView
            if (unifiedView?.view != null) {
                Log.d(TAG, "Adding Video/View Object")
                mainImageContainer.addView(unifiedView.view)
            }
        } else {
            // 如果是普通图片 URL
            val url = unifiedItem.mainImageUrl
            if (!url.isNullOrEmpty()) {
                Log.d(TAG, "Loading Image URL: $url")
                val imageView = ImageView(context)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(context).load(url).into(imageView)
                mainImageContainer.addView(imageView, FrameLayout.LayoutParams(-1, -1))
            } else {
                // 尝试加载多图列表的第一张
                val images = unifiedItem.imagesUrl
                if (!images.isNullOrEmpty()) {
                    Log.d(TAG, "Loading Group Image URL: ${images[0]}")
                    val imageView = ImageView(context)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context).load(images[0]).into(imageView)
                    mainImageContainer.addView(imageView, FrameLayout.LayoutParams(-1, -1))
                }
            }
        }

        val activity = getActivity(context)
        if (activity != null) {
            unifiedItem.bindAdToRootContainer(activity, rootContainer, clickViews, actionViews)
        } else {
            Log.e(TAG, "Activity is null, bind failed")
        }

        return itemView
    }

    override fun getView(): View {
        return container
    }

    override fun dispose() {
        currentNativeItem?.destroy()
        mAMPSUnifiedNativeAd?.destroy()
    }
}