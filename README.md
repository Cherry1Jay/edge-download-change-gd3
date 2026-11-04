# edge_download_change（Ghost Downloader 衍生版）

> ## ⚠️ 非官方衍生版本 / UNOFFICIAL FORK
>
> **本仓库不是上游项目，也不是上游发布的 APK。**
>
> - 上游项目：[`lswlc33/edge_download_change`](https://github.com/lswlc33/edge_download_change)，作者 **[雪中明月](https://github.com/lswlc33)**
> - 本仓库相对上游**只增加了一处功能**：把 **Ghost Downloader 3** 加入可选下载目标（见 [本衍生版改了什么](#本衍生版改了什么)）
> - 其余全部代码、架构、文案、分析脚本均来自上游，版权归上游作者所有
> - **上游仓库没有 LICENSE 文件**（未声明任何开源许可，即默认保留所有权利）。因此本仓库**不主张、也无法授予任何许可**，
>   仅作为个人学习/自用目的的衍生修改而存在。详见 [许可与免责声明](#许可与免责声明)

把 **Microsoft Edge for Android**（`com.microsoft.emmx`）的下载确认弹窗，换成模块自己的
**「复制 / 下载」**对话框，并让下载交给**系统下载器**（Android DownloadManager）或你选定的
**第三方下载器**，而不是 Edge 自带的下载管理器。

> 这是一个 LSPosed 模块（libxposed API 102），上游已验证 Edge **153.0.4234.49**。
> English: [README.en.md](README.en.md) ｜ 开发/原理细节: [lsp_module/README.md](lsp_module/README.md)
> ｜ hook 目标的推导过程: [analysis/README.md](analysis/README.md)

---

## 本衍生版改了什么

只有一处功能改动：**新增 Ghost Downloader 3 作为下载目标**（`com.xychr.ghostdownloader`）。

| 项目 | 上游行为 | 本衍生版 |
|---|---|---|
| Ghost Downloader 3 | 不在下载目标列表里，只能选「自定义包名」——但它走 `ACTION_VIEW`，对 GD3 **无效** | 内置为一级选项 `Ghost Downloader`，选中即生效 |
| 交接方式 | — | **显式组件 + `ACTION_SEND` + `text/plain`**（与「分享链接到 GD3」同一条通路） |

**为什么不能用「自定义包名」凑合？** 抓 `dumpsys package` 可以看到 GD3 的 `MainActivity` 只声明了两类过滤器：

- `ACTION_SEND` / `text/plain` —— 分享纯文本（用来接链接）
- `ACTION_VIEW` —— 但只认 `magnet:` / `ed2k:` / `ftp:` / `ftps:` / `content:` 与 m3u8 / dash / torrent 的 MIME

**没有 http/https 的 `VIEW` 过滤器**。而「自定义包名」固定用 `ACTION_VIEW` 投递，所以对 GD3 必然找不到
可接收的 Activity，最终静默回退到系统下载器。

改动共三处，且三个坑都是踩出来的：

```java
// lsp_module/src/.../Downloaders.java —— 注册下载目标（显式组件 + SEND）
new Entry("ghostdl", "Ghost Downloader", "com.xychr.ghostdownloader", MODE_SEND_EXPLICIT,
        "com.xychr.ghostdownloader.MainActivity"),
```

```xml
<!-- lsp_module/AndroidManifest.xml —— Android 11+ 包可见性 -->
<package android:name="com.xychr.ghostdownloader" />
```

| 坑 | 现象 | 原因 |
|---|---|---|
| 漏 `<queries>` | 选择器显示「Ghost Downloader（**未安装**）」，但 App 明明装着 | Android 11+ 包可见性：`getPackageInfo()` 对未声明的包抛 `NameNotFoundException` |
| 用「隐式 Intent + `setPackage()`」 | 交接失败并**静默回退**系统下载器 | 隐式 Intent 仍要在**调用方**可见范围内解析；注入代码跑在 Edge 进程，Edge 没有 GD3 的可见性 → `ActivityNotFoundException` |
| 改用**显式组件** | 稳定命中 | 显式 Intent 直接指向组件，不经隐式解析，不受调用方包可见性影响 |

实际效果（真机核对）：GD3 打开「新建下载」，链接已填入并完成 PROBE，显示 `vue.global.js / 128.0 KB`、
目标目录与线程数。

### 第三个坑：设置同步（Edge 读不到你选的下载器）

| 现象 | 原因 |
|---|---|
| 模块里明明选了 Ghost Downloader，Edge 的确认弹窗却写着「将交给 **系统下载器**」，下载最终走系统 DownloadManager | 注入代码跑在 Edge 进程里，原本要去**拉**模块 App 的设置，但 Android 11+ 包可见性让它连模块的 ContentProvider / Service 都解析不到；LSPosed 的 `getRemotePreferences` 又要求模块 App 的 prefs 被重定向到 `/data/adb/lspd/prefs`，本机并没有发生 |

实测证据：在 Edge 进程里做诊断，显式组件的 `resolveService(module) = null`、`bindService` 返回 `false`，
系统日志是 `Can't resolve content provider ... caller is com.microsoft.emmx`——而同一时刻
`resolveActivity(GD3 显式组件)` 正常，所以**交接本身没问题，坏的是设置通道**。

改法：把方向反过来，模块 App **推**、Edge 收。

```xml
<!-- lsp_module/AndroidManifest.xml —— 让模块 App 能看见 Edge（发送方也要可见性） -->
<package android:name="com.microsoft.emmx" />
```

- 模块 App 每次改设置、每次打开界面（`onResume`）都发一条带 action 的广播，直达 `com.microsoft.emmx`；
- Edge 侧注册接收器，收到即应用，并写进**自己的** `shared_prefs/edge_download_change_cache.xml`；
- 冷启动直接读这份缓存，**完全不需要跨进程调用**；provider / remote prefs 保留为尽力而为的补充。

真机日志链路：

```
模块端  settings pushed: ghostdl
Edge 端 settings pushed by module app: ghostdl
落盘    /data/data/com.microsoft.emmx/shared_prefs/edge_download_change_cache.xml → downloader=ghostdl
```

> 注意：换签名重装模块后，LSPosed 的模块登记会被一并清掉。`lspctl`（LSPosed 2.2+ 开发者模式）可以在**不重启**的
> 前提下补回来：
> ```bash
> su -c '/data/adb/modules/zygisk_lsposed/lspctl module enable --user=0 io.github.lswlc33.edge_download_change'
> su -c '/data/adb/modules/zygisk_lsposed/lspctl scope add --user=0 io.github.lswlc33.edge_download_change com.microsoft.emmx'
> ```

> 使用前提：**Ghost Downloader 3 已安装**（Android 9+ / arm64-v8a，项目：[XiaoYouChR/Ghost-Downloader-3](https://github.com/XiaoYouChR/Ghost-Downloader-3)）。
> 交接后 GD3 会打开「新建任务」并自动解析链接（它自己会做 PROBE 请求补全文件名/大小），确认后开始下载。

---

## 一、使用前景

Edge 安卓版是个好浏览器，但它的**下载管理器一直比较弱**：下载能力完全绑定在浏览器内部，
不能交给系统或其他下载器，通知栏与下载列表的体验也一般，遇到大文件、断点续传、
需要多线程加速的场景就很吃力。

这个模块适合你，如果你：

- 想把网页下载**交给系统下载器**（通知栏显示进度、系统 Downloads 应用里统一管理）；
- 装了 **ADM / IDM / 1DM / FDM / Gopeed / Ghost Downloader 3 / Aria2App** 等下载器，希望网页下载直接进它们；
- 习惯**先看到真实下载链接**再决定下不下（核对域名、避免误点下载、把链接贴给别人）；
- 喜欢"**不满意就自己改**"：模块提供了拦截开关、下载目标选择、状态自检和日志；
- 或者单纯想研究"**Chromium 内核浏览器的下载链路是怎么被接管的**"（`analysis/` 里有完整方法）。

> 注意：需要设备已 root 且安装了支持 **libxposed API 102** 的框架（如 LSPosed）。
> 没有 root / 不用 Xposed 的用户，本模块无法工作。

## 二、解决了什么问题

| 原来的问题 | 现在的行为 |
|---|---|
| 下载只能由 Edge 自己完成，无法交给系统/第三方下载器 | 弹窗里点「下载」→ 由**系统下载器**或**你选的第三方下载器**创建下载任务 |
| Edge 的确认弹窗只给文件名和大小，看不到真实链接 | 我们的弹窗**显示完整 URL**，并提供「复制」一键复制 |
| 点了取消/复制，Edge 仍可能在后台跑流量 | Edge 的下载项在**创建瞬间就被取消**（早于响应体传输），不会偷偷下载 |
| 需要登录 Cookie 的链接，交给系统下载器可能失败 | 提供「复制」通路：复制链接后粘贴到已登录的工具下载（**已知限制**） |
| 装完模块不知道有没有生效 | 状态页显示**模块版本 / 是否已注入 / 最近注入时间 / 框架版本**，并给出排查清单 |
| 出问题只能猜 | 日志页记录每次拦截、跳过（含原因）与下载跳转结果，可复制导出 |

## 三、使用方法

**前置条件**：Android 8.0+；支持 libxposed API 102 的 Xposed 框架（LSPosed 等）；
Edge for Android（`com.microsoft.emmx`）。

1. **安装模块**：
   - 从本仓库 [Releases](../../releases/latest) 下载 `edge_download_change-<版本>.apk`；
   - 或本地构建：`ANDROID_SDK_ROOT=<SDK> bash lsp_module/build.sh`（产物 `edge_download_change-<版本>.apk`，
     需要 JDK 17 + Android SDK build-tools 35 + platform android-35）。

   > ⚠️ **签名不同，必须覆盖安装前先卸载上游版本。**
   > 上游的 APK 由上游作者的私钥签名，本衍生版由另一把本地密钥签名，两者 `applicationId` 相同但签名不同，
   > 直接安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。请先卸载原模块（**会清空模块自己的设置**），
   > 再安装本版本，然后在 LSPosed 管理器里重新启用本模块并确认作用域为 Edge。
   > 想回到上游版本同理：卸载本版 → 装回上游 APK。
2. **启用模块**：在 LSPosed 管理器里启用本模块。作用域 `com.microsoft.emmx` 已由模块**静态声明**，
   多数管理器会自动应用；如果你的管理器不识别静态作用域，请手动勾选 Edge。
3. **重启 Edge**：**强制停止** Edge（只切后台不算），再重新打开。
4. **验证**：打开模块 App，状态应显示「● 已激活」（含最近注入时间与框架版本）；若未激活，
   状态页有 4 步排查清单，也可以看 LSPosed 日志里的 `EdgeSysDL` 关键字。
5. **日常使用**：在 Edge 里点下载链接 → 弹出「下载此文件？」
   - **下载**：交给当前下载目标（默认系统下载器，通知栏可见进度）；
   - **复制**：复制下载链接，并取消 Edge 内置下载；
   - 点框外/返回：取消，不创建任何下载；
   - **成功时完全无打扰**：不会弹任何 Toast；只有出错或回退（下载器启动失败、链接获取失败、复制失败、
     系统下载器不可用）时才提示一句，细节都在模块日志里。
6. **按需设置**（模块 App 内）：
   - **开启拦截**：关掉后 Edge 完全恢复原生下载行为；
   - **下载目标**：系统下载器 / ADM / IDM / 1DM / FDM / DVGet / Download Navi / Aria2App /
     Gopeed / **Ghost Downloader** / AB DM / FluxDown / 迅雷，或自定义包名；第三方下载器启动失败会自动回退系统下载器；
   - **日志**：查看/复制/清空。

**常见问题**

- *状态一直"未检测到激活"*：模块没被注入。依次检查：模块是否已启用 → 作用域是否包含 Edge →
  是否**强制停止**过 Edge → 看 LSPosed 日志里有没有 `EdgeSysDL ... module loaded`。
- *点了「下载」但没有下载*：目标下载器未安装或被系统限制；此时模块会自动改用系统下载器并写日志。
- *下载体积很小/是登录页*：该链接需要登录 Cookie，请用「复制」把链接贴到已登录的工具里下载。
- *Edge 升级后失效*：Edge 的混淆字段可能变化；`analysis/README.md` 有重新确认 hook 目标的方法。

## 四、软件原理（简述）

Edge 是 Chromium 内核，**下载引擎在 native 层**，Java 侧只是桥接与界面；下载确认弹窗由
`split_chrome` 分包里的一个混淆类 `rge.a(文件名, 大小, Callback)` 弹出（模块按**方法签名**
定位它，不依赖混淆类名）。模块挂接三处：

1. **`DownloadManagerService.onDownloadItemCreated`**（native 回调，弹窗之前触发）
   —— 取出真实 URL、文件名、MIME，并**立即取消 Edge 的下载项**（此刻还没开始传输数据），
   然后弹出模块自己的「复制 / 下载」对话框；
2. **确认弹窗工厂 `rge.a`** —— 拦截 Edge 自己的弹窗；若它比下载项先出现，则先接管弹窗、
   等下载项出现后再执行用户的选择；
3. **取消兜底** —— 反射 `removeDownload()` 之外，还会向 `DownloadBroadcastManager` 发送
   从 dex 字符串中解析出的官方 `DOWNLOAD_CANCEL` 动作，并在 1.5 秒后重试一次。

用户点「下载」→ 模块调用 `DownloadManager.enqueue()`（或第三方下载器的显式 Intent）
创建下载；点「复制」→ 写入剪贴板并撤销 Edge 的下载。**Edge 的下载项在创建瞬间就被取消**
（此时尚未开始传输数据），所以最终拿到文件的永远是系统下载器/第三方下载器。

（实现细节：下载项先创建、弹窗后出现是常态，模块直接用该下载项的 URL，**完全不回调 Edge 的弹窗回调**；
仅当出现"弹窗先于下载项"的异常顺序时，模块才会在**用户已做出选择之后**回调一次"接受"以取得 URL，
并在下载项创建的瞬间取消它 —— 同样不发生实际传输。）

设置与状态是一条**双向通道**：模块 App ↔ Edge 内的注入代码，通过 `ContentProvider`
（实时下发设置、回收状态与日志）与框架的**远程偏好**（无 IPC 的读取路径）打通；
注入端每 60 秒心跳一次，状态页据此判断是否已激活。

版本适配要点（踩过的坑，详见 [lsp_module/README.md](lsp_module/README.md) 版本记录）：
`GURL` 在 Edge 153 **没有重写 `toString()`**，URL 必须读实例字段 `a`；
恢复的历史下载项 URL 为空且 `j=0`，只能用 `j==0 && q==0` 区分"新下载"；
框架 hook 只保留 `Activity.onResume`（最热路径上的 hook 已移除，降低崩溃风险）。

## 下载与构建

| 渠道 | 内容 | 说明 |
|---|---|---|
| **Release** | 本衍生版的 APK（tag = `版本号-版本名`） | 由本地构建签名后上传，签名密钥固定，可覆盖本仓库的历史版本 |
| **本地构建** | `bash lsp_module/build.sh` | 需 JDK 17 + `build-tools;35.0.0` + `platforms;android-35`，见下 |

```bash
# 1. 准备 SDK（已装 Android SDK 可跳过）
export ANDROID_SDK_ROOT=/path/to/android-sdk   # 需含 build-tools/35.0.0 与 platforms/android-35

# 2. 构建（首次会自动生成 lsp_module/module.keystore）
bash lsp_module/build.sh
# 产物：lsp_module/edge_download_change-<versionName>.apk
```

> **签名密钥务必自行备份**（`lsp_module/module.keystore`，默认口令见 `build.sh`）。
> 换一把密钥就意味着后续版本无法覆盖安装，用户必须先卸载。

> 上游仓库自带 Nightly / Release 自动化（含版本自增与官方模块仓库镜像），
> 那套流程绑定的是上游作者的仓库与 Secrets，**本衍生版已移除**，以避免误推到上游或产生无意义的失败构建。

## 项目结构

```
edge_download_change/
├── README.md / README.en.md     # 中文 / 英文说明
├── lsp_module/                  # LSPosed 模块工程（源码、资源、构建脚本、开发文档）
└── analysis/                    # Edge 下载链路的分析脚本与结论（含 Via 浏览器参照分析）
```

## 许可与免责声明

### 上游归属

| 内容 | 归属 / 许可 |
|---|---|
| 本仓库全部代码（含本衍生版的修改） | 上游 [`lswlc33/edge_download_change`](https://github.com/lswlc33/edge_download_change)，作者 **雪中明月（@lswlc33）** |
| 上游项目的许可 | **上游未提供 LICENSE 文件**（GitHub 未识别到许可协议），依著作权法默认 **保留所有权利**。本仓库**不主张任何额外授权，也不对外授予任何许可** |
| `lsp_module/xposed_api/`（libxposed API 102，`io.github.libxposed:api:102.0.0`） | Apache-2.0，仅作编译期依赖，不打包进 APK |
| Edge / Chromium 的类名、字段名、常量 | 归 Microsoft / Chromium 项目及其各自所有者 |
| Ghost Downloader 3（`XiaoYouChR/Ghost-Downloader-3`） | **GPL-3.0**，作者 **XiaoYouChR**。本模块**不包含、不链接、不分发其任何代码**，仅通过 Android `Intent` 调用已安装的 App |

**因此**：本仓库的存在形式是「个人自用的衍生修改」，不是上游的官方分支，也不构成对上游代码的再许可。
如果你需要在自己的设备上使用，**更推荐的做法是**：直接使用上游版本，或按本仓库的 diff 自行构建；
若你希望该功能进入上游，请向上游仓库提交 PR，而不是依赖本衍生版。

**若上游作者对本仓库有任何异议，请开 issue，我会立即归档或删除本仓库及其 Release 产物。**

### 免责声明（Disclaimer）

1. **无任何担保。** 本软件按「现状」（AS IS）提供，不附带任何明示或暗示的担保，包括但不限于适销性、
   特定用途适用性与不侵权担保。使用本软件造成的任何直接或间接损失（包括设备故障、数据丢失、
   账号异常、下载内容丢失等），作者不承担任何责任。
2. **非官方、无关联。** 本仓库与 Microsoft、Edge 浏览器、Chromium 项目、LSPosed 框架、
   Ghost Downloader 3 及其作者均**无任何隶属、赞助或背书关系**，相关商标归各自所有者。
3. **高风险环境。** 本模块需要 **root** 与 **LSPosed（libxposed API 102）**，并通过 Hook 修改
   Edge 的下载链路。root / Xposed 环境可能导致系统不稳定、应用崩溃、无法通过完整性校验、
   影响银行/支付类应用等，**风险自负**。
4. **合规使用。** 请仅在**你有权下载**的内容上使用本模块，并遵守所在地区法律法规、
   目标网站的服务条款与版权规定。**严禁**将本模块用于绕过付费墙、盗版分发、
   侵犯他人著作权或其他违法用途。因使用者的行为产生的一切法律责任由使用者自行承担。
5. **无技术支持义务。** Issue / PR 欢迎，但不承诺响应时间与修复。
6. **可随时停止分发。** 作者可随时修改、停止分发或删除本仓库及其产物，无需事先通知。
