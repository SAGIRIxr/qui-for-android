# Server Statistics and Upstream Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 qui for Android 中加入移动端服务器统计，并同步 I2P Peer 地址、内容文件排序和繁体中文支持。

**Architecture:** 服务器统计、Peer 地址和文件排序都实现为无 Android 依赖的纯 Kotlin 逻辑，由现有 ViewModel 状态和 Compose 页面消费；服务器统计复用 Dashboard 已获取的完整 `serverState`，不增加网络请求。翻译继续以英文资源为源，优先复用 QUI 上游 locale，并通过 override 补齐 Android 专属繁中内容。

**Tech Stack:** Kotlin 2.0.21、Jetpack Compose Material 3、StateFlow、Preferences DataStore、kotlinx.serialization、JUnit 4.13.2、Python 3 翻译生成器、Android Gradle Plugin 8.7.3。

**Spec:** `docs/superpowers/specs/2026-09-04-server-stats-and-upstream-parity-design.md`

## Global Constraints

- 继续使用 `DashboardViewModel` 当前每实例一行 torrent-list 请求返回的完整 `serverState`，不新增 API、Repository 方法或轮询任务。
- 本次范围只包含服务器统计、I2P Peer 地址、内容排序和 `zh-TW`；不引入 torrent 导出、`client_settings` 同步、Spreadsheet 主题或服务器管理功能。
- 服务器统计仅纳入请求成功且累计下载或累计上传至少一个大于 0 的实例；总分享率为总上传除以总下载，总下载为 0 时取 `0.0`。
- Peer 缺失显示 `-`，明确为 0 显示 `0`；总上传和总下载均为 0 时不渲染服务器统计板块。
- 文件排序默认名称升序；点击同一列切换方向，切换到名称默认升序，切换到数值列默认降序。
- 生成资源采用规格中明确的 TDD 例外，但必须通过 100% 翻译报告、资源合并和 Debug 构建。
- 不新增第三方依赖，不引入 Compose UI 测试框架，不改变 minSdk 26、targetSdk 35 或现有 API 合同。

---

## File Map

- Create `app/src/main/java/dev/qui/android/ui/dashboard/ServerStatistics.kt`: 服务器统计展示模型与聚合函数。
- Create `app/src/test/java/dev/qui/android/ui/dashboard/ServerStatisticsTest.kt`: 聚合、筛选、分享率和 Peer 可空规则。
- Modify `app/src/main/java/dev/qui/android/ui/dashboard/DashboardViewModel.kt`: 在 `DashboardUiState` 暴露统计派生值。
- Modify `app/src/main/java/dev/qui/android/ui/dashboard/DashboardScreen.kt`: 首位折叠卡片、实例行和详情 Bottom Sheet。
- Modify `app/src/main/java/dev/qui/android/data/AppPreferencesStore.kt`: 默认开启的服务器统计板块偏好。
- Modify `app/src/main/java/dev/qui/android/ui/RootViewModel.kt`: 偏好 setter 转发。
- Modify `app/src/main/java/dev/qui/android/ui/settings/SettingsScreen.kt`: 仪表盘设置复选项。
- Create `app/src/main/java/dev/qui/android/ui/detail/PeerAddress.kt`: canonical、IPv4、IPv6 和 I2P 地址格式化。
- Create `app/src/test/java/dev/qui/android/ui/detail/PeerAddressTest.kt`: Peer 地址兼容测试。
- Modify `app/src/main/java/dev/qui/android/data/model/Torrent.kt`: `TorrentPeer.ip`/`port` 改为可空。
- Create `app/src/main/java/dev/qui/android/ui/detail/FileSort.kt`: 文件排序状态、切换规则和 comparator。
- Create `app/src/test/java/dev/qui/android/ui/detail/FileSortTest.kt`: 四列排序、方向、确定性和刷新保持测试。
- Modify `app/src/main/java/dev/qui/android/ui/detail/TorrentDetailViewModel.kt`: 保存并更新 `FileSort`。
- Modify `app/src/main/java/dev/qui/android/ui/detail/TorrentDetailScreen.kt`: Peer 地址函数和内容排序控件。
- Modify `app/src/main/res/values/strings.xml`: 服务器统计英文资源。
- Modify `tools/generate_translations.py`: `zh-TW` locale 和服务器统计显式 key。
- Modify `tools/translations_overrides.json`: 全部 Android 专属繁中翻译。
- Modify `tools/check_translations.py`: 十语言说明文字。
- Modify `app/src/main/java/dev/qui/android/ui/AppLocale.kt`: `zh-TW` 标签与 endonym。
- Modify `app/src/main/res/xml/locales_config.xml`: 系统可发现的 `zh-TW` locale。
- Generate `app/src/main/res/values-{cs,de,fr,it,ko,uk,zh-rCN,b+pt+BR}/strings.xml`: 服务器统计新增文案及统一生成结果。
- Create `app/src/main/res/values-zh-rTW/strings.xml`: 完整繁中资源。
- Modify `README.md`, `README.zh-CN.md`, `CHANGELOG.md`, `CHANGELOG.zh-CN.md`: 功能、语言和上游对应说明。

## Execution Setup

- [ ] **Step 1: Read the approved spec and this plan**

Run:

```powershell
Get-Content -Raw docs/superpowers/specs/2026-09-04-server-stats-and-upstream-parity-design.md
Get-Content -Raw docs/superpowers/plans/2026-09-04-server-stats-and-upstream-parity.md
```

Expected: both files describe the same four features and the same exclusions.

- [ ] **Step 2: Create an isolated worktree**

Invoke `superpowers:using-git-worktrees`, create branch `feature/server-stats-upstream-parity` from commit containing this plan, and verify the resolved worktree path is outside the primary checkout’s `.git` directory.

- [ ] **Step 3: Establish a clean baseline**

Run in the isolated worktree:

```powershell
$env:PYTHONDONTWRITEBYTECODE = "1"
python tools/check_translations.py
.\gradlew.bat :app:testDebugUnitTest
git status --short
```

Expected: translation check reports `8 locales, 389 strings each - all present.`, unit tests pass, and the isolated worktree is clean.

### Task 1: Server Statistics Domain Model

**Files:**
- Create: `app/src/main/java/dev/qui/android/ui/dashboard/ServerStatistics.kt`
- Modify: `app/src/main/java/dev/qui/android/ui/dashboard/DashboardViewModel.kt:73-112`
- Test: `app/src/test/java/dev/qui/android/ui/dashboard/ServerStatisticsTest.kt`

**Interfaces:**
- Consumes: `InstanceCard(instance, sessionDownloaded, sessionUploaded, allTimeDownloaded, allTimeUploaded, peerConnections, errorRes)`.
- Produces: `ServerStatisticsRow`, `ServerStatistics`, `buildServerStatistics(cards: List<InstanceCard>): ServerStatistics?`, and `DashboardUiState.serverStatistics: ServerStatistics?`.

- [ ] **Step 1: Write the failing aggregation tests**

Create `ServerStatisticsTest.kt` with:

```kotlin
package dev.qui.android.ui.dashboard

import dev.qui.android.data.model.Instance
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStatisticsTest {

    @Test
    fun `excludes empty and failed instances`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(id = 1),
                    card(id = 2, downloaded = 0, uploaded = 0),
                    card(id = 3, downloaded = 10, errorRes = 1),
                    card(id = 4, downloaded = 10, uploaded = 5),
                )
            )
        )

        assertEquals(listOf(4), statistics.rows.map { it.instanceId })
    }

    @Test
    fun `sums totals and calculates weighted share ratio`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(
                        id = 1,
                        downloaded = 100,
                        uploaded = 50,
                        downloadedSession = 10,
                        uploadedSession = 20,
                    ),
                    card(
                        id = 2,
                        downloaded = 300,
                        uploaded = 600,
                        downloadedSession = 30,
                        uploadedSession = 60,
                        peers = 7,
                    ),
                )
            )
        )

        assertEquals(400L, statistics.totalDownloaded)
        assertEquals(650L, statistics.totalUploaded)
        assertEquals(1.625, statistics.shareRatio, 0.0)
        assertEquals(7L, statistics.totalPeerConnections)
        assertEquals(10L, statistics.rows.first().downloadedSession)
        assertEquals(60L, statistics.rows.last().uploadedSession)
    }

    @Test
    fun `uses zero ratio when cumulative download is zero`() {
        val statistics = requireNotNull(
            buildServerStatistics(listOf(card(id = 1, downloaded = 0, uploaded = 50)))
        )

        assertEquals(0.0, statistics.shareRatio, 0.0)
        assertEquals(0.0, statistics.rows.single().shareRatio, 0.0)
    }

    @Test
    fun `preserves unknown and explicit zero peer counts`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(id = 1, downloaded = 10, peers = null),
                    card(id = 2, downloaded = 20, peers = 0),
                )
            )
        )

        assertEquals(listOf(null, 0L), statistics.rows.map { it.peerConnections })
        assertEquals(0L, statistics.totalPeerConnections)
    }

    @Test
    fun `returns null when no instance has transfer history`() {
        assertEquals(null, buildServerStatistics(listOf(card(id = 1))))
    }

    private fun card(
        id: Int,
        downloaded: Long? = null,
        uploaded: Long? = null,
        downloadedSession: Long = 0,
        uploadedSession: Long = 0,
        peers: Long? = null,
        errorRes: Int? = null,
    ) = InstanceCard(
        instance = Instance(id = id, name = "client-$id"),
        sessionDownloaded = downloadedSession,
        sessionUploaded = uploadedSession,
        allTimeDownloaded = downloaded,
        allTimeUploaded = uploaded,
        peerConnections = peers,
        errorRes = errorRes,
    )
}
```

- [ ] **Step 2: Run the focused test and verify red state**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.dashboard.ServerStatisticsTest"
```

Expected: compilation fails because `buildServerStatistics` and its models do not exist.

- [ ] **Step 3: Implement the minimal pure Kotlin model**

Create `ServerStatistics.kt` with:

```kotlin
/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.dashboard

data class ServerStatisticsRow(
    val instanceId: Int,
    val instanceName: String,
    val downloaded: Long,
    val downloadedSession: Long,
    val uploaded: Long,
    val uploadedSession: Long,
    val shareRatio: Double,
    val peerConnections: Long?,
)

data class ServerStatistics(
    val rows: List<ServerStatisticsRow>,
    val totalDownloaded: Long,
    val totalUploaded: Long,
    val shareRatio: Double,
    val totalPeerConnections: Long,
)

fun buildServerStatistics(cards: List<InstanceCard>): ServerStatistics? {
    val rows = cards.asSequence()
        .filter(InstanceCard::isHealthy)
        .mapNotNull { card ->
            val downloaded = card.allTimeDownloaded ?: 0L
            val uploaded = card.allTimeUploaded ?: 0L
            if (downloaded <= 0L && uploaded <= 0L) return@mapNotNull null

            ServerStatisticsRow(
                instanceId = card.instance.id,
                instanceName = card.instance.name,
                downloaded = downloaded,
                downloadedSession = card.sessionDownloaded,
                uploaded = uploaded,
                uploadedSession = card.sessionUploaded,
                shareRatio = ratio(uploaded, downloaded),
                peerConnections = card.peerConnections,
            )
        }
        .toList()

    val totalDownloaded = rows.sumOf(ServerStatisticsRow::downloaded)
    val totalUploaded = rows.sumOf(ServerStatisticsRow::uploaded)
    if (totalDownloaded == 0L && totalUploaded == 0L) return null

    return ServerStatistics(
        rows = rows,
        totalDownloaded = totalDownloaded,
        totalUploaded = totalUploaded,
        shareRatio = ratio(totalUploaded, totalDownloaded),
        totalPeerConnections = rows.sumOf { it.peerConnections ?: 0L },
    )
}

private fun ratio(uploaded: Long, downloaded: Long): Double =
    if (downloaded > 0L) uploaded.toDouble() / downloaded else 0.0
```

Add this property inside `DashboardUiState`:

```kotlin
val serverStatistics: ServerStatistics? get() = buildServerStatistics(cards)
```

- [ ] **Step 4: Run focused and full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.dashboard.ServerStatisticsTest"
.\gradlew.bat :app:testDebugUnitTest
```

Expected: both commands pass; no network or Android runtime is needed by the new tests.

- [ ] **Step 5: Commit the domain model**

```powershell
git add app/src/main/java/dev/qui/android/ui/dashboard/ServerStatistics.kt app/src/main/java/dev/qui/android/ui/dashboard/DashboardViewModel.kt app/src/test/java/dev/qui/android/ui/dashboard/ServerStatisticsTest.kt
git commit -m "feat: add server statistics aggregation"
```

### Task 2: Dashboard Statistics Card and Preference

**Files:**
- Modify: `app/src/main/java/dev/qui/android/data/AppPreferencesStore.kt:45-130,197-211`
- Modify: `app/src/main/java/dev/qui/android/ui/RootViewModel.kt:118-132`
- Modify: `app/src/main/java/dev/qui/android/ui/settings/SettingsScreen.kt:287-316`
- Modify: `app/src/main/java/dev/qui/android/ui/dashboard/DashboardScreen.kt:1-160,650-end`
- Modify: `app/src/main/res/values/strings.xml:325-360`
- Modify: `tools/generate_translations.py:35-90`
- Generate: the eight existing translated `strings.xml` files.

**Interfaces:**
- Consumes: `DashboardUiState.serverStatistics`, `ServerStatistics`, `ServerStatisticsRow`, `formatBytes`, `formatRatio`, `QuiTheme.ratioColor`.
- Produces: `Snapshot.showServerStats: Boolean`, `AppPreferencesStore.setShowServerStats(Boolean)`, `RootViewModel.setShowServerStats(Boolean)`, `ServerStatisticsCard`, and `ServerStatisticsBottomSheet`.

- [ ] **Step 1: Add the preference through the complete persistence chain**

Add the following exact members beside the three existing dashboard visibility members:

```kotlin
// AppPreferencesStore.Keys
val showServerStats = booleanPreferencesKey("dash_server_stats")

// AppPreferencesStore.Snapshot
val showServerStats: Boolean = true,

// snapshot flow mapping
showServerStats = prefs[Keys.showServerStats] ?: true,

// AppPreferencesStore setter
suspend fun setShowServerStats(enabled: Boolean) = context.prefsDataStore.edit {
    it[Keys.showServerStats] = enabled
}

// RootViewModel forwarding method
fun setShowServerStats(enabled: Boolean) = viewModelScope.launch {
    prefsStore.setShowServerStats(enabled)
}
```

Insert this first in Settings’ Dashboard `SectionCard`:

```kotlin
CheckRow(
    label = stringResource(R.string.dashboard_section_server_stats),
    checked = prefs.showServerStats,
    onChange = root::setShowServerStats,
)
```

- [ ] **Step 2: Add English resources and explicit upstream mappings**

Add these strings to the Dashboard section of `values/strings.xml`:

```xml
<string name="dashboard_section_server_stats">Server Statistics</string>
<string name="dashboard_server_downloaded">Downloaded</string>
<string name="dashboard_server_downloaded_session">Downloaded (Session)</string>
<string name="dashboard_server_uploaded">Uploaded</string>
<string name="dashboard_server_uploaded_session">Uploaded (Session)</string>
<string name="dashboard_server_ratio">Ratio</string>
<string name="dashboard_server_peers">Peers</string>
```

Add these entries to `QUI_KEY_MAP`:

```python
"dashboard_section_server_stats": "dashboard:serverStats.title",
"dashboard_server_downloaded": "dashboard:serverStats.tableHeaders.downloaded",
"dashboard_server_downloaded_session": "dashboard:serverStats.tableHeaders.downloadedSession",
"dashboard_server_uploaded": "dashboard:serverStats.tableHeaders.uploaded",
"dashboard_server_uploaded_session": "dashboard:serverStats.tableHeaders.uploadedSession",
"dashboard_server_ratio": "dashboard:serverStats.tableHeaders.ratio",
"dashboard_server_peers": "dashboard:serverStats.tableHeaders.peers",
```

Regenerate the existing locales:

```powershell
$env:PYTHONDONTWRITEBYTECODE = "1"
python tools/generate_translations.py C:\Users\23686\AppData\Local\Temp\codex-qui-upstream-20260904
python tools/check_translations.py
```

Expected: the generator reports `396 source strings`, all eight locales are complete, and the checker reports `8 locales, 396 strings each - all present.`

- [ ] **Step 3: Wire screen-local expansion and selection state**

Opt the file into Material 3 experimental APIs, then add this state near the start of `DashboardScreen`:

```kotlin
val serverStatistics = state.serverStatistics
var serverStatsExpanded by remember { mutableStateOf(true) }
var selectedServerStatsId by remember { mutableStateOf<Int?>(null) }

LaunchedEffect(serverStatistics, prefs.showServerStats) {
    val selectedId = selectedServerStatsId
    if (
        selectedId != null &&
        (!prefs.showServerStats || serverStatistics?.rows?.none {
            it.instanceId == selectedId
        } != false)
    ) {
        selectedServerStatsId = null
    }
}
```

Insert this before `showGlobalStats` in the `LazyColumn`:

```kotlin
if (prefs.showServerStats && serverStatistics != null) {
    item(key = "server-statistics") {
        ServerStatisticsCard(
            statistics = serverStatistics,
            expanded = serverStatsExpanded,
            onToggleExpanded = { serverStatsExpanded = !serverStatsExpanded },
            onOpenInstance = { selectedServerStatsId = it },
        )
    }
}
```

After the `LazyColumn`, resolve the current row rather than storing a stale row object:

```kotlin
val selectedServerStats = if (prefs.showServerStats) {
    serverStatistics?.rows?.firstOrNull { it.instanceId == selectedServerStatsId }
} else {
    null
}
selectedServerStats?.let { row ->
    ServerStatisticsBottomSheet(
        row = row,
        onDismiss = { selectedServerStatsId = null },
    )
}
```

- [ ] **Step 4: Implement the foldable card**

Add `ServerStatisticsCard` using the existing `QuiCard` and `Metric` components:

```kotlin
@Composable
private fun ServerStatisticsCard(
    statistics: ServerStatistics,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenInstance: (Int) -> Unit,
) {
    val palette = QuiTheme.palette

    QuiCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_section_server_stats).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.mutedForeground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.dashboard_show_less else R.string.dashboard_show_more
                ),
                tint = palette.mutedForeground,
            )
        }

        Spacer(Modifier.height(12.dp))
        ServerStatsMetricPair(
            firstLabel = stringResource(R.string.dashboard_server_downloaded),
            firstValue = formatBytes(statistics.totalDownloaded),
            firstColor = QuiTheme.downloadColor,
            secondLabel = stringResource(R.string.dashboard_server_uploaded),
            secondValue = formatBytes(statistics.totalUploaded),
            secondColor = QuiTheme.uploadColor,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric(
                label = stringResource(R.string.dashboard_server_ratio),
                value = formatRatio(statistics.shareRatio),
                modifier = Modifier.weight(1f),
                color = QuiTheme.ratioColor(statistics.shareRatio),
            )
            if (statistics.totalPeerConnections > 0L) {
                Metric(
                    label = stringResource(R.string.dashboard_server_peers),
                    value = statistics.totalPeerConnections.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(10.dp))
                statistics.rows.forEach { row ->
                    HorizontalDivider(color = palette.border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenInstance(row.instanceId) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.instanceName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "↓${formatBytes(row.downloaded)} · ↑${formatBytes(row.uploaded)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.mutedForeground,
                            )
                        }
                        Text(
                            text = formatRatio(row.shareRatio),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = QuiTheme.ratioColor(row.shareRatio),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Implement the six-metric Bottom Sheet**

Add the pair helper and Bottom Sheet:

```kotlin
@Composable
private fun ServerStatsMetricPair(
    firstLabel: String,
    firstValue: String,
    firstColor: androidx.compose.ui.graphics.Color? = null,
    secondLabel: String,
    secondValue: String,
    secondColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(Modifier.fillMaxWidth()) {
        Metric(
            label = firstLabel,
            value = firstValue,
            modifier = Modifier.weight(1f),
            color = firstColor,
        )
        Metric(
            label = secondLabel,
            value = secondValue,
            modifier = Modifier.weight(1f),
            color = secondColor,
        )
    }
}

@Composable
private fun ServerStatisticsBottomSheet(
    row: ServerStatisticsRow,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = row.instanceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            ServerStatsMetricPair(
                firstLabel = stringResource(R.string.dashboard_server_downloaded),
                firstValue = formatBytes(row.downloaded),
                firstColor = QuiTheme.downloadColor,
                secondLabel = stringResource(R.string.dashboard_server_downloaded_session),
                secondValue = formatBytes(row.downloadedSession),
                secondColor = QuiTheme.downloadColor,
            )
            Spacer(Modifier.height(16.dp))
            ServerStatsMetricPair(
                firstLabel = stringResource(R.string.dashboard_server_uploaded),
                firstValue = formatBytes(row.uploaded),
                firstColor = QuiTheme.uploadColor,
                secondLabel = stringResource(R.string.dashboard_server_uploaded_session),
                secondValue = formatBytes(row.uploadedSession),
                secondColor = QuiTheme.uploadColor,
            )
            Spacer(Modifier.height(16.dp))
            ServerStatsMetricPair(
                firstLabel = stringResource(R.string.dashboard_server_ratio),
                firstValue = formatRatio(row.shareRatio),
                firstColor = QuiTheme.ratioColor(row.shareRatio),
                secondLabel = stringResource(R.string.dashboard_server_peers),
                secondValue = row.peerConnections?.toString() ?: "-",
            )
        }
    }
}
```

Add imports for `ModalBottomSheet` and the existing layout/icon types used above. Do not add a new request or ViewModel mutation for expansion or row selection.

- [ ] **Step 6: Compile and verify translation parity**

Run:

```powershell
python tools/check_translations.py
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: all tests pass, all eight translated resources contain 396 entries, and Debug APK assembly succeeds.

- [ ] **Step 7: Commit the Dashboard feature**

```powershell
git add app/src/main/java/dev/qui/android/data/AppPreferencesStore.kt app/src/main/java/dev/qui/android/ui/RootViewModel.kt app/src/main/java/dev/qui/android/ui/settings/SettingsScreen.kt app/src/main/java/dev/qui/android/ui/dashboard/DashboardScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-cs/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-ko/strings.xml app/src/main/res/values-b+pt+BR/strings.xml app/src/main/res/values-uk/strings.xml app/src/main/res/values-zh-rCN/strings.xml tools/generate_translations.py
git commit -m "feat: show server statistics on dashboard"
```

### Task 3: I2P-Aware Peer Addresses

**Files:**
- Create: `app/src/main/java/dev/qui/android/ui/detail/PeerAddress.kt`
- Modify: `app/src/main/java/dev/qui/android/data/model/Torrent.kt:162-183`
- Modify: `app/src/main/java/dev/qui/android/ui/detail/TorrentDetailScreen.kt:508-565`
- Test: `app/src/test/java/dev/qui/android/ui/detail/PeerAddressTest.kt`

**Interfaces:**
- Consumes: `TorrentPeer(key: String?, ip: String?, port: Int?)`.
- Produces: `formatPeerAddress(peer: TorrentPeer): String`.

- [ ] **Step 1: Write failing address and decode tests**

Create `PeerAddressTest.kt`:

```kotlin
package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentPeer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerAddressTest {

    @Test
    fun `canonical key wins for IPv4 IPv6 and I2P`() {
        assertEquals(
            "192.0.2.1:6881",
            formatPeerAddress(TorrentPeer(key = "192.0.2.1:6881", ip = "ignored", port = 1)),
        )
        assertEquals(
            "[2001:db8::1]:6881",
            formatPeerAddress(TorrentPeer(key = "[2001:db8::1]:6881")),
        )
        assertEquals(
            "exampledestination.b32.i2p",
            formatPeerAddress(TorrentPeer(key = "exampledestination.b32.i2p")),
        )
    }

    @Test
    fun `falls back to IPv4 and bracketed IPv6`() {
        assertEquals(
            "192.0.2.2:8080",
            formatPeerAddress(TorrentPeer(ip = "192.0.2.2", port = 8080)),
        )
        assertEquals(
            "[2001:db8::2]:8080",
            formatPeerAddress(TorrentPeer(ip = "2001:db8::2", port = 8080)),
        )
    }

    @Test
    fun `falls back to IP alone and dash for missing fields`() {
        assertEquals("192.0.2.3", formatPeerAddress(TorrentPeer(ip = "192.0.2.3")))
        assertEquals("192.0.2.3", formatPeerAddress(TorrentPeer(ip = "192.0.2.3", port = 0)))
        assertEquals("-", formatPeerAddress(TorrentPeer()))
    }

    @Test
    fun `missing JSON address fields remain null`() {
        val peer = Json { ignoreUnknownKeys = true }.decodeFromString<TorrentPeer>("{}")

        assertNull(peer.ip)
        assertNull(peer.port)
    }
}
```

- [ ] **Step 2: Verify the tests fail for the current model and UI helper**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.detail.PeerAddressTest"
```

Expected: compilation fails because `formatPeerAddress` is absent and the model fields are not nullable.

- [ ] **Step 3: Make the model accurately represent I2P responses**

Change only these fields in `TorrentPeer`:

```kotlin
val ip: String? = null,
val port: Int? = null,
```

Keep `key`, Repository flattening, and every other serialized field unchanged.

- [ ] **Step 4: Implement the formatter**

Create `PeerAddress.kt`:

```kotlin
/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentPeer

fun formatPeerAddress(peer: TorrentPeer): String {
    peer.key?.takeIf(String::isNotBlank)?.let { return it }

    val ip = peer.ip?.trim().orEmpty()
    if (ip.isEmpty()) return "-"

    val port = peer.port?.takeIf { it in 1..65535 } ?: return ip
    val host = if (':' in ip && !(ip.startsWith('[') && ip.endsWith(']'))) {
        "[$ip]"
    } else {
        ip
    }
    return "$host:$port"
}
```

Replace the direct interpolation in `PeersTab` with:

```kotlin
text = formatPeerAddress(peer),
```

- [ ] **Step 5: Run focused and regression tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.detail.PeerAddressTest"
.\gradlew.bat :app:testDebugUnitTest
```

Expected: canonical IPv4/IPv6/I2P, both fallbacks, missing fields, and all existing tests pass.

- [ ] **Step 6: Commit the compatibility fix**

```powershell
git add app/src/main/java/dev/qui/android/data/model/Torrent.kt app/src/main/java/dev/qui/android/ui/detail/PeerAddress.kt app/src/main/java/dev/qui/android/ui/detail/TorrentDetailScreen.kt app/src/test/java/dev/qui/android/ui/detail/PeerAddressTest.kt
git commit -m "fix: display I2P peer addresses"
```

### Task 4: Torrent Content Sorting

**Files:**
- Create: `app/src/main/java/dev/qui/android/ui/detail/FileSort.kt`
- Modify: `app/src/main/java/dev/qui/android/ui/detail/TorrentDetailViewModel.kt:30-130`
- Modify: `app/src/main/java/dev/qui/android/ui/detail/TorrentDetailScreen.kt:178-214,568-630`
- Test: `app/src/test/java/dev/qui/android/ui/detail/FileSortTest.kt`

**Interfaces:**
- Consumes: `TorrentFile(index, name, size, progress, priority)`.
- Produces: `FileSortColumn`, `SortDirection`, `FileSort`, `sortTorrentFiles(List<TorrentFile>, FileSort)`, `toggleFileSort(FileSort, FileSortColumn)`, `DetailUiState.sortedFiles`, and `TorrentDetailViewModel.toggleFileSort(FileSortColumn)`.

- [ ] **Step 1: Write failing sort behavior tests**

Create `FileSortTest.kt`:

```kotlin
package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortTest {

    @Test
    fun `default order is case-insensitive name ascending with deterministic ties`() {
        val files = listOf(
            file(2, "zeta.mkv"),
            file(3, "alpha.mkv"),
            file(1, "Alpha.mkv"),
        )

        assertEquals(listOf(1, 3, 2), sortTorrentFiles(files, FileSort()).map { it.index })
    }

    @Test
    fun `size supports both directions and name tie breaking`() {
        val files = listOf(
            file(2, "Beta", size = 20),
            file(1, "Alpha", size = 20),
            file(3, "Zed", size = 10),
        )

        assertEquals(
            listOf(3, 1, 2),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Ascending),
            ).map { it.index },
        )
        assertEquals(
            listOf(1, 2, 3),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Descending),
            ).map { it.index },
        )
    }

    @Test
    fun `progress and priority use numeric values`() {
        val files = listOf(
            file(1, "One", progress = 0.5, priority = 1),
            file(2, "Two", progress = 0.9, priority = 0),
            file(3, "Three", progress = 0.1, priority = 7),
        )

        assertEquals(
            listOf(2, 1, 3),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Progress, SortDirection.Descending),
            ).map { it.index },
        )
        assertEquals(
            listOf(3, 1, 2),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Priority, SortDirection.Descending),
            ).map { it.index },
        )
    }

    @Test
    fun `same column toggles and a new column gets its conventional default`() {
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Descending),
            toggleFileSort(FileSort(), FileSortColumn.Name),
        )
        assertEquals(
            FileSort(FileSortColumn.Size, SortDirection.Descending),
            toggleFileSort(FileSort(), FileSortColumn.Size),
        )
        assertEquals(
            FileSort(FileSortColumn.Size, SortDirection.Ascending),
            toggleFileSort(
                FileSort(FileSortColumn.Size, SortDirection.Descending),
                FileSortColumn.Size,
            ),
        )
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Ascending),
            toggleFileSort(
                FileSort(FileSortColumn.Priority, SortDirection.Descending),
                FileSortColumn.Name,
            ),
        )
    }

    @Test
    fun `file refresh preserves the selected sort`() {
        val selected = FileSort(FileSortColumn.Progress, SortDirection.Descending)
        val refreshed = DetailUiState(
            files = listOf(file(1, "Old", progress = 0.1)),
            fileSort = selected,
        ).copy(
            files = listOf(
                file(2, "Low", progress = 0.2),
                file(3, "High", progress = 0.8),
            )
        )

        assertEquals(selected, refreshed.fileSort)
        assertEquals(listOf(3, 2), refreshed.sortedFiles.map { it.index })
    }

    private fun file(
        index: Int,
        name: String,
        size: Long = 0,
        progress: Double = 0.0,
        priority: Int = 0,
    ) = TorrentFile(
        index = index,
        name = name,
        size = size,
        progress = progress,
        priority = priority,
    )
}
```

- [ ] **Step 2: Run the focused test and verify red state**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.detail.FileSortTest"
```

Expected: compilation fails because the file sort types and state members are absent.

- [ ] **Step 3: Implement deterministic pure sorting**

Create `FileSort.kt`:

```kotlin
/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentFile
import java.util.Locale

enum class FileSortColumn { Name, Size, Progress, Priority }

enum class SortDirection { Ascending, Descending }

data class FileSort(
    val column: FileSortColumn = FileSortColumn.Name,
    val direction: SortDirection = SortDirection.Ascending,
)

fun toggleFileSort(current: FileSort, column: FileSortColumn): FileSort {
    if (current.column == column) {
        val direction = when (current.direction) {
            SortDirection.Ascending -> SortDirection.Descending
            SortDirection.Descending -> SortDirection.Ascending
        }
        return current.copy(direction = direction)
    }

    return FileSort(
        column = column,
        direction = if (column == FileSortColumn.Name) {
            SortDirection.Ascending
        } else {
            SortDirection.Descending
        },
    )
}

fun sortTorrentFiles(files: List<TorrentFile>, sort: FileSort): List<TorrentFile> {
    val primary = when (sort.column) {
        FileSortColumn.Name -> compareBy<TorrentFile> { it.name.lowercase(Locale.ROOT) }
        FileSortColumn.Size -> compareBy(TorrentFile::size)
        FileSortColumn.Progress -> compareBy(TorrentFile::progress)
        FileSortColumn.Priority -> compareBy(TorrentFile::priority)
    }
    val directed = if (sort.direction == SortDirection.Ascending) {
        primary
    } else {
        primary.reversed()
    }
    val deterministicTieBreak = compareBy<TorrentFile>(
        { it.name.lowercase(Locale.ROOT) },
        TorrentFile::name,
        TorrentFile::index,
    )
    return files.sortedWith(directed.then(deterministicTieBreak))
}
```

- [ ] **Step 4: Preserve sorting in detail state and polling**

Add to `DetailUiState`:

```kotlin
val fileSort: FileSort = FileSort(),
```

Add inside its body:

```kotlin
val sortedFiles: List<TorrentFile> get() = sortTorrentFiles(files, fileSort)
```

Add to `TorrentDetailViewModel`:

```kotlin
fun toggleFileSort(column: FileSortColumn) {
    _state.update { state ->
        state.copy(fileSort = toggleFileSort(state.fileSort, column))
    }
}
```

Leave the Content polling update as `it.copy(files = list)` so `fileSort` survives every refresh and is reset only with the ViewModel lifecycle.

- [ ] **Step 5: Add the fixed sorting control row above the scrollable file list**

Change the Content call to:

```kotlin
DetailTab.Content -> ContentTab(
    files = state.sortedFiles,
    sort = state.fileSort,
    onSortChange = viewModel::toggleFileSort,
    onSetPriority = viewModel::setFilePriority,
)
```

Change `ContentTab` to accept `sort: FileSort` and `onSortChange: (FileSortColumn) -> Unit`. Wrap its list in a `Column(Modifier.fillMaxSize())`, then place this row before a `LazyColumn(Modifier.weight(1f))`:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    FILE_SORT_OPTIONS.forEach { (column, labelRes) ->
        val selected = sort.column == column
        val directionLabel = stringResource(
            if (sort.direction == SortDirection.Ascending) {
                R.string.sort_ascending
            } else {
                R.string.sort_descending
            }
        )
        FilterChip(
            selected = selected,
            onClick = { onSortChange(column) },
            label = { Text(stringResource(labelRes)) },
            leadingIcon = if (selected) {
                {
                    Icon(
                        imageVector = if (sort.direction == SortDirection.Ascending) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = directionLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                null
            },
        )
    }
}
```

Add the exact option list at file scope:

```kotlin
private val FILE_SORT_OPTIONS = listOf(
    FileSortColumn.Name to R.string.sort_name,
    FileSortColumn.Size to R.string.sort_size,
    FileSortColumn.Progress to R.string.sort_progress,
    FileSortColumn.Priority to R.string.sort_priority,
)
```

Keep the existing empty state before the wrapping `Column`; use `items(files, key = TorrentFile::index)` for stable row identity.

- [ ] **Step 6: Run focused tests and compile the Compose screen**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.qui.android.ui.detail.FileSortTest"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: all sort tests pass, replacing only the `files` field preserves the selected sort, and the Compose imports/signatures compile.

- [ ] **Step 7: Commit content sorting**

```powershell
git add app/src/main/java/dev/qui/android/ui/detail/FileSort.kt app/src/main/java/dev/qui/android/ui/detail/TorrentDetailViewModel.kt app/src/main/java/dev/qui/android/ui/detail/TorrentDetailScreen.kt app/src/test/java/dev/qui/android/ui/detail/FileSortTest.kt
git commit -m "feat: sort torrent content files"
```

### Task 5: Traditional Chinese and User Documentation

**Files:**
- Modify: `tools/generate_translations.py`
- Modify: `tools/translations_overrides.json`
- Modify: `tools/check_translations.py`
- Modify: `app/src/main/java/dev/qui/android/ui/AppLocale.kt`
- Modify: `app/src/main/res/xml/locales_config.xml`
- Create: `app/src/main/res/values-zh-rTW/strings.xml`
- Regenerate: all eight existing translated `strings.xml` files.
- Modify: `README.md`, `README.zh-CN.md`, `CHANGELOG.md`, `CHANGELOG.zh-CN.md`.

**Interfaces:**
- Consumes: QUI upstream `web/src/i18n/locales/zh-TW`, all 396 English app resources, and Appendix A below.
- Produces: language tag `zh-TW`, Android qualifier `values-zh-rTW`, endonym `繁體中文`, and nine complete non-English locale files.

- [ ] **Step 1: Add locale plumbing and prove the untranslated gap**

Add `("zh-TW", "values-zh-rTW")` immediately after `zh-CN` in `LANGUAGES`. Add `"zh-TW"` after `"zh-CN"` in `SUPPORTED_LANGUAGES`, add `"zh-TW" to "繁體中文"` in `LANGUAGE_NAMES`, and add `<locale android:name="zh-TW" />` after `zh-CN` in `locales_config.xml`. Update nearby comments/docstrings from nine total/eight non-English languages to ten total/nine non-English languages.

Run before adding Appendix A values:

```powershell
$env:PYTHONDONTWRITEBYTECODE = "1"
python tools/generate_translations.py C:\Users\23686\AppData\Local\Temp\codex-qui-upstream-20260904 --report
```

Expected: existing languages remain complete; `zh-TW` reports 137 missing keys and exits with code 1.

- [ ] **Step 2: Add core, Dashboard, delete and detail overrides**

For each key in Appendix A groups 1 and 2, add the exact `"zh-TW"` value to its existing JSON object. For plural resources, add a language object containing the exact `other` form.

- [ ] **Step 3: Add login, search and Settings overrides**

For each key in Appendix A group 3, add the exact `"zh-TW"` value. Preserve `%1$d`, `%1$s`, punctuation, and escaped newline sequences exactly.

- [ ] **Step 4: Add sort, storage, torrent and update overrides**

For each key in Appendix A group 4, add the exact `"zh-TW"` value. Keep `release_notes_heading` equal to `简体中文`: it is an internal parser sentinel for the only Chinese section currently emitted by the release workflow, not a visible language name.

- [ ] **Step 5: Add widget overrides and update override metadata**

For each key in Appendix A group 5, add the exact `"zh-TW"` value. Update `_comment` to list `zh-TW` and describe nine non-English locales.

- [ ] **Step 6: Generate and validate all translations**

Run:

```powershell
$env:PYTHONDONTWRITEBYTECODE = "1"
python tools/generate_translations.py C:\Users\23686\AppData\Local\Temp\codex-qui-upstream-20260904 --report
python tools/generate_translations.py C:\Users\23686\AppData\Local\Temp\codex-qui-upstream-20260904
python tools/check_translations.py
```

Expected: every language reports `396/396`, `values-zh-rTW/strings.xml` is created, and the checker reports `9 locales, 396 strings each - all present.`

- [ ] **Step 7: Update English and Simplified Chinese documentation**

Apply these content changes without changing the app version:

- README feature list: Dashboard now includes the server statistics card; Torrent details now include I2P-safe Peer addresses and content sorting by name/size/progress/priority; language count changes from nine to ten.
- README Languages section: list `en`, `cs`, `de`, `fr`, `it`, `ko`, `pt-BR`, `uk`, `zh-CN`, `zh-TW`; Translations says the other nine locales are generated.
- Chinese README mirrors those facts using “服务器统计”“I2P 节点地址”“名称/大小/进度/优先级排序”和“十种语言”。
- Add this section above 0.4.5 in `CHANGELOG.md`:

```markdown
## Unreleased

### Added
- A collapsible Server Statistics card on the Dashboard, with per-instance session and
  all-time transfer totals, share ratios and peer counts.
- Content-file sorting by name, size, progress and priority, matching qui's current
  defaults and direction toggles.
- Traditional Chinese (`zh-TW`), bringing the app back in sync with qui's ten locales.

### Fixed
- I2P peers now show their canonical `.b32.i2p` address instead of `:0`; IPv4 and IPv6
  fallbacks remain available for older responses.
```

- Add this matching section above 0.4.5 in `CHANGELOG.zh-CN.md`:

```markdown
## 尚未发布

### 新增
- 仪表盘新增可折叠的“服务器统计”卡片，可查看各实例的本次/累计传输量、分享率和节点数。
- 内容文件可按名称、大小、进度和优先级排序，默认方向及切换规则与 qui 当前版本一致。
- 新增繁体中文（`zh-TW`），应用重新与 qui 的十种语言保持一致。

### 修复
- I2P 节点会显示规范的 `.b32.i2p` 地址，不再错误显示为 `:0`；旧版响应仍可回退显示
  IPv4 或 IPv6 地址。
```

- [ ] **Step 8: Verify locale resources and build**

Run:

```powershell
python tools/check_translations.py
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

Expected: 9 translated locale directories and 396 entries per locale, all JVM tests pass, Debug build succeeds, and no whitespace errors are reported.

- [ ] **Step 9: Commit language and documentation support**

```powershell
git add tools/generate_translations.py tools/translations_overrides.json tools/check_translations.py app/src/main/java/dev/qui/android/ui/AppLocale.kt app/src/main/res/xml/locales_config.xml app/src/main/res/values-*/strings.xml README.md README.zh-CN.md CHANGELOG.md CHANGELOG.zh-CN.md
git commit -m "feat: add Traditional Chinese locale"
```

## Appendix A: Exact `zh-TW` Override Values

### Group 1: Queue, add, common and Dashboard

```text
action_bottom_of_queue = 移到佇列末尾
action_more = 更多操作
action_top_of_queue = 移到佇列首位
add_choose_files = 選擇 .torrent 檔案
add_first_last_piece = 優先下載首尾區塊
add_magnet_or_url = Magnet 連結或 URL
add_to_qui = 新增至 qui
common_none = （無）
common_or_type_new = 或輸入新的
dashboard_active_count = %1$d 個活躍
dashboard_all_clients = 所有用戶端
dashboard_all_time = 累計
dashboard_alt_speed_limits = 備用速度限制
dashboard_hide_address = 隱藏位址
dashboard_no_instances_message = 在 qui 中新增 qBittorrent 實例後，它會顯示在這裡。
dashboard_no_instances_title = 尚無用戶端
dashboard_no_tracker_data = 尚無 Tracker 資料。
dashboard_session = 本次
dashboard_show_address = 顯示位址
```

### Group 2: Delete, detail, filters and instances

```text
delete_body_keep_files = 種子將從 qBittorrent 中移除，已下載的檔案仍保留在磁碟上。
delete_body_with_files = 已下載的檔案將從磁碟永久刪除，此操作無法復原。
delete_title_many = 刪除 %1$d 個種子？
delete_title_one = 刪除此種子？
detail_no_peers = 沒有已連線的節點
detail_no_trackers = 沒有 Tracker
detail_reannounce_in = 距下次通報
detail_x_of_y = %1$d / %2$d
filters_hint = 輕觸項目以包含，輕觸減號以排除。
instances_active = {"other":"%1$d 個活躍用戶端"}
instances_not_reachable = 無法連線
instances_selected = 已選取
```

### Group 3: Login, search and Settings

```text
login_api_key_hint = 在 qui 的「設定 → API 金鑰」中建立金鑰。金鑰不會過期，應用程式會保持登入狀態。
login_error_credentials = 認證資訊不正確
login_error_forbidden = 伺服器拒絕存取
login_error_generic = 發生錯誤
login_error_host = 無法連線到該主機
login_error_no_address = 請輸入 qui 伺服器位址
login_error_not_qui = 該位址似乎不是 qui 伺服器
login_error_tls = TLS 憑證遭拒——如果這是您自己的伺服器，請開啟「信任自簽憑證」
login_error_unreachable = 無法連線到伺服器——請檢查位址以及服務是否正在執行
login_hide_password = 隱藏密碼
login_probe_ok = 已連線，請在下方登入。
login_probe_setup_required = 該 qui 伺服器尚未建立帳戶——請在下方建立。
login_server_address = 伺服器位址
login_show_password = 顯示密碼
login_subtitle = 連線到您的 qui 伺服器
login_toggle_key_visibility = 切換金鑰顯示
login_trust_certs = 信任自簽憑證
login_trust_certs_hint = 僅在您完全掌控的伺服器上啟用。
scope_back_to_top = 回到頂端
search_clear_history = 清除搜尋記錄
search_fuzzy_example = 輸入「breaking bad」可符合「Breaking.Bad」
search_recent = 最近
selection_count = {"other":"已選取 %1$d 項"}
settings_about = 關於
settings_account = 帳戶
settings_behaviour = 行為
settings_confirm_delete = 刪除前確認
settings_confirm_delete_hint = 移除種子前先詢問。
settings_dynamic_color = 使用系統配色
settings_dynamic_color_hint = 跟隨 Android 桌布擷取色彩，而不使用 qui 主題。
settings_incognito = 隱身模式
settings_incognito_hint = 將名稱、分類和 Tracker 替換為 Linux ISO。
settings_language_system = 跟隨系統
settings_selected = 已選取
settings_sign_out_body = 將刪除此伺服器儲存的認證資訊。
settings_sign_out_title = 登出？
settings_signed_in = 已登入
settings_source_code = 原始碼
settings_speed_units = 速度單位
settings_widgets = 小工具
```

### Group 4: Sort, storage, torrents and updates

```text
release_notes_heading = 简体中文
sort_added_on = 最近新增
sort_downloaded_session = 本次下載量
sort_reannounce = 距下次通報
sort_seen_complete = 上次完整可見
sort_uploaded_session = 本次上傳量
speed_limits_hint = 單位為 KiB/s。留白表示不變更該項限制，輸入 0 表示不限制速度。
storage_cleared = 已清除
storage_entries = %1$d 項
storage_tracker_icons = Tracker 圖示
torrents_count = {"other":"%1$d 個種子"}
torrents_empty_filtered = 沒有符合目前搜尋和篩選條件的內容。
torrents_empty_message = 此用戶端尚無種子。
torrents_empty_title = 沒有種子
torrents_live = 即時
torrents_loaded_of = %1$d / %2$d
torrents_no_clients_message = 請先在 qui 中新增 qBittorrent 實例，然後向下拉動重新整理。
torrents_partial_results = 有用戶端未回應，清單不完整
torrents_polling = 輪詢中
torrents_select_client = 選擇用戶端
tracker_status_not_working = 無法運作
update_auto_check = 啟動時檢查更新
update_auto_check_note = 啟動應用程式時檢查是否有新版本。不會自動下載任何內容。
update_check = 檢查更新
update_from_to = %1$s → %2$s
update_later = 稍後
update_no_notes = 此版本沒有更新說明。
update_notes = 更新內容
update_open = 前往下載
update_qui_server = qui 伺服器有新版本
update_skip = 略過此版本
update_version = 版本 %1$s
```

### Group 5: Widgets

```text
widget_add = 新增
widget_description = 4×2 — 速度、四種狀態計數、總大小和可用空間。
widget_error_http = 伺服器錯誤（HTTP %1$s）
widget_error_no_response = 沒有用戶端回應
widget_error_offline = 沒有網路連線
widget_error_timeout = 伺服器回應逾時
widget_error_unauthorized = 請重新登入 —— 伺服器拒絕了已儲存的認證資訊
widget_free_space = 剩餘 %1$s
widget_incognito_note = 小工具上的種子名稱會跟隨應用程式的隱身模式開關。
widget_interval = 背景重新整理
widget_interval_hour = 1 小時
widget_interval_minutes = %1$d 分鐘
widget_interval_note = Android 最快每 15 分鐘才會執行一次背景工作。無論此處設定多少，開啟應用程式或輕觸小工具上的重新整理按鈕都會立即更新。
widget_interval_off = 關閉
widget_list_active = 傳輸中優先
widget_list_content = 傳輸清單
widget_list_recent = 最近新增
widget_name = qui 統計
widget_no_active = 目前沒有傳輸中的種子
widget_not_signed_in = 請先登入 qui
widget_overview_description = 2×2 — 速度加上下載中和做種數量。
widget_overview_name = qui 概覽
widget_pin_added = 已新增至主畫面
widget_pin_dismiss = 知道了
widget_pin_no_response = 如果主畫面沒有彈出確認視窗，通常是缺少「建立桌面捷徑」權限。小米上的路徑是：設定 → 應用程式設定 → qui → 權限管理 → 桌面捷徑。\n\n您也可以長按主畫面，從小工具清單中新增 qui。
widget_pin_no_response_title = 沒有任何反應？
widget_pin_open_settings = 應用程式設定
widget_pin_requested = 請在主畫面上確認以完成新增。
widget_pin_unsupported = 目前的主畫面不支援從應用程式內新增小工具。請長按主畫面，從小工具清單中選擇 qui。
widget_refresh = 重新整理
widget_refresh_note = 澎湃 OS、MIUI 等系統經常直接略過背景工作。如果小工具不再更新，請為 qui 開啟自動啟動，並將省電策略設為不限制。
widget_refreshing = 重新整理中……
widget_scope = 用戶端
widget_scope_all = 所有活躍用戶端
widget_speed_description = 2×1 — 總下載和上傳速度。
widget_speed_name = qui 速度
widget_stale = 最後更新於 %1$s
widget_torrents_description = 4×4 — 目前正在傳輸的種子及進度。輕觸任一列可直接開啟。
widget_torrents_name = qui 傳輸中
```

## Final Verification and Review

- [ ] **Step 1: Invoke `superpowers:requesting-code-review` and review the complete branch diff against the spec**

The review must explicitly confirm: no new API request; failed/empty instances excluded; weighted ratio; nullable Peer distinction; selected Bottom Sheet row closes after refresh removal; all four sort columns and direction defaults; ten locale tags; no excluded server-management feature.

- [ ] **Step 2: Run the complete verification suite from a clean Gradle invocation**

```powershell
$env:PYTHONDONTWRITEBYTECODE = "1"
python tools/check_translations.py
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
git diff --check
```

Expected: translation checker reports 9 locales × 396 resources, all JVM tests pass, Android lint has no errors, `assembleDebug` succeeds, and `git diff --check` prints nothing.

- [ ] **Step 3: Inspect final artifacts and history**

```powershell
Get-Item app/build/outputs/apk/debug/app-debug.apk | Select-Object FullName,Length,LastWriteTime
git log --oneline --decorate -6
git status --short
```

Expected: the Debug APK exists; history contains the four feature commits after the design/plan commits; no tracked changes remain. Planning-memory files from the primary checkout are not copied or committed into the feature branch.

- [ ] **Step 4: Invoke `superpowers:finishing-a-development-branch`**

Present the verified branch integration options without merging, pushing, or deleting a worktree unless the user selects that action.
