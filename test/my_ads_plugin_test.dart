import 'package:flutter_test/flutter_test.dart';
import 'package:my_ads_plugin/my_ads_plugin.dart';
import 'package:my_ads_plugin/my_ads_plugin_platform_interface.dart';
import 'package:my_ads_plugin/my_ads_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMyAdsPluginPlatform
    with MockPlatformInterfaceMixin
    implements MyAdsPluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final MyAdsPluginPlatform initialPlatform = MyAdsPluginPlatform.instance;

  test('$MethodChannelMyAdsPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMyAdsPlugin>());
  });

  test('getPlatformVersion', () async {
    MyAdsPlugin myAdsPlugin = MyAdsPlugin();
    MockMyAdsPluginPlatform fakePlatform = MockMyAdsPluginPlatform();
    MyAdsPluginPlatform.instance = fakePlatform;

    expect(await myAdsPlugin.getPlatformVersion(), '42');
  });
}
