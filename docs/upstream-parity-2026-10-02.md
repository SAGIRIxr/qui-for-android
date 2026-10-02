# 2026-10-02 qui 上游跟进

本次从 Android 0.5.6（2026-09-07）检查到上游最新稳定版
[v1.30.0（2026-09-21）](https://github.com/autobrr/qui/releases/tag/v1.30.0)，
并浏览 develop 截至 2026-10-01 的后续提交。Android 的实现依据固定在已发布协议；
本次 app 版本为 0.5.7。

| 上游变化 | Android 跟进 |
| --- | --- |
| v1.30.0 [会话认证加强 #2548](https://github.com/autobrr/qui/pull/2548) | 请求携带 `X-Requested-With: XMLHttpRequest`，保留调用方的 SSE Accept；所有返回原始 Response 的业务写操作检查 HTTP 状态并反馈错误。API key 优先级维持上游规则。 |
| v1.29.0 [流版本与基线校验 #2506](https://github.com/autobrr/qui/pull/2506) | 校验 `version` 与 `delta.baseVersion`；漏帧或基线不匹配时重连，期间 REST 轮询。无版本的旧服务端仍受支持。 |
| v1.29.0 [短暂错误后恢复快照 #2656](https://github.com/autobrr/qui/pull/2656) | 将 SSE 基线与 REST 显示结果分开；服务端流错误不丢弃基线，后续有效增量恢复完整列表。连接结束时允许重连，心跳不会将未初始化数据标为实时。 |
| 增量帧的汇总数据及省略语义 | 增量也刷新数量、速度、磁盘、翻页和部分结果状态；省略 counts 保留原值，省略 categories/tags 清空集合。 |
| v1.30.0 [Catalan #2682](https://github.com/autobrr/qui/pull/2682) | 增加 `ca`，复用稳定版译文并补全 Android 独有文案；设置和系统语言配置同步注册。 |

v1.29.0 的内容排序、统一视图按实例执行动作在 app 中已经实现。本次没有重复移植。
BDInfo、cross-seed、自动化、孤立文件扫描、SSH/SFTP 等需要服务端管理或文件系统能力的功能，
仍遵循 README 的客户端范围。服务器性能、安全配置和数据库迁移由 qui 升级提供。

后续开发分支还在推进远程文件系统、cross-seed 页面和本地化清理；这些尚未发布的能力
不作为 app 本次支持承诺。参见 [上游 develop](https://github.com/autobrr/qui/commits/develop/)。

验证结果：112 项 JVM 测试通过（新增 25 项认证、HTTP 失败、流版本与恢复回归），
6 项 Python 发布脚本测试通过，10 种非英语语言各 419 项完整性检查通过；
Android lint 无错误，Debug 与固定签名 Release APK 均构建成功。
APK 版本已核验为 0.5.7（18），签名与已有 0.5.6 安装一致。

Android 14 真机已覆盖安装并完成冒烟验收：

- 登录、语言、隐身及列表偏好在升级后保留。
- 连接现有 qui v1.26.0 的七个实例，仪表盘、统一列表、速度与剩余空间显示正常。
- 详情五个标签、内容排序、系统返回和添加面板打开/关闭可用。
- Català 的设置及列表文字、计数显示正确，验证后恢复原来的跟随系统语言。
- 强制停止后冷启动恢复原页面和连接，未观察到崩溃或 ANR。

真机验收未对实际种子执行写入操作。v1.29/v1.30 新协议的版本缺口恢复、
Cookie 写请求头和 HTTP 拒绝反馈由上述自动化回归覆盖，未在真实新版服务器上重复测试。
