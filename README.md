# 谷歌区域伪装（GoogleRegionSpoof）

一个 LSPosed 模块，把**谷歌应用读到的客户端区域信号**伪装成选定的目标地区：
SIM/多卡/网络国家码、运营商（MCC+MNC 与名称）、语言列表（LocaleList）、城市联动时区、Resources 配置的 locale、
`gsm.*` 与 `persist.sys.timezone` 系统属性、GMS 环境信号净化（屏蔽周边 Wi-Fi/基站扫描），以及可选的 GPS 坐标。

> **边界先说清**：本模块只改应用在本地读到的区域信号，**不改变 IP 出口**。
> 很多谷歌服务是「IP + 客户端信号」一起看的，请配合代理（IP 出口与所选区域及城市时区一致）使用，
> 否则信号互相矛盾，效果会打折。

## v1.4 更新

- **解除硬编码时区，支持城市联动与跟随系统**：
  - 城市数据新增对应 IANA 时区（美国覆盖美东纽约、美西洛杉矶/旧金山、美中芝加哥、美山丹佛），切换城市即联动更新时区与坐标；
  - 新增「接管应用时区」独立开关（默认开启，关闭后直接读取手机系统时区），同步拦截 `TimeZone.getDefault` / `getDefaultRef` 及 `SystemProperties.get("persist.sys.timezone")`。
- **GMS 环境信号净化（替代高风险虚假 GPS）**：
  - 新增「环境信号净化（屏蔽 Wi-Fi / 基站扫描）」开关（默认开启），仅对 `com.google.android.gms` 生效，拦截周边 Wi-Fi 扫描、当前路由器 BSSID 及同步/异步基站扫描，避免物理网络环境与代理 IP 冲突；
  - GPS 伪装保持默认关闭，并在界面醒目标注高风险提示。
- **补全 Android 10+ 多卡管理 API**：
  - 同步 Hook `SubscriptionInfo`（`getCountryIso`、`getMccString`、`getMncString`、`getMcc`、`getMnc`、`getCarrierName`、`getDisplayName`），避免双卡设备上多卡接口泄露真实运营商与国家码。
- **精细化 `LocaleList` 语言回退 + 语言独立开关**：
  - 构造 `[目标语言, 原系统语言...]`（如 `[en-US, zh-CN]`）注入 `LocaleList`、`Configuration.getLocales()` 与 `Resources.getConfiguration()`，兼顾地区检测与中文回退；
  - 对 Google 应用（`com.google.android.googlequicksearchbox`）与 Gemini（`com.google.android.apps.bard`）自动跳过语言/Configuration Hook，避免 Gemini 主页与悬浮窗因内部语言校验触发无限重建闪动（其 SIM/多卡/运营商/时区伪装仍正常生效）；
  - 新增「接管应用语言（Locale）」独立开关（默认开启），方便在部分对语言变化敏感的应用中一键关闭排查。
- **构建与设置优化**：
  - 关于页版本号改为动态读取；`build.sh` 支持环境变量传入签名参数并新增 GitHub Actions 自动构建。

## 包名（v1.2 起变更，升级必看）

- 当前包名：`io.github.zealler9560.googleregion`
- 本模块将上架 **LSPosed 官方仓库**，官方仓库中的仓库名即新包名。v1.2 为满足官方仓库
  对包名的要求（使用 `io.github.<GitHub 用户名>` 前缀，或自有域名的倒置域名）更换了包名。
- **从旧版升级**：旧版包名为 `io.zealler.googleregion`。包名变化后新版是一个全新应用，
  **无法覆盖安装旧版**。请先**卸载旧版**，再安装新版，然后在 LSPosed 中**重新启用模块、
  重新勾选作用域**；旧版里选好的国家/运营商等配置**不会迁移**，请在新版里重新选择一次。

## v1.3 更新

- **推荐作用域补全**：从 5 个扩到 26 个谷歌应用（见下方「推荐作用域」）。
- **修复手动补勾不生效**：v1.2 及以前，模块内部（`HookEntry.isTarget`）写死了与推荐名单
  相同的 5 个包名，用户在 LSPosed 里手动补勾的其他谷歌应用会被直接放行返回、并不伪装。
  v1.3 改为谷歌系包名一律放行，以 LSPosed 作用域勾选为总闸，补勾哪个生效哪个。
- v1.2 的包名不变，覆盖安装即可，配置不丢。

## 界面概览（仿 GlobalKit 的卡片式交互）

- **主页**（整页可滚动，卡片式）：
  - 顶部状态卡：模块名 + 当前状态；
  - **当前地区卡**：国旗 + 国家名，点「更改」打开**可搜索**的国家选择列表（14 个国家）；
  - **运营商卡片（二级联动）**：横向卡片列出所选国家的 2–3 家运营商（名称 + MCCMNC），
    点选即生效并同步覆盖多卡接口；**切换国家时运营商自动重置为该国第一家**；
  - **城市 / 时区与语言卡片**：点击选择同国主要城市（联动 IANA 时区与坐标），并提供「接管应用时区」「接管应用语言（Locale）」两个独立开关；
  - **环境与定位防泄漏卡片**：提供「环境信号净化（屏蔽 Wi-Fi / 基站扫描）」（默认开，仅 GMS 生效）与「GPS 伪装（高风险）」（默认关）；
  - **当前伪装值**可展开面板：SIM/网络国家码、运营商与 MCC/MNC、多卡接口状态、语言模式、时区、环境净化、GPS 状态一览；
  - 底部说明文字与「设置」入口。
- **设置页**：外观三档（跟随系统 / 浅色 / 深色）+ 主题颜色（橙/蓝/绿/紫/红，默认橙），
  选完立即作用于模块自己的界面，重启保持。

## 国家与运营商（14 国，每国 2–3 家真实运营商）

| 国家 | 运营商（MCC+MNC） | 语言 | 城市与时区（支持切换） |
|---|---|---|---|
| 美国 us | T-Mobile 310260 / AT&T 310410 / Verizon 311480 | en-US | 纽约 `America/New_York` / 洛杉矶 & 旧金山 `America/Los_Angeles` / 芝加哥 `America/Chicago` / 丹佛 `America/Denver` |
| 日本 jp | NTT docomo 44010 / SoftBank 44020 / au (KDDI) 44007 | ja-JP | 东京 / 大阪 `Asia/Tokyo` |
| 新加坡 sg | Singtel 52501 / StarHub 52505 / M1 52503 | en-SG | 新加坡 `Asia/Singapore` |
| 香港 hk | CMHK 中国移动香港 45400 / CSL 45419 / SmarTone 45406 | zh-HK | 香港 `Asia/Hong_Kong` |
| 台湾 tw | 中华电信 46692 / 台湾大哥大 46697 / 远传电信 46601 | zh-TW | 台北 / 高雄 / 台中 `Asia/Taipei` |
| 英国 gb | EE 23430 / O2 23410 / Vodafone 23415 | en-GB | 伦敦 / 曼彻斯特 `Europe/London` |
| 德国 de | Telekom.de 26201 / O2 26207 / Vodafone 26202 | de-DE | 柏林 / 法兰克福 / 慕尼黑 `Europe/Berlin` |
| 韩国 kr | SK Telecom 45005 / KT 45008 / LG U+ 45006 | ko-KR | 首尔 / 釜山 `Asia/Seoul` |
| 泰国 th | AIS 52001 / TrueMove H 52004 / dtac 52005 | th-TH | 曼谷 / 清迈 `Asia/Bangkok` |
| 澳大利亚 au | Telstra 50501 / Optus 50502 / Vodafone 50503 | en-AU | 悉尼 `Australia/Sydney` / 墨尔本 `Australia/Melbourne` |
| 马来西亚 my | Maxis 50212 / Celcom 50219 / Digi 50216 | ms-MY | 吉隆坡 `Asia/Kuala_Lumpur` |
| 菲律宾 ph | Globe 51502 / Smart 51503 | en-PH | 马尼拉 `Asia/Manila` |
| 越南 vn | Viettel 45204 / Vinaphone 45202 / Mobifone 45201 | vi-VN | 胡志明市 / 河内 `Asia/Ho_Chi_Minh` |
| 巴西 br | Vivo 72410 / Claro 72405 / TIM 72402 | pt-BR | 圣保罗 / 里约热内卢 `America/Sao_Paulo` |

默认：美国 / T-Mobile / 纽约 / 时区接管开 / 语言接管开 / 环境净化开 / GPS 关。配置分开存储，旧版配置升级时自动补全新增键。

## 实际 hook 的方法

每个 hook 独立 try/catch，失败静默放行原结果，不会让目标应用崩溃：

- **SIM / 运营商（TelephonyManager）**：`getSimCountryIso`、`getNetworkCountryIso`、`getSimOperator`、`getNetworkOperator`、`getSimOperatorName`、`getNetworkOperatorName`（均含带 `int` 参数的隐藏重载）
- **多卡接口（SubscriptionInfo）**：`getCountryIso`、`getMccString`、`getMncString`、`getMcc`、`getMnc`、`getCarrierName`、`getDisplayName`
- **语言（仅「接管应用语言」开启时）**：
  - `Locale.getDefault()`（含 `Category` 重载）
  - `LocaleList.getDefault()` / `getAdjustedDefault()`（首位为目标语言，次位保留原系统语言回退）
  - `Configuration.getLocales()`（仅替换非空语言列表，不干扰空 override 配置）
  - `Resources.getConfiguration()`（返回改过 `LocaleList` 的配置副本，不动应用原对象）
- **时区（仅「接管应用时区」开启时）**：`TimeZone.getDefault()`、`TimeZone.getDefaultRef()`，以及 `SystemProperties.get("persist.sys.timezone")`
- **系统属性（`SystemProperties.get`）**：拦截 `gsm.sim.operator.iso-country`、`gsm.operator.iso-country`、`gsm.sim.operator.numeric`、`gsm.operator.numeric`、`gsm.sim.operator.alpha`、`gsm.operator.alpha`（以及上述 `persist.sys.timezone`）
- **环境信号净化（仅「环境信号净化」开启且宿主为 `com.google.android.gms` 时）**：
  - `WifiManager.getScanResults()` 返回空列表
  - `WifiInfo.getBSSID()` 返回 `"02:00:00:00:00:00"`
  - `TelephonyManager.getAllCellInfo()` 返回空列表、`getCellLocation()` 返回 `null`
  - `TelephonyManager.requestCellInfoUpdate(Executor, CellInfoCallback)` 通过回调返回空列表
- **GPS（高风险，仅开关打开时才装钩）**：`Location.getLatitude / getLongitude` 返回所选城市坐标
  （按「包名+城市」哈希加一个稳定小偏移，约 ±130 米）；`LocationManager.getLastKnownLocation` 返回按假坐标构造的 Location。

配置读取失败（文件不可读等）时自动回退默认配置，不会崩溃。

## 推荐作用域（共 26 个）

- `com.google.android.gms`（Google Play 服务）
- `com.android.vending`（Play 商店）
- `com.google.android.googlequicksearchbox`（Google 应用）
- `com.google.android.apps.bard`（Gemini）
- `com.google.android.youtube`（YouTube）
- `com.google.android.gsf`（Google 服务框架）
- `com.google.android.gm`（Gmail）
- `com.google.android.apps.docs`（云端硬盘）
- `com.google.android.apps.translate`（翻译）
- `com.google.android.apps.magazines`（Google 新闻）
- `com.google.android.apps.walletnfcrel`（Google 钱包）
- `com.google.android.apps.subscriptions.red`（Google One）
- `com.google.android.apps.cloudconsole`（Google Cloud 控制台）
- `com.google.android.apps.adm`（查找中心）
- `com.google.android.apps.authenticator2`（身份验证器）
- `com.google.ar.lens`（智能镜头）
- `com.google.android.inputmethod.latin`（Gboard）
- `com.google.android.apps.labs.language.tailwind`（NotebookLM）
- `com.google.android.apps.privacy.wildlife`（VPN by Google）
- `com.google.android.wearable.app.cn`（Wear OS）
- `com.google.android.apps.youtube.music`（YouTube Music）
- `com.google.android.apps.maps`（地图）
- `com.google.android.apps.photos`（相册）
- `com.android.chrome`（Chrome）
- `com.google.android.play.games`（Play 游戏）
- `com.google.android.videos`（Google TV）

名单之外也行：v1.3 起模块内部不再写死包名，以 LSPosed 里的勾选为总闸——
任何谷歌系应用（`com.google.*` 包名，以及 Play 商店、Chrome）手动补勾即生效。

## 使用方法

1. 安装 APK，在 LSPosed 中启用本模块，确认作用域勾选了上面的谷歌应用；
2. 打开「谷歌区域伪装」，选好国家、运营商，再在「城市 / 时区」中选择与你代理 IP 所在时区匹配的城市；
3. 推荐保持「环境信号净化」开启、「GPS 伪装」关闭；
4. **强停**对应的谷歌应用后重新打开才生效；**Play 商店还需到系统设置里清一次它的缓存**，
   否则商店首页地区可能不变；
5. IP 出口请用代理切到同一地区，与客户端信号保持一致。

## 已知限制（实事求是）

- **IP 不归本模块管**：只伪装客户端信号，IP 仍是真实出口，务必配合代理。
- **Play 商店换区不只看客户端信号**：还受谷歌账号的国家/地区设置与支付资料（付款方式所属国家）
  影响；账号地区一年只能改一次这类平台规则，模块改不了。
- 部分谷歌服务会结合账号历史、设备型号等多维信号判断地区，本模块覆盖的是最常见的
  SIM/多卡/运营商/语言/时区/环境扫描这一组，不能保证所有区域功能都解锁。
- 切换配置后需强停目标应用（配置在目标进程启动时读取），做不到热切换。
- GPS 伪装只覆盖 `Location` 读数与 `getLastKnownLocation`；走网络定位/Fused 内部通道的
  读取不一定经过这两个入口，效果因应用而异。
- 配置文件依赖 LSPosed 的跨进程读取（XSharedPreferences）；个别 ROM 上若读不到配置，
  会回退成默认美国，而不是报错。

## 构建

```sh
# 方式一：本地手动工具链（需配置 ANDROID_HOME、JDK 17+，可选 KS / KS_PASS / KEY_PASS）
./build.sh

# 方式二：GitHub Actions 自动构建（push 或手动触发 Build APK workflow，直接下载 Artifact）
```

- 框架：经典 Xposed API 82（`libs/api-82.jar` 为官方 API 存根，仅编译期使用，不打包进 APK）；
- 最低 Android 8.0（API 26）；当前版本 v1.4（versionCode 6）。

## 借鉴与致谢

本项目的实现思路学习自以下两个开源项目（仅学习思路，本仓库代码为独立编写）：

- [Ackites/Nrfr](https://github.com/Ackites/Nrfr)：免 Root 的 SIM 国家码修改工具，
  用 Shizuku 调用系统 `ICarrierConfigLoader` 覆写运营商配置。本项目借鉴了它的
  「国家 → 运营商」预设组织方式。详见 [docs/学习笔记.md](docs/学习笔记.md)。
- [GlobalKit（com.tiktok.unlocker）](https://github.com/Xposed-Modules-Repo/com.tiktok.unlocker)：
  面向 TikTok 的 LSPosed 区域伪装模块（基于 libxposed Modern API 102）。
  本项目仿照它的信号面（SIM/网络国家、运营商、语言、时区、GPS）与卡片式设置界面
  （国家/运营商二级联动、可搜索国家列表、外观与主题色设置），目标应用换成谷歌全家桶，
  框架层采用等价的经典 Xposed API 82 + XSharedPreferences。

## 免责声明

仅供学习、研究与个人测试使用。因使用本模块导致的应用异常、账号限制或其他损失，由使用者自行承担；
使用时请遵守所在地法律法规及相关服务条款。
