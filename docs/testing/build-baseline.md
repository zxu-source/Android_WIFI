# 当前安卓工程构建基线

日期：2026-10-09。验证对象为实施后的完整安卓首版，不把它当成未经修改的原模板构建记录。

环境：Windows 11 x86_64；Android Studio 自带 JDK 25.0.3；Gradle Wrapper 9.6.0；daemon toolchain 25；AGP 9.4.1；Kotlin 插件 2.2.10；compile/target 37；min23；SDK Build Tools36.0.0。使用已有 SDK 和 JDK，未修改系统级 JAVA_HOME。

命令行原本无 JAVA_HOME/Java PATH，本次仅在构建进程设置 JAVA_HOME 为本机 Android Studio 的 jbr。其他机器应使用其实际 JDK 路径或 Android Studio 配置，不提交个人机器路径配置。

实际成功的任务：

```powershell
gradlew.bat --version
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

跨实现测试另启动局域网模拟服务的本地 loopback 实例，并设置 `WIFI_SIMULATOR_TEST_PORT=18080`。未设置此环境变量时该项跳过，不能据此宣称跨实现检查通过。

完整结果和未验证范围见[安卓首版验证记录](android-first-release.md)。
