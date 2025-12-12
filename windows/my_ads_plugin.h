#ifndef FLUTTER_PLUGIN_MY_ADS_PLUGIN_H_
#define FLUTTER_PLUGIN_MY_ADS_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace my_ads_plugin {

class MyAdsPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  MyAdsPlugin();

  virtual ~MyAdsPlugin();

  // Disallow copy and assign.
  MyAdsPlugin(const MyAdsPlugin&) = delete;
  MyAdsPlugin& operator=(const MyAdsPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace my_ads_plugin

#endif  // FLUTTER_PLUGIN_MY_ADS_PLUGIN_H_
