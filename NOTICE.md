# NOTICE / 声明

本仓库是 **非官方衍生版本（unofficial fork）**，不是上游项目，也不发布上游的 APK。

This repository is an **unofficial fork**. It is not the upstream project and does not publish upstream builds.

---

## 1. 上游归属 / Upstream attribution

| | |
|---|---|
| 项目 / Project | `edge_download_change` — 把 Edge for Android 的下载交给系统下载器或第三方下载器 |
| 上游 / Upstream | <https://github.com/lswlc33/edge_download_change> |
| 作者 / Author | **雪中明月**（GitHub: [@lswlc33](https://github.com/lswlc33)） |
| 本仓库 / This fork | 上游代码 + **一处功能改动**（新增 Ghost Downloader 3 下载目标） |

本仓库中的全部代码（包括本衍生版的修改）著作权归上游作者所有。
本衍生版仅作为个人学习与自用目的的修改而存在，**不主张任何额外权利**。

All code in this repository — including the modification made here — is the work of the upstream
author. This fork exists solely as a personal-use derivative patch and claims no additional rights.

## 2. 许可状态 / Licensing status

- **上游未提供 `LICENSE` 文件**，GitHub 也未识别到任何许可协议。依著作权法，未声明许可即
  **默认保留所有权利（all rights reserved）**，不构成对任何人的开源授权。
- 因此本衍生版**不主张、也无法授予任何许可**。本仓库的存在不代表上游作者认可或授权本衍生版。
- Upstream ships **no `LICENSE` file**; GitHub detects no license. With no license granted, all rights
  are reserved by default. This fork therefore claims no rights and **grants none**.

第三方组件 / Third-party components：

| 组件 | 许可 | 说明 |
|---|---|---|
| `lsp_module/xposed_api/`（libxposed API 102，`io.github.libxposed:api:102.0.0`） | Apache-2.0 | 仅编译期依赖，不打包进 APK |
| Edge / Chromium 的类名、字段名、常量 | 归 Microsoft / Chromium 项目 | 仅为兼容性引用（Hook 目标） |
| Ghost Downloader 3（[XiaoYouChR/Ghost-Downloader-3](https://github.com/XiaoYouChR/Ghost-Downloader-3)） | **GPL-3.0** | 本模块**不包含、不链接、不分发**其任何代码，仅通过 Android `Intent` 调用用户已安装的 App，因此不产生 GPL 传染 |

> 若上游作者对本仓库有任何异议，请在 Issues 中提出，本仓库及其 Release 产物将**立即归档或删除**。
> If the upstream author objects, open an issue — this repository and its release artifacts will be
> archived or removed immediately.

## 3. 免责声明 / Disclaimer

1. **无担保 / No warranty.** 本软件按「现状」（AS IS）提供，不附带任何明示或暗示担保，包括但不限于
   适销性、特定用途适用性与不侵权担保。使用本软件导致的任何直接或间接损失（设备故障、数据丢失、
   账号异常、下载中断等），作者不承担责任。
2. **非官方、无关联 / Unofficial & unaffiliated.** 本仓库与 Microsoft、Edge 浏览器、Chromium 项目、
   LSPosed 框架、Ghost Downloader 3 及其作者**无任何隶属、赞助或背书关系**；相关名称与商标归各自所有者。
3. **高风险 / High risk.** 本模块需要 **root** 与 **LSPosed（libxposed API 102）**，并通过 Hook 修改
   Edge 的下载链路。root / Xposed 环境可能导致系统不稳定、应用崩溃、完整性校验失败、
   银行与支付类应用异常等，**风险自负**。
4. **合规使用 / Lawful use.** 请仅对**你有权下载**的内容使用本模块，并遵守所在地区法律法规、
   目标网站服务条款与版权规定。**严禁**用于绕过付费墙、盗版分发、侵犯著作权或其他违法用途；
   因使用者行为产生的一切法律责任由使用者自行承担。
5. **无技术支持义务 / No support obligation.** 欢迎 Issue / PR，但不承诺响应时间与修复。
6. **可随时停止分发 / Distribution may stop.** 作者可随时修改、停止分发或删除本仓库及其产物，无需事先通知。

---

完整说明见 [README.md](README.md#许可与免责声明) 与 [README.en.md](README.en.md#license--disclaimer)。
