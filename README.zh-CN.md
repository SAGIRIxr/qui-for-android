# qui for Android

[English](README.md) · **简体中文**

[qui](https://github.com/autobrr/qui) 的原生 Android 客户端。qui 是 autobrr 的单文件
qBittorrent Web UI;本应用直接对接你自己的 qui 服务器的 REST 与 SSE 接口——和网页端用的是
同一套——用 Compose 重建了 qui 的移动端体验。

qui 没有官方 iOS 应用,手机上是以 PWA 形式安装的。本项目就是把那套移动端体验在 Android 上
原生重做一遍。

> 非官方项目,与 autobrr 及 qui 维护者无关。

## 功能

- **多客户端** —— 在 qui 管理的每个 qBittorrent 实例之间切换,或者合并成 qui 的**统一视图**,
  此时每一行都会标出它来自哪个客户端。
- **实时种子列表**,走 qui 的多路复用 SSE 流(`/api/stream`);流断开时回退到 REST 轮询。
- **三种列表密度** —— 常规、紧凑、极简,对应 qui 的移动端视图模式,可直接在列表页切换。
- **搜索、排序、筛选全部在服务端完成** —— 34 个排序字段和完整的包含/排除筛选集(状态、分类、
  标签、Tracker)都交给 qui 处理,所以结果和网页端完全一致。搜索会保留本地历史,并附有 qui 的
  通配符与模糊匹配说明。
- **种子操作** —— 开始、暂停、重新校验、重新汇报、队列优先级、分类、标签、速度限制、分享限制
  (分享率 / 做种时间 / 无活动做种时间),以及删除(可选是否删文件),支持单个和批量。卡片左划
  会露出 开始/暂停、重新校验、删除 三个圆形按钮。
- **种子详情** —— 常规、Tracker、连接、内容、HTTP 源 五个标签页,支持单文件优先级。
- **仪表盘** —— qui 的全局统计卡片、跨客户端合并的 Tracker 明细,以及每个客户端的卡片(数量、
  传输总量、磁盘占用、备用速度限制开关)。显示哪些板块可在设置里配置。
- **添加种子** —— 磁力链接、URL 和 `.torrent` 文件,包括从其它应用分享过来的磁力链和文件。
- **qui 的全部九套主题**及其配色变体,支持浅色/深色/跟随系统,以及可选的 Material You 动态取色。
- **九种语言** —— 和 qui 提供的完全一致。首次启动自动匹配设备语言,也可在设置里覆盖。
- **隐身模式** —— 用与 qui 相同的哈希算法,把名称、分类、标签和 Tracker 替换成一套确定性的
  Linux 发行版词汇,并在仪表盘上遮蔽实例地址。和 qui 一样,可从列表页直接开关。
- **四个桌面小部件** —— 见 [小部件](#小部件)。
- **Tracker 图标**,取自 qui 自己的图标缓存。

## 小部件

桌面的小部件选择器里会出现四个条目。前三个共用同一个 provider,而且全都可以拉伸——之所以分开
列出,是因为大多数人根本不知道小部件能拖大:

- **qui 速度** —— 2×1,只有上传下载两个速度。
- **qui 概览** —— 2×2,加上标题栏、刷新按钮,以及下载中/做种的数量。
- **qui 统计** —— 4×2,补上暂停和错误的数量、总大小和剩余空间。
- **qui 传输中** —— 4×4,列出当前正在传输的种子,每行带进度条和速度。没有活动种子时回退显示
  最近添加的。点击某一行会打开那个种子的详情页,`+` 打开添加面板。

拉伸前三个中的任意一个都会重新选择布局,所以一个拖到四列宽的 *qui 速度* 会变成完整的统计卡片。

**设置 → 小部件** 可以不经过桌面的选择器直接添加,把小部件锁定到单个客户端(而不是把所有活动
客户端加起来),并选择传输列表优先显示什么。小部件上的种子名称跟随应用的隐身模式开关——桌面是
给旁边站着的人看的。

它们都跟随系统的浅色/深色设置,并在 Android 12 及以上借用桌面自己的圆角半径,所以能和系统
自带的小部件齐平。

多个客户端合并时,剩余空间取其中**最小的那个**——也就是最先满的那块盘——并标上 `≥` 而不是当作
总量呈现,因为不同机器有不同的盘。种子列表自己的头部也是同样的规则。

### 刷新

安卓不允许小部件自身的更新计划快于每 30 分钟一次,而澎湃 OS、MIUI、EMUI 和 ColorOS 会把这个
间隔拉得更长,甚至干脆不执行。所以定时更新只是兜底,不是主要机制。真正让小部件保持最新的是:

- 打开应用 —— 每次仪表盘轮询都会把新数字推给所有已放置的小部件;
- 小部件自己标题栏上的刷新按钮。

在小米设备上,**设置 → 应用设置 → qui → 自启动**打开、**省电策略 → 无限制**,才能让定时更新
真正触发。不设置的话,小部件会一直显示它最后一次成功获取到的数字(带时间戳),直到你点刷新。

没有**超级小部件**(澎湃 OS 的可交互小部件格式)版本:那需要小米的 MiuiWidget SDK 和一个通过
他们商店审核的应用。这里提供的是标准 `AppWidgetProvider` 小部件,澎湃 OS 能正常列出和渲染。

## 语言

qui 支持的九种语言:`en`、`cs`、`de`、`fr`、`it`、`ko`、`pt-BR`、`uk`、`zh-CN`。首次启动时
安卓会挑选与设备语言最接近的一个,**设置 → 语言**可以覆盖。

翻译由 `tools/generate_translations.py` 生成。只要同一句英文在 qui 自己的语言文件里出现过,
就直接复用 qui 的译文——所以种子状态、筛选项名称和操作名在两边读起来完全一样。只有上游没有
对应项的字符串才在 `tools/translations_overrides.json` 里翻译。重新生成:

```bash
python tools/generate_translations.py ../qui-upstream
```

不要手工编辑 `app/src/main/res/values-*/strings.xml`,它们是生成的。

## 环境要求

- Android 8.0(API 26)或更新版本
- 一台能连上的 [qui](https://github.com/autobrr/qui) 服务器

## 连接

填入你的 qui 服务器地址(例如 `http://192.168.1.10:7476`)并登录。

支持两种认证方式:

- **密码** —— 常规的 qui 登录。会话 Cookie 存在设备上。
- **API 密钥** —— 在 qui 的**设置 → API 密钥**里创建。密钥不会过期,应用会保持登录状态;
  推荐用这种。

如果你的 qui 服务器还没有账号,"测试连接"会检测出来,表单会切换成创建第一个账号。

自签名证书默认会被拒绝。**信任自签名证书**开关会关闭对该服务器的证书校验——只在你自己掌控的
服务器上打开。

## 构建

```bash
./gradlew assembleDebug
```

APK 生成在 `app/build/outputs/apk/debug/`。CI 会在每次推送时构建并作为产物上传。

运行测试:

```bash
./gradlew testDebugUnitTest
```

## 发布

推送一个 `v*` 标签会构建 release APK 并挂到 GitHub release 上:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

release 说明取自 [CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md) 和 [CHANGELOG.md](CHANGELOG.md)
里与该标签同名的小节,中英双语一起发布;应用内的更新弹窗读的也是同一份文字,并按设备语言挑选
对应的那一半。所以打标签前先在这两个文件顶部写好新版本的小节。

### 签名

默认情况下 release APK 用安卓的 **debug** 密钥签名。它能正常安装,但那个密钥不是你的,而且
每一份 SDK 装的都是同一个,所以只能当成图方便,算不上真正的签名。

想用自己的密钥签,生成一个 keystore 并添加四个仓库 secret。密码只有你自己看得到:

```bash
keytool -genkeypair -v -keystore release.jks -alias qui -keyalg RSA -keysize 4096 -validity 10000
```

```bash
base64 -w0 release.jks > release.jks.base64
```

然后在 *Settings → Secrets and variables → Actions* 里添加:

| Secret | 值 |
| --- | --- |
| `QUI_KEYSTORE_BASE64` | `release.jks.base64` 的内容 |
| `QUI_KEYSTORE_PASSWORD` | keystore 密码 |
| `QUI_KEY_ALIAS` | `qui` |
| `QUI_KEY_PASSWORD` | 密钥密码 |

务必备份好 `release.jks` 并且不要提交进仓库。安卓靠签名密钥来识别一个应用:丢了就没法在已安装
的版本上发更新,换密钥会强制用户先卸载。发布流程会打印实际使用的证书,方便你确认用的是哪个密钥。

## 与 qui 的对应关系

本应用是 qui API 的客户端,不是 qui 的重新实现:

| qui | Android |
| --- | --- |
| `GET /api/stream`(SSE) | `QuiStreamClient` —— init/update/delta/heartbeat 帧 |
| `GET /api/instances/{id}/torrents` | `QuiRepository.torrents` |
| `GET /api/torrents/cross-instance` | 统一视图 |
| `POST /api/instances/{id}/torrents/bulk-action` | `QuiRepository.bulkAction` |
| `GET /api/tracker-icons` | `TrackerIconStore` |
| `web/src/themes/*.css` | `QuiThemes.kt`,经 OKLCH → sRGB 转换生成 |
| `web/src/i18n/locales/*` | `values-*/strings.xml`,生成 |
| `web/src/lib/utils.ts`、`speedUnits.ts` | `ui/format/Format.kt` |
| `web/src/lib/incognito.ts` | `ui/torrents/Incognito.kt` |
| `TorrentCardsMobile.tsx` | `ui/torrents/TorrentCard.kt` |
| `MobileFooterNav.tsx` | `ui/QuiApp.kt` |
| `contexts/MobileScrollContext.tsx` | `ui/MobileScroll.kt` |
| `pages/Dashboard.tsx` | `ui/dashboard/` |

主题颜色不是靠肉眼调的:qui 的 CSS 自定义属性会被解析,OKLCH 值转换成 sRGB,所以配色和网页端
在数值上完全相同。

## 翻译

英文在 `app/src/main/res/values/strings.xml`,是唯一需要手工编辑的文件。另外八种语言都是生成的:

```bash
python tools/generate_translations.py ../qui-upstream
```

对每一条英文,生成器都会去 qui 自己的语言文件里找同一句话并复用它的译文,所以种子状态、筛选项
和操作名读起来和网页端一模一样(大约占词条总数的四分之三)。其余的来自手写的
`tools/translations_overrides.json`,覆盖只在本应用中存在的字符串——登录表单、仪表盘卡片,以及
安卓特有的设置项。

`tools/check_translations.py` 会在 CI 里运行,如果某个语言缺少词条,或者译文的 `%1$s` 占位符
与英文原文对不上,就会失败。它不需要 qui 的源码。

新增一种语言需要改三处:生成器里的 `LANGUAGES`、`ui/AppLocale.kt` 里的 `SUPPORTED_LANGUAGES`
和 `LANGUAGE_NAMES`,以及 `res/xml/locales_config.xml`。

## 未实现

qui 的服务器管理类功能是刻意不做的——它们更适合在网页端操作,而且有几项是付费功能:

cross-seed、自动化规则、RSS 管理、实例备份、孤立文件/目录扫描、制作种子、Jackett/Torznab
索引器、*arr 集成,以及实例的增删改。这些请在 qui 里管理;本应用是给日常使用的种子客户端。

两个值得知道的限制:

- **小部件**按安卓自己的计划刷新,不会快于 30 分钟一次——见[刷新](#刷新)。
- **仪表盘板块的显示设置**存在设备上,不会通过 qui 的 `/api/dashboard-settings` 同步,所以不会
  跟着你到网页端。

## 许可证

GPL-2.0-or-later,与上游 qui 一致。见 [LICENSE](LICENSE)。

qui 的名称和标志属于 autobrr 项目。标志以相同许可证从 `web/src/components/ui/Logo.tsx` 复用。
