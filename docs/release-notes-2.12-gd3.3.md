## 这是什么

**非官方衍生版**。在上游 [`lswlc33/edge_download_change`](https://github.com/lswlc33/edge_download_change) 的基础上，
新增 **Ghost Downloader 3**（`com.xychr.ghostdownloader`）作为 Edge for Android 的下载接管目标。

- 版本：`2.12-gd3.3`（versionCode 18），基于上游 v2.12
- 适用：Edge for Android（真机验证 **156.0.4314.43**）+ LSPosed（libxposed API 102）

## 改了什么

| # | 改动 | 解决的问题 |
|---|---|---|
| 1 | 下载目标列表内置 `Ghost Downloader` | 上游只能选「自定义包名」，而自定义包名走 `ACTION_VIEW`；GD3 没有 http(s) 的 VIEW 过滤器，必然回退系统下载器 |
| 2 | 交接改为**显式组件 + `ACTION_SEND` + `text/plain`** | 与系统「分享链接到 GD3」同一条通路，一次命中；显式 Intent 不受调用方包可见性限制 |
| 3 | `<queries>` 补上 `com.xychr.ghostdownloader` | 否则模块 UI 把已安装的 GD3 显示成「未安装」（Android 11+ 包可见性） |
| 4 | 设置同步改为**模块 App 推送 + Edge 本地缓存** | 原先注入代码去「拉」设置，被包可见性挡死，弹窗永远显示「系统下载器」 |
| 5 | `<queries>` 补上 `com.microsoft.emmx` | 让模块 App 能把设置广播直达 Edge（发送方也需要可见性） |

### 第 4 条值得单独说明

注入代码跑在 Edge 进程里。Android 11+ 的包可见性让它连模块的 ContentProvider / Service 都解析不到
（显式 `resolveService` 返回 `null`、`bindService` 返回 `false`，系统日志是
`Can't resolve content provider ... caller is com.microsoft.emmx`），所以「拉设置」这条路在本机是死的。

改成反向推送后：模块 App 每次改设置/打开界面 → 广播到 `com.microsoft.emmx` → Edge 收到即应用，
并写进自己的 `shared_prefs/edge_download_change_cache.xml`；冷启动直接读缓存，**零跨进程调用**。

## 真机验证

```
Edge 弹窗： 「下载」将交给 Ghost Downloader
点「下载」 → GD3「新建下载」自动填入链接
解析结果： vue-3.5.13.tgz / 615.0 KB / 8 线程 / /storage/emulated/0/Download/ghostdl
```

## 安装

1. 已装上游版的话，**两者签名不同，不能覆盖安装**，需先卸载（本版签名 SHA-256 见下）；
2. 安装本 APK → 在 LSPosed 里启用模块、作用域勾选 **Microsoft Edge**；
3. 打开模块 App，下载目标选 **Ghost Downloader**；
4. 强停 Edge 再打开，网页里点下载即可。

> 若重装后 LSPosed 列表里没有本模块，LSPosed 2.2+ 可免重启补登记：
> ```bash
> su -c '/data/adb/modules/zygisk_lsposed/lspctl module enable --user=0 io.github.lswlc33.edge_download_change'
> su -c '/data/adb/modules/zygisk_lsposed/lspctl scope add --user=0 io.github.lswlc33.edge_download_change com.microsoft.emmx'
> ```

**APK 签名指纹（SHA-256）**：`51:58:B1:5F:0B:70:02:14:A4:C1:90:96:91:0F:54:59:5F:8B:5B:C9:18:D4:76:A1:4E:5F:AB:2A:5B:8E:F2:3D`

## 归属与免责

- 本仓库全部代码（含本版的改动）著作权归上游作者 **雪中明月（@lswlc33）**；
- 上游**没有 LICENSE 文件**（GitHub 检测为无许可证）→ 默认保留所有权利。本衍生版**不主张任何权利、不授予任何许可**，
  仅作个人设备上的自用补丁存在；
- Ghost Downloader 3（[XiaoYouChR/Ghost-Downloader-3](https://github.com/XiaoYouChR/Ghost-Downloader-3)）为 **GPL-3.0**，
  本模块**不包含、不链接、不再分发**它的任何代码，只向已安装的 App 发一个 Android `Intent`；
- `lsp_module/xposed_api/` 来自 libxposed API 102（Apache-2.0），仅作编译期依赖，不打包进 APK。

**免责声明**

1. **无任何担保**。按「现状」提供，不附带明示或默示担保。作者不对任何直接/间接损失负责（设备故障、数据丢失、账号问题、下载失败等）。
2. **非官方、无关联**。与 Microsoft / Edge / Chromium / LSPosed / Ghost Downloader 3 及其作者均无隶属、赞助或背书关系，商标归各自所有者。
3. **高风险环境**。本模块需要 **root + LSPosed**，并 hook Edge 的下载链路；root/Xposed 环境可能导致系统不稳定、
   应用崩溃、完整性校验失败，并影响银行/支付类应用。**风险自负**。
4. **合法使用**。仅对你有权下载的内容使用，并遵守当地法律、目标站点条款与著作权。**不得**用于绕过付费墙、
   传播盗版或侵犯版权。你的行为由你自己负责。
5. **无支持义务**。欢迎提 Issue/PR，但不承诺响应时间或修复。
6. **随时可能停止分发**，不再另行通知。

**若上游作者对本仓库有异议，请开 Issue，我会立即归档或连同发布产物一并删除。**
