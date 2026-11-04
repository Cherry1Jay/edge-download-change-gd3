# 2.12-gd3.2 — 加入 Ghost Downloader 3 下载目标

> ## ⚠️ 非官方衍生版 / UNOFFICIAL FORK
>
> 本 APK **不是**上游项目 [lswlc33/edge_download_change](https://github.com/lswlc33/edge_download_change) 发布的，
> 也未获上游作者 **雪中明月（@lswlc33）** 认可或背书。它是基于上游 v2.12 源码的一处个人修改，
> **仅供个人学习与自用**。请优先使用上游版本；如需此功能，建议向上游提交 PR。
>
> 上游仓库**没有 LICENSE 文件**（未声明开源许可 = 默认保留所有权利）。本衍生版**不主张、也不授予任何许可**。
> 若上游作者对本项目有异议，请开 issue，会立即归档并删除全部发布产物。

## 本版改了什么

只加了一处功能：**把 Ghost Downloader 3（`com.xychr.ghostdownloader`）加入下载目标列表**。

| 项目 | 上游 v2.12 | 本衍生版 |
|---|---|---|
| GD3 在下载器列表 | 无，只能选「自定义包名」 | 内置一级选项 **Ghost Downloader** |
| 交接方式 | 自定义包名走 `ACTION_VIEW`（对 GD3 无效） | **显式组件 + `ACTION_SEND` + `text/plain`** |
| `<queries>` 声明 | 无 GD3 | 已加 `com.xychr.ghostdownloader` |
| 交接失败时 | 静默回退系统下载器 | 保持原回退逻辑，但正常路径已不再失败 |

### 为什么「自定义包名」对 GD3 不管用

`dumpsys package com.xychr.ghostdownloader` 显示 `MainActivity` 只声明了：

- `ACTION_SEND` / `text/plain` —— 接收分享的纯文本（链接）
- `ACTION_VIEW` —— 但只认 `magnet:` / `ed2k:` / `ftp:` / `ftps:` / `content:` 与 m3u8 / dash / torrent 的 MIME

**没有 http/https 的 `VIEW` 过滤器**，所以 `ACTION_VIEW` 投递必然找不到 Activity，静默回退系统下载器。

### 两个踩出来的坑

| 坑 | 现象 | 原因 | 修法 |
|---|---|---|---|
| 漏 `<queries>` | 列表里显示「Ghost Downloader（**未安装**）」，但 App 确实装着 | Android 11+ 包可见性：对未声明包 `getPackageInfo()` 抛 `NameNotFoundException` | 在 Manifest `<queries>` 里声明 GD3 |
| 隐式 Intent + `setPackage()` | 交接失败并**静默回退**系统下载器 | 隐式 Intent 要在**调用方**可见范围内解析；注入代码跑在 Edge 进程，Edge 看不到 GD3 → `ActivityNotFoundException` | 改**显式组件** Intent，不经隐式解析 |

## 验证

真机（小米 17 Pro / HyperOS，KernelSU + LSPosed）实测：

- 模块下载目标选择器显示 `✓ Ghost Downloader（已安装）`
- 选定后从 Edge 触发下载 → GD3 打开「新建下载」，链接已填入并完成 PROBE
- 抓到的界面：`vue.global.js / 128.0 KB`、目标目录、线程数均正确

## 安装

1. 需要 **root + LSPosed**（libxposed API 102）；作用域选 **Microsoft Edge**（`com.microsoft.emmx`）。
2. 下载本页 APK 安装。
   > ⚠️ **本 APK 与上游 APK 签名不同，无法覆盖安装。** 若已装上游版，需先卸载（**这会清掉 LSPosed 里该模块的启用状态与作用域**）。
3. **装完/覆盖安装后必须重启一次手机**，让 LSPosed 重新扫描到模块，然后在 LSPosed 里勾选启用并确认作用域。
   （HyperOS 上 LSPosed 2.2.1 只在开机时扫描模块，运行期 `adb install`、管理器内重装、`PACKAGE_ADDED` 广播都触发不了重扫。）
4. 打开 Edge → 触发一次下载 → 在弹窗里选 **Ghost Downloader**。

## 构建信息

| 项 | 值 |
|---|---|
| versionCode / versionName | `17` / `2.12-gd3.2` |
| 基于上游 | `ea68595` `chore(release): v2.12` |
| minSdk / targetSdk | 26 / 35 |
| APK SHA-256 | `219ce1093daee69a6f9309b4f7750bce2ef2f394a9434da575d8473f6498e600` |
| 签名 | 上游仓库自带的 `lsp_module/module.keystore`（与上游 APK 不同） |

自行构建：见 [README](../README.md#下载与构建)，需要 JDK 17 + `build-tools;35.0.0` + `platforms;android-35`。

## 免责声明

1. **无任何担保。** 按「现状」提供，不附带任何明示或默示担保，包括适销性、特定用途适用性与非侵权。
   作者不对任何直接或间接损失（设备故障、数据丢失、账号异常、下载中断）负责。
2. **非官方、无关联。** 与 Microsoft / Edge / Chromium / LSPosed / Ghost Downloader 3 及其作者无任何隶属、
   赞助或背书关系；相关商标归各自所有者。
3. **高风险环境。** 本模块需要 root 与 LSPosed，并 hook Edge 的下载链路。root/Xposed 环境可能造成系统不稳定、
   应用崩溃、完整性校验失败，并影响银行/支付类应用。**风险自负。**
4. **合法使用。** 仅用于下载你有权下载的内容，并遵守当地法律、目标站点服务条款与著作权规定。
   **不得**用于绕过付费墙、传播盗版或侵犯版权，一切后果由使用者自行承担。
5. **无支持义务。** 欢迎 issue / PR，但不承诺响应时间与修复。
6. **可能随时停止分发**，不另行通知。

## 上游归属

| 项目 | 归属 / 许可 |
|---|---|
| 本仓库全部代码（含本衍生版的改动） | 上游 [lswlc33/edge_download_change](https://github.com/lswlc33/edge_download_change)，作者 **雪中明月（@lswlc33）** |
| 上游许可 | **无 LICENSE 文件** — 默认保留所有权利。本衍生版不主张、不授予任何许可 |
| `lsp_module/xposed_api/`（libxposed API 102） | Apache-2.0，仅编译期依赖，不打包进 APK |
| Edge / Chromium 类名与字段 | Microsoft / Chromium 项目及各自所有者 |
| Ghost Downloader 3 | **GPL-3.0**，作者 **XiaoYouChR**。本模块**不包含、不链接、不转发**其任何代码，只向已安装的 App 发一个 Android `Intent` |
