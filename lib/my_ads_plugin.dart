
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

  static Future<void> showSplash(String posId) async {
    await _channel.invokeMethod('showSplash', {"posId": posId});
  }

  static Future<void> showInterstitial(String posId) async {
    await _channel.invokeMethod('showInterstitial', {"posId": posId});
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