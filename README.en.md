# edge_download_change (Ghost Downloader fork)

> ## ⚠️ UNOFFICIAL FORK
>
> **This repository is not the upstream project and does not publish upstream builds.**
>
> - Upstream: [`lswlc33/edge_download_change`](https://github.com/lswlc33/edge_download_change) by **[雪中明月 (@lswlc33)](https://github.com/lswlc33)**
> - This fork changes **exactly one thing**: it adds **Ghost Downloader 3** as a selectable download target
> - Everything else — code, architecture, docs, analysis scripts — is upstream work, and all rights remain with the upstream author
> - **Upstream ships no LICENSE file** (no open-source license granted; all rights reserved by default).
>   This fork therefore claims no rights and can grant none. See [License & Disclaimer](#license--disclaimer).

An LSPosed module that replaces **Microsoft Edge for Android**'s (`com.microsoft.emmx`)
download confirmation dialog with its own **Copy / Download** dialog, and hands the
download over to the **system DownloadManager** (or a third-party downloader app) instead
of Edge's built-in download manager.

> LSPosed module (libxposed API 102), verified on Edge **153.0.4234.49** (by upstream).
> 中文说明: [README.md](README.md) ｜ Design notes: [lsp_module/README.md](lsp_module/README.md)
> ｜ How the hook targets were derived: [analysis/README.md](analysis/README.md)

---

## What this fork changes

One functional change only: **Ghost Downloader 3 support** (`com.xychr.ghostdownloader`).

`dumpsys package com.xychr.ghostdownloader` shows that its `MainActivity` declares only:

- `ACTION_SEND` with `text/plain` — the "share a link to Ghost Downloader" entry point
- `ACTION_VIEW` — but only for `magnet:` / `ed2k:` / `ftp:` / `ftps:` / `content:` and the
  m3u8 / dash / torrent MIME types

There is **no `VIEW` filter for http(s) URLs**. Upstream's *custom package* option always
delivers with `ACTION_VIEW`, so pointing it at Ghost Downloader 3 silently falls back to the
system downloader.

Two changes, both forced by real failures:

```java
// lsp_module/src/.../Downloaders.java - register the target (explicit component + SEND)
new Entry("ghostdl", "Ghost Downloader", "com.xychr.ghostdownloader", MODE_SEND_EXPLICIT,
        "com.xychr.ghostdownloader.MainActivity"),
```

```xml
<!-- lsp_module/AndroidManifest.xml - Android 11+ package visibility -->
<package android:name="com.xychr.ghostdownloader" />
```

| Pitfall | Symptom | Cause |
|---|---|---|
| Missing `<queries>` | picker shows "Ghost Downloader (**not installed**)" although it is installed | Android 11+ package visibility: `getPackageInfo()` throws `NameNotFoundException` for undeclared packages |
| Implicit `ACTION_SEND` + `setPackage()` | hand-off fails and **silently falls back** to the system downloader | an implicit intent is still resolved against the **caller's** visibility; the injected code runs inside Edge, which cannot see GD3 → `ActivityNotFoundException` |
| Explicit component instead | works reliably | an explicit intent addresses the component directly, so no resolution and no visibility requirement |

Verified on device: Ghost Downloader opens "New download" with the URL filled in and already
probed (`vue.global.js / 128.0 KB`, target folder, thread count).

Requires **Ghost Downloader 3** to be installed (Android 9+, arm64-v8a; see
[XiaoYouChR/Ghost-Downloader-3](https://github.com/XiaoYouChR/Ghost-Downloader-3)).

---

## Why it exists

Edge for Android is a good browser with a weak download story: downloads are locked inside
the browser, they cannot be handed to the system or to a download manager app, and large
files / resumable downloads are painful.

This module is for you if you want to:

- send web downloads to the **system downloader** (progress in the notification shade,
  managed in the system Downloads app);
- hand them to an installed **downloader app** (ADM / IDM / 1DM / FDM / Gopeed / Ghost Downloader 3 / Aria2App …);
- **see the real URL before downloading** (verify the domain, avoid misclicks, share the link);
- keep the module **controllable**: interception toggle, download-target picker, activation
  status and a log viewer;
- or study how a Chromium-based browser's download path can be taken over (see `analysis/`).

Requirements: a rooted device with a framework supporting **libxposed API 102** (e.g. LSPosed).

## What it solves

| Before | After |
|---|---|
| Downloads can only be handled by Edge itself | Tapping **Download** creates the download with the **system downloader** or a chosen **downloader app** |
| Edge's dialog shows only name and size | Our dialog shows the **full URL** and copies it with one tap |
| Cancelling may still leave Edge transferring data | Edge's download item is **cancelled the moment it is created** (before the body transfer) |
| Links that need a login cookie fail with the system downloader | **Copy** gives you a path to paste the link into a logged-in tool (known limitation) |
| No way to tell whether the module works | Status page shows **version / activation / last injection / framework version**, plus a checklist |
| Debugging by guesswork | Log page records every interception, skip (with reason) and hand-off result |

## Usage

1. **Install**:
   - grab `edge_download_change-<version>.apk` from this repository's
     [Releases](../../releases/latest);
   - or build locally: `ANDROID_SDK_ROOT=<sdk> bash lsp_module/build.sh`
     (needs JDK 17 + `build-tools;35.0.0` + `platforms;android-35`).

   > ⚠️ **Different signing key — uninstall upstream first.**
   > Upstream APKs are signed with the upstream author's key. This fork uses a different local
   > key, so installing over upstream fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
   > Uninstall the original module (this wipes the module's own settings), install this build,
   > then re-enable it in LSPosed and confirm the scope is Edge. Going back to upstream works
   > the same way in reverse.
2. **Enable** the module in your Xposed/LSPosed manager. The scope `com.microsoft.emmx` is
   declared statically by the module; if your manager ignores static scopes, check Edge manually.
3. **Restart Edge**: force-stop it (switching to background is not enough) and open it again.
4. **Verify**: the module app should show "● Active" with the last injection time and framework
   version. Otherwise follow the 4-step checklist on the status card, or search the LSPosed log
   for `EdgeSysDL`.
5. **Daily use**: tap a download link in Edge → a "Download this file?" dialog appears:
   - **Download** – creates the download with the selected target (system downloader by default);
   - **Copy** – copies the link and cancels Edge's own download;
   - dismiss the dialog – cancels everything, nothing is created.
6. **Settings**: interception on/off; download target (system downloader, ADM, IDM, 1DM, FDM,
   DVGet, Download Navi, Aria2App, Gopeed, **Ghost Downloader**, AB DM, FluxDown, Xunlei,
   or a custom package name);
   log viewer. If a third-party downloader cannot be started, the module falls back to the
   system downloader and says so in the log.

## How it works (short version)

Edge is Chromium-based: downloads run in native code and the confirmation dialog is shown by an
obfuscated class (`rge.a(fileName, size, Callback)`), which this module locates **by method
signature** rather than by its unstable name. The module hooks three things:

1. `DownloadManagerService.onDownloadItemCreated` – called right before the dialog; reads the real
   URL/name/MIME, **cancels Edge's download item immediately** (no data transferred yet) and shows
   its own Copy/Download dialog;
2. the confirmation-dialog factory `rge.a` – replaces Edge's dialog; if the dialog appears before
   the download item, it waits for the item and then applies the user's choice;
3. a cancel fallback – `removeDownload()` plus the official `DOWNLOAD_CANCEL` broadcast action
   (resolved from the dex string pool) and one retry after 1.5 s.

Tapping **Download** calls `DownloadManager.enqueue()` (or starts the chosen downloader app with an
explicit intent); tapping **Copy** writes the link to the clipboard and cancels Edge's download.
Edge's download item is cancelled the moment it is created (before any body transfer), so the file
is always fetched by the system/third-party downloader.

(Implementation note: the usual order is item-first, and the module then uses that item's URL
without touching Edge's dialog callback at all. Only in the unusual dialog-first order does it
answer the callback with "accepted" - and only after the user made a choice - cancelling the item
the instant it appears, so no data is transferred either.)

Settings and status travel over a two-way channel between the module app and the injected code
(a `ContentProvider` for live settings and status/log delivery, plus the framework's read-only
remote preferences), with a heartbeat every 60 s driving the activation indicator.

Version-specific gotchas (documented in `lsp_module/README.md`): `GURL.toString()` is not
overridden in Edge 153 (read the field `a`), restored history items must be filtered with
`j == 0 && q == 0`, and only `Activity.onResume` is hooked to keep the framework-hook footprint
minimal.

## Download & build

| Channel | Content | Notes |
|---|---|---|
| **Release** | this fork's signed APK (tag `<versionCode>-<versionName>`) | signed with a fixed local key, installs over previous builds of *this* repo |
| **Local build** | `bash lsp_module/build.sh` | needs JDK 17 + `build-tools;35.0.0` + `platforms;android-35` |

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # must contain build-tools/35.0.0 and platforms/android-35
bash lsp_module/build.sh
# artifact: lsp_module/edge_download_change-<versionName>.apk
```

> **Back up your signing key** (`lsp_module/module.keystore`, default passwords are in `build.sh`).
> Losing it means future builds can no longer be installed over the existing one.

> Upstream's Nightly / Release automation (auto version bump + mirroring to the official module
> repository) is bound to the upstream author's repo and secrets, so it is **removed here** to
> avoid accidental pushes upstream or pointless failing builds.

## Repository layout

```
edge_download_change/
├── README.md / README.en.md     # Chinese / English documentation
├── lsp_module/                  # the module project (sources, resources, build script, docs)
└── analysis/                    # how the Edge hook targets were derived (+ Via browser reference)
```

## License & Disclaimer

### Attribution

| Item | Ownership / license |
|---|---|
| All code in this repo (including this fork's one-line change) | upstream [`lswlc33/edge_download_change`](https://github.com/lswlc33/edge_download_change) by **雪中明月 (@lswlc33)** |
| Upstream license | **No LICENSE file upstream** (GitHub detects none) — all rights reserved by default. This fork **claims no rights and grants no license** |
| `lsp_module/xposed_api/` (libxposed API 102, `io.github.libxposed:api:102.0.0`) | Apache-2.0, compile-time only, not bundled into the APK |
| Edge / Chromium class names, fields and constants | Microsoft / the Chromium project and their respective owners |
| Ghost Downloader 3 (`XiaoYouChR/Ghost-Downloader-3`) | **GPL-3.0** by **XiaoYouChR**. This module contains, links and redistributes **none** of its code — it only fires an Android `Intent` at the installed app |

**Therefore**: this repository exists as a personal-use derivative patch, not as an official
branch of upstream and not as a re-license of upstream code. If you want this on your own device,
prefer upstream, or build from this repo's diff yourself; if you want the feature upstream,
send a pull request to upstream instead of relying on this fork.

**If the upstream author objects to this repository, open an issue and it will be archived or
deleted along with its release artifacts, immediately.**

### Disclaimer

1. **No warranty.** Provided "AS IS", without warranty of any kind, express or implied, including
   merchantability, fitness for a particular purpose and non-infringement. The author is not liable
   for any direct or indirect damage (device failure, data loss, account issues, lost downloads).
2. **Unofficial, unaffiliated.** No affiliation, sponsorship or endorsement by Microsoft, Edge,
   Chromium, LSPosed, Ghost Downloader 3 or their authors. Trademarks belong to their owners.
3. **High-risk environment.** This module requires **root** and **LSPosed (libxposed API 102)** and
   hooks Edge's download path. Root/Xposed setups can destabilise the system, crash apps, break
   integrity checks and affect banking/payment apps. **Use at your own risk.**
4. **Lawful use.** Only use it on content you have the right to download, and comply with local law,
   the target site's terms of service and copyright. **Do not** use it to bypass paywalls,
   redistribute pirated material or infringe copyright. You are solely responsible for your actions.
5. **No support obligation.** Issues and PRs are welcome, but no response time or fix is promised.
6. **Distribution may stop at any time** without notice.
