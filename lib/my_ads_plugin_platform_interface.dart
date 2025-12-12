import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'my_ads_plugin_method_channel.dart';

abstract class MyAdsPluginPlatform extends PlatformInterface {
  /// Constructs a MyAdsPluginPlatform.
  MyAdsPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static MyAdsPluginPlatform _instance = MethodChannelMyAdsPlugin();

  /// The default instance of [MyAdsPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelMyAdsPlugin].
  static MyAdsPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [MyAdsPluginPlatform] when
  /// they register themselves.
  static set instance(MyAdsPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
