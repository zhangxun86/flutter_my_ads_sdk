# Flutter 聚合广告插件 (My Ads Plugin)

这是一个功能强大的 Flutter 聚合广告插件，集成了 **穿山甲 (Pangle)**、**优量汇 (GDT)**、**京东 (JAD)**、**华为 (Huawei)**、**快手 (KS)** 等主流广告联盟。

## ✨ 主要功能
*   **开屏广告 (Splash)**：支持**连续展示**（如连看3次）。
*   **插屏广告 (Interstitial)**：支持**连续展示**。
*   **激励视频 (Reward Video)**：支持奖励回调。
*   **Banner 广告**：Widget 组件嵌入。

---

## 1. 引入依赖

在你的 Flutter 项目 `pubspec.yaml` 文件中，通过 Git 方式添加依赖：

```yaml
dependencies:
  flutter:
    sdk: flutter

  # 引入插件
  my_ads_plugin:
    git:
      url: https://github.com/zhangxun86/flutter_my_ads_sdk.git
      ref:  v1.0.6
```

运行安装命令：
```bash
flutter pub get
```

---

## 2. Android 环境配置 (必须)

为了确保广告 SDK 能正常编译运行，必须在**宿主 Android 项目**中进行以下配置。

### 2.1 配置 Maven 仓库
打开项目根目录下的 **`android/build.gradle`**，在 `allprojects -> repositories` 中添加：

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        
        // --- 必须添加以下广告 SDK 仓库 ---
        maven { url "https://artifact.bytedance.com/repository/pangle" } // 穿山甲
        maven { url 'https://jitpack.io' } // 京东/通用
        maven { url 'https://developer.huawei.com/repo/' } // 华为
    }
}
```

### 2.2 配置 AndroidManifest (解决合并冲突)
打开 **`android/app/src/main/AndroidManifest.xml`**，修改 `<manifest>` 和 `<application>` 标签：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="你的应用包名"
    xmlns:tools="http://schemas.android.com/tools"> <!-- 1. 添加 tools -->

    <application
        android:label="你的应用名"
        android:icon="@mipmap/ic_launcher"
        
        android:allowBackup="true"
        tools:replace="android:label,android:allowBackup"> <!-- 2. 强制替换 -->

        <!-- ... Activity ... -->
    </application>
</manifest>
```

### 2.3 开启 AndroidX 和 Jetifier
打开 **`android/gradle.properties`**，添加以下配置（防止旧版 SDK 崩溃）：

```properties
android.useAndroidX=true
android.enableJetifier=true
```

### 2.4 设置 MinSDK
打开 **`android/app/build.gradle`**，确保 `minSdkVersion` 至少为 **21**。

---

## 3. Flutter 代码集成指南

### 3.1 初始化 SDK & 监听事件
建议在 `main.dart` 的 `initState` 中进行初始化。

```dart
import 'package:my_ads_plugin/my_ads_plugin.dart';

@override
void initState() {
  super.initState();
  
  // 1. 设置全局事件监听
  MyAdsPlugin.setEventHandler((adType, event, msg) {
    print("广告回调: [$adType] -> $event ($msg)");
    
    // 监听激励视频奖励发放
    if (adType == 'reward' && event == 'onReward' && msg == 'true') {
      print(">>> 恭喜获得奖励 <<<");
      // TODO: 在这里给用户发金币
    }
  });

  // 2. 初始化 SDK
  MyAdsPlugin.initSdk(
    appId: "你的AppID", 
    appName: "你的应用名称"
  );
}
```

### 3.2 开屏广告 (支持连续展示)
调用 `showSplash` 方法。

```dart
// 单次展示
MyAdsPlugin.showSplash(posId: "开屏广告位ID");

// 连续展示 3 次 (适合开屏或应用切换时)
// 广告关闭后会自动加载下一条，直到次数用完
MyAdsPlugin.showSplash(
  posId: "开屏广告位ID", 
  count: 3
);
```

### 3.3 插屏广告 (支持连续展示)
调用 `showInterstitial` 方法。

```dart
// 单次展示
MyAdsPlugin.showInterstitial(posId: "插屏广告位ID");

// 连续展示 2 次
MyAdsPlugin.showInterstitial(
  posId: "插屏广告位ID", 
  count: 2
);
```

### 3.4 激励视频广告
调用 `showRewardVideo` 方法。

```dart
MyAdsPlugin.showRewardVideo(posId: "激励视频广告位ID");
```

### 3.5 Banner 广告 (Widget)
直接嵌入页面布局，**必须指定高度**。

```dart
SizedBox(
  height: 100, // 建议高度 100-150
  child: AdBannerWidget(
    posId: "Banner广告位ID", 
    height: 100,
  ),
)
```

### 3.6 自渲染原生广告 (Widget)
通常用于信息流，高度较高，支持自适应图片。

```dart
SizedBox(
  height: 300, // 根据 XML 布局调整，通常 250-350
  child: AdUnifiedWidget(
    posId: "原生自渲染广告位ID", 
    height: 300,
  ),
)
```

---

## 4. 常见问题排查 (FAQ)

**Q: 编译报错 `Direct local .aar file dependencies are not supported`?**
> **A:** 这是 Gradle 缓存问题。请在宿主项目终端依次执行：
> `flutter clean` -> `flutter pub get` -> `flutter run`。

**Q: 运行崩溃 `ClassNotFoundException`?**
> **A:** 检查 `android/gradle.properties` 是否配置了 `android.enableJetifier=true`。配置后必须执行 `flutter clean`。

**Q: 广告加载成功但显示空白?**
> **A:** 
> 1. Banner/Native 广告：检查 Flutter Widget 的 `height` 是否设置得太小。
> 2. 检查 Logcat 日志过滤 `AdSystem`，查看是否有错误码。
> 3. 确认广告位 ID 类型是否匹配（例如不要把 Banner ID 用在 Native 上）。

**Q: 如何更新插件代码?**
> **A:** 在宿主项目中运行 `flutter pub upgrade my_ads_plugin`。
