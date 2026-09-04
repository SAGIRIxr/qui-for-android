# 服务器统计与 QUI 上游功能跟进设计

## 目标

在不增加服务器请求数量的前提下，为 Android 仪表盘补齐 QUI WebUI 的服务器统计，并同步三个与日常种子管理直接相关的上游变化：I2P Peer 地址显示、种子内容文件排序和繁体中文支持。

## 范围

本次实现包含：

1. 仪表盘服务器统计折叠卡片及板块显示设置。
2. IPv4、IPv6 和 I2P Peer 地址的统一显示逻辑。
3. 内容页按名称、大小、进度和优先级排序。
4. `zh-TW` Android 资源、语言选择配置、翻译生成器和 README 更新。

本次不包含 torrent 导出文件流程、QUI `client_settings` 跨设备同步、Spreadsheet 结构化伪装主题、Cross-seed/ARR/自动化/索引器等服务器管理功能。统一视图 bulk-action targets 已由现有 Android 实现覆盖，也不产生重复改动。

## 服务器统计

### 数据与聚合

继续使用 `DashboardViewModel` 当前每实例一行 torrent-list 请求返回的完整 `serverState`，不新增 API、Repository 方法或轮询任务。

新增纯 Kotlin 统计模型，将 `InstanceCard` 转换为可测试的展示数据：

- 仅纳入请求成功，且 `allTimeDownloaded` 或 `allTimeUploaded` 至少一个大于 0 的实例。
- 每实例累计下载取 `allTimeDownloaded ?: 0`，本次下载取 `sessionDownloaded`，累计上传取 `allTimeUploaded ?: 0`，本次上传取 `sessionUploaded`。
- 每实例分享率在累计下载大于 0 时为 `累计上传 / 累计下载`，否则为 `0.0`，与 QUI WebUI 一致。
- 汇总累计下载、累计上传和 Peer 数分别对纳入行求和；汇总分享率使用 `汇总上传 / 汇总下载`，不对实例分享率求平均。
- Peer 数保持可空语义：实例字段缺失显示 `-`；汇总只累加已知值，并且仅在总数大于 0 时显示。
- 汇总上传和下载均为 0 时不渲染整个服务器统计板块。

聚合逻辑放在独立、无 Compose 依赖的 dashboard 文件中，避免继续增大 `DashboardViewModel.kt`，并允许 JVM 单元测试直接覆盖规则。

### Compose 交互

服务器统计位于仪表盘第一项，采用已确认的方案 A：

- 卡片默认展开，标题行带展开/收起图标。
- 标题下方用适合手机宽度的两行摘要展示累计下载、累计上传、分享率和 Peer 总数；数值沿用 `formatBytes` 与现有分享率颜色规则。
- 展开后每个实例一行，显示实例名、累计下载、累计上传和分享率；点击一行打开 Material 3 `ModalBottomSheet`。
- Bottom Sheet 以两列指标网格展示累计下载、本次下载、累计上传、本次上传、分享率和 Peer 数。
- 收起状态只在当前 Compose 页面生命周期内保留，默认值为展开；本次不新增跨启动的折叠状态同步。

设置页“仪表盘”板块增加默认开启的“服务器统计”复选项。该开关沿用现有 DataStore、RootViewModel 和 SettingsScreen 数据流，仅控制是否渲染，不改变网络轮询。

### 兼容与异常

- 旧版 QUI 未返回累计字段时，该实例不进入统计；其他仪表盘卡片照常显示。
- 某个实例请求失败或被禁用时，其 `InstanceCard.errorRes` 非空，不进入统计聚合。
- Peer 字段缺失与明确返回 0 必须区分，分别显示 `-` 和 `0`。
- Bottom Sheet 所引用实例在刷新后消失时自动关闭，避免展示陈旧数据。

## Peer 地址兼容

`TorrentPeer.ip` 与 `TorrentPeer.port` 改为可空，以表达 I2P Peer 没有 IP/端口的上游数据。新增纯函数生成显示地址：

1. `key` 非空时优先原样显示 canonical key；这覆盖 IPv4、带方括号的 IPv6 和 `*.b32.i2p`。
2. 没有 key 时，若 IP 和有效端口同时存在，则组合为地址；IPv6 回退格式使用 `[地址]:端口`。
3. 只有 IP 时显示 IP；所有字段均缺失时显示 `-`。

Peer 列表改用该函数，不再直接拼接 `${ip}:${port}`。本次不新增封禁、复制地址或其他 Peer 操作。

## 内容文件排序

新增无 Android 依赖的文件排序模型：

- 列：`Name`、`Size`、`Progress`、`Priority`。
- 方向：`Ascending`、`Descending`。
- 默认值：名称升序。
- 点击当前列切换方向；切换到任意其他列时默认升序。
- 名称比较忽略大小写，结果相同时按原始名称和文件 index 保证确定顺序。
- 大小按 `size`，进度按 `progress`，优先级按 qBittorrent 数值 `priority` 比较；数值相同时以名称和 index 作为稳定次序。

排序选择保存在 `TorrentDetailViewModel` 对应 torrent 的页面状态中，轮询刷新文件列表时继续生效，离开详情页后重置。内容列表上方增加四个紧凑排序控件，活动列显示方向图标并提供明确的无障碍描述。

## 繁体中文与翻译生成

- 翻译生成器增加 `("zh-TW", "values-zh-rTW")`。
- `locales_config.xml` 增加 `zh-TW`，系统语言选择器可发现该语言。
- 服务器统计和文件排序文案优先通过显式 QUI i18n key 映射复用上游译文。
- 上游没有的 Android 专属文案在 `translations_overrides.json` 中提供完整 `zh-TW` 值。
- 重新生成全部非英语资源，运行 `--report` 要求所有语言覆盖率为 100%，且生成器退出码为 0。
- README 中语言数量从九种更新为十种，并加入 `zh-TW`。

生成的 `values-*` XML 不逐条手写测试；其验收证据是生成器 100% 覆盖报告、Android resource merge 和 Debug 构建。这是本设计对生成资源采用的明确 TDD 例外，业务逻辑仍全部测试先行。

## 测试策略

实现遵循 Red-Green-Refactor：

1. 服务器统计测试覆盖实例筛选、汇总值、加权分享率、累计下载为 0、Peer 可空与失败实例排除。
2. Peer 地址测试覆盖 canonical IPv4、canonical IPv6、I2P key、IPv6 回退、仅 IP 和完全缺失。
3. 文件排序测试覆盖默认名称顺序、四列升降序、切换列默认方向、相同数值的确定性次序，以及轮询数据替换后排序选择不变。
4. 翻译运行生成器报告并检查十种 locale；生成输出执行 XML/resource merge 验证。
5. 最终运行完整 JVM 单元测试、Kotlin/Android lint（若项目现有任务可用）和 `assembleDebug`，并检查 `git diff --check`。

Compose UI 当前没有仪器化测试基础设施，本次不为一个卡片额外引入整套 UI 测试栈；UI 通过可测试展示模型、编译、资源合并和页面渲染检查保证。

## 文档与交付

README/中文 README 更新功能列表、语言列表和与 QUI 的对应关系；CHANGELOG 记录四项用户可见变化。交付说明区分 v1.28.0 稳定功能与 2026-09-03 `develop` 上的内容排序，并列出未纳入的独立后续候选。
