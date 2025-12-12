import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'my_ads_plugin_platform_interface.dart';

/// An implementation of [MyAdsPluginPlatform] that uses method channels.
class MethodChannelMyAdsPlugin extends MyAdsPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('my_ads_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
