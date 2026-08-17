# 更新记录

## [2.0.2] - 2026-08-17

### 修复

- Presence 有效响应仍会立即更新缓存；`ONLINE` 缓存 30 秒，离线状态及请求超时、没有消息载体或发送失败产生的 `UNKNOWN` 缓存缩短为 5 秒，避免领主刚上线或短暂代理通信异常时公共回能长时间使用慢速值。
- 保留同一 UUID 的在途请求去重，避免缩短未知缓存后产生请求风暴。

### 兼容性影响

- Velocity/Purpur 两端版本统一为 `2.0.2`，Presence 协议和断开历史文件格式不变。
- MMMFlight 可选集成文档与编译目标更新为 `1.11.2`。

### 升级注意事项

- 必须同时替换 Velocity 端和全部 Purpur 端 JAR 后完整重启；无需手动修改配置或迁移数据。

## [2.0.1] - 2026-08-16

### 修复

- 修正 Shade 构建配置，Velocity 成品只保留可直接部署的 `mmm-velocity-bridge-velocity-2.0.1.jar`，不再生成 `original-*` 副本。

### 升级注意事项

- Purpur 与 Velocity 两端均需替换为 `2.0.1`，协议和数据格式无需迁移。

## [2.0.0] - 2026-08-15

### 新增

- 新增按 UUID 查询代理玩家 Presence 的版本化协议。
- 新增 MMMFlight `ProxyPresenceService` 实现、30 秒缓存和并发请求去重。
- 新增 Velocity 断开时间持久化与七天历史清理。
- 新增 `%mmmvelocitybridge_online%` 和 `%mmmvelocitybridge_maxplayers%`。

### 调整

- 工程、Maven artifact、JAR 和 Purpur 插件名统一为 MMMVelocityBridge。
- 依赖改为 Maven 仓库解析，不再使用本机绝对 `systemPath`。
- PlaceholderAPI 改为可选依赖，同时保留旧 `mmmproxy` 变量。
- 保留 `mmm:bridge` 和旧统计消息格式，兼容旧统计后端。

### 兼容性影响

- 新旧桥接插件不能同时加载；部署时应成套替换 Velocity 与 Purpur JAR。
- 旧 PlaceholderAPI 变量和 Purpur 依赖名称继续可用；旧 JAR 文件名和下载脚本需要更新。
- Presence 功能需要所有桥接端及 MMMFlight 1.11.0 更新，未更新的一端会安全降级为 `UNKNOWN`。

### 升级注意事项

- 先更新并启动 Velocity，再更新全部 Purpur 后端，最后启用依赖 Presence 的业务插件。
- 本版本不迁移旧数据；无需手工修改配置。

## [1.0.0]

- 初始统计桥接版本，提供 `stats_request`/`stats_response` 和 `mmmproxy` 占位符。
