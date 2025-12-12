#include "include/my_ads_plugin/my_ads_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "my_ads_plugin.h"

void MyAdsPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  my_ads_plugin::MyAdsPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
