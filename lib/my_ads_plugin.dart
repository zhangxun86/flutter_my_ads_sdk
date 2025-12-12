
import 'my_ads_plugin_platform_interface.dart';

import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

class MyAdsPlugin {
  static const MethodChannel _channel = MethodChannel('my_ads_plugin');

  // 初始化
  static Future<void> initSdk({required String appId, String appName = "App"}) async {
    await _channel.invokeMethod('initSdk', {"appId": appId, "appName": appName});
  }

  // 监听回调
  static void setEventHandler(Function(String adType, String event, String msg) callback) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onAdEvent') {
        final args = call.arguments;
        callback(args['adType'], args['event'], args['msg']);
      }
    });
  }

  /// 展示开屏广告
  /// [posId] 广告位ID
  /// [count] 连续展示次数，默认为 1
  static Future<void> showSplash({required String posId, int count = 1}) async {
    await _channel.invokeMethod('showSplash', {
      "posId": posId,
      "count": count, // 传给 Android
    });
  }

  /// 展示插屏广告
  /// [posId] 广告位ID
  /// [count] 连续展示次数，默认为 1
  static Future<void> showInterstitial({required String posId, int count = 1}) async {
    await _channel.invokeMethod('showInterstitial', {
      "posId": posId,
      "count": count, // 传给 Android
    });
  }

  static Future<void> showRewardVideo(String posId) async {
    await _channel.invokeMethod('showRewardVideo', {"posId": posId});
  }
}

// Banner Widget
class AdBannerWidget extends StatelessWidget {
  final String posId;
  final double height;
  const AdBannerWidget({super.key, required this.posId, this.height = 100});
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: AndroidView(
        viewType: 'ad_banner_view',
        creationParams: {"posId": posId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}

// Native Widget
class AdUnifiedWidget extends StatelessWidget {
  final String posId;
  final double height;
  const AdUnifiedWidget({super.key, required this.posId, this.height = 150});
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: AndroidView(
        viewType: 'ad_unified_view',
        creationParams: {"posId": posId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}