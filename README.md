
Flutter 聚合广告插件 (My Ads Plugin)

这是一个集成了 穿山甲 (Pangle)、优量汇 (GDT)、京东 (JAD)、华为 (Huawei)、快手 (KS) 等主流广告联盟的 Flutter 聚合广告插件。支持开屏、插屏、激励视频、Banner 和自渲染原生广告。

1. 引入依赖

在你的 Flutter 项目的 pubspec.yaml 文件中，通过 Git 方式添加依赖：

dependencies:
  flutter:
    sdk: flutter

  # 引入聚合广告插件
  my_ads_plugin:
    git:
      url: https://github.com/zhangxun86/flutter_my_ads_sdk.git
      ref: main

运行安装命令：

code
Bash
download
content_copy
expand_less
flutter pub get
2. Android 环境配置 (必须步骤)

为了确保广告 SDK 能正常下载和编译，必须在宿主 Android 项目中进行以下配置。

2.1 配置 Maven 仓库

打开项目根目录下的 android/build.gradle，在 allprojects -> repositories 中添加广告源地址：

code
Groovy
download
content_copy
expand_less
allprojects {
    repositories {
        google()
        mavenCentral()
        
        // --- 添加以下广告 SDK 仓库 ---
        maven { url "https://artifact.bytedance.com/repository/pangle" } // 穿山甲
        maven { url 'https://jitpack.io' } // 京东/通用
        maven { url 'https://developer.huawei.com/repo/' } // 华为
    }
}
2.2 配置 AndroidManifest (解决冲突)

打开 android/app/src/main/AndroidManifest.xml。
广告 SDK 内部定义了 Label 和备份策略，容易与主工程冲突，必须添加 tools:replace。

code
Xml
download
content_copy
expand_less
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="你的应用包名"
    xmlns:tools="http://schemas.android.com/tools"> <!-- 1. 添加 tools 命名空间 -->

    <application
        android:label="你的应用名称"
        android:icon="@mipmap/ic_launcher"
        
        android:allowBackup="true"
        tools:replace="android:label,android:allowBackup"> <!-- 2. 强制替换冲突属性 -->

        <!-- ... Activity 配置 ... -->
    </application>
</manifest>
2.3 设置 MinSDK 和 兼容性

打开 android/app/build.gradle，确保 minSdkVersion 至少是 21。

code
Groovy
download
content_copy
expand_less
android {
    defaultConfig {
        minSdkVersion 21 
    }
}

打开 android/gradle.properties，确保开启了 AndroidX 和 Jetifier（用于兼容旧版 SDK）：

code
Properties
download
content_copy
expand_less
android.useAndroidX=true
android.enableJetifier=true
3. Flutter 代码集成
3.1 初始化 SDK

建议在 App 启动时（如 main.dart 的 initState）初始化。

code
Dart
download
content_copy
expand_less
import 'package:my_ads_plugin/my_ads_plugin.dart';

// ...

@override
void initState() {
  super.initState();
  
  // 1. 设置全局事件监听 (处理奖励回调等)
  MyAdsPlugin.setEventHandler((adType, event, msg) {
    print("广告事件: [$adType] -> $event ($msg)");
    
    // 示例：监听激励视频奖励
    if (adType == 'reward' && event == 'onReward' && msg == 'true') {
      print(">>> 恭喜获得奖励 <<<");
    }
  });

  // 2. 初始化 SDK
  MyAdsPlugin.initSdk(
    appId: "你的AppID", 
    appName: "应用名称"
  );
}
3.2 各种广告调用示例
Banner 广告 (Widget)

直接嵌入页面布局，需要指定固定高度。

code
Dart
download
content_copy
expand_less
SizedBox(
  height: 100, // 建议高度 100-150
  child: AdBannerWidget(
    posId: "Banner广告位ID", 
    height: 100,
  ),
)
自渲染原生广告 (Native Widget)

通常用于信息流，高度较高，支持自适应图片。

code
Dart
download
content_copy
expand_less
SizedBox(
  height: 300, // 根据 XML 布局调整，通常 250-350
  child: AdUnifiedWidget(
    posId: "原生自渲染广告位ID", 
    height: 300,
  ),
)
全屏类广告 (方法调用)
code
Dart
download
content_copy
expand_less
// 展示开屏广告
MyAdsPlugin.showSplash(posId: "开屏广告位ID");

// 展示插屏广告
MyAdsPlugin.showInterstitial(posId: "插屏广告位ID");

// 展示激励视频
MyAdsPlugin.showRewardVideo(posId: "激励视频广告位ID");
4. 常见问题 (FAQ)

Q: 编译报错 Direct local .aar file dependencies are not supported?

A: 请在新项目终端执行 flutter clean，然后 flutter pub get，最后 flutter run。这是 Gradle 缓存导致的。

Q: 运行崩溃 ClassNotFoundException: ... OctopusProvider?

A: 必须在 android/gradle.properties 中添加 android.enableJetifier=true 并执行 flutter clean。

Q: 广告显示空白?

A:

检查 Logcat 日志，过滤 AdSystem。

检查 Flutter Widget 的 height 是否足够（原生广告至少 250+）。

检查广告位 ID 类型是否匹配（不要把 Banner ID 传给 Native）。

版本历史

v1.0.0: 初始版本，支持开屏、插屏、激励、Banner、Native。
