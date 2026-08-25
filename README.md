# MMMVelocityBridge

MMMVelocityBridge 在 Velocity 与 Purpur 之间提供代理统计和跨服玩家在线状态桥接。作者：Xiaomenxin。当前组件版本：Velocity `2.0.3`，Purpur `2.0.4`。

- [更新记录](CHANGELOG.md)
- [技术指南](TECHNICAL_GUIDE.md)

## 能力

- 保留全网在线人数和最大人数统计。
- 通过 `ProxyPresenceService` 异步查询指定 UUID 的代理在线状态。
- 记录真正断开代理的时间；玩家切换后端服不会被判定为离线。
- Presence 缺失、超时、畸形或无在线消息载体时安全返回 `UNKNOWN`。

本插件不实现 Residence、MMMFlight 回能、飞行点数、商店或数据库业务。

## 运行要求

- Java 21。
- Velocity 3.5.x 快照系列。
- Purpur 1.21.11 服务端。
- Velocity 端和每个需要服务的 Purpur 后端必须安装对应的新 JAR。
- PlaceholderAPI 为可选依赖；缺失时 Presence 服务仍可用，只跳过统计占位符。
- MMMFlight 1.12.4 为可选集成；安装后桥接会注册其公开 Presence 服务，未安装时统计桥接仍可用。

## 安装与升级

1. 停止代理和后端服务，备份插件目录及代理数据目录。
2. 删除旧的 `MMMVelocityBridge`、`MMMProxyStats-Purpur` JAR，不要与新插件同时加载。
3. 将 `mmm-velocity-bridge-velocity-2.0.3.jar` 放入 Velocity 的 `plugins` 目录。
4. 将 `mmm-velocity-bridge-purpur-2.0.4.jar` 放入每个 Purpur 的 `plugins` 目录。
5. 启动顺序为 Velocity、各 Purpur 桥接、再启动依赖 Presence 的业务插件。

本版本不迁移旧数据；新代理数据文件会在插件数据目录中创建为 `last-disconnects.json`。更新前请保留备份，回滚时必须成套回滚两端 JAR。

## PlaceholderAPI

新变量为 `%mmmvelocitybridge_online%` 和 `%mmmvelocitybridge_maxplayers%`。旧变量 `%mmmproxy_online%` 和 `%mmmproxy_maxplayers%` 继续提供，方便已有计分板和菜单无缝运行。

## Presence 服务

当 MMMFlight 1.12.4 存在时，Purpur 端通过 Bukkit `ServicesManager` 注册其 `local.mmm.flight.api.ProxyPresenceService`。`lookup(UUID)` 在命中缓存时立即完成 Future；首次查询或过期刷新时返回等待代理响应的 Future，同一 UUID 只发起一个去重请求。已确认的 `ONLINE` 在刷新期间保留，响应、主动推送或超时后原子替换缓存。状态包括 `ONLINE`、`OFFLINE_RECENT`、`OFFLINE_LONG` 和 `UNKNOWN`；`ONLINE` 缓存 30 秒，离线或未知状态只缓存 5 秒。每次接受更新序列号更大的代理推送后，Purpur 端还会向 MMMFlight 发布一次 Presence 变化事件，使回能速率立即重新计算；不增加周期轮询。

没有在线玩家时无法发送 Plugin Message，调用方应按 `UNKNOWN` 安全降级。

## 权限与对外接口

本插件不注册命令，也没有权限节点。对外接口包括上述 PlaceholderAPI 变量，以及由 MMMFlight 定义、桥接注册实现的 Bukkit `ProxyPresenceService`；接入插件通过 `ServicesManager#load` 获取服务，服务未注册时必须按 `UNKNOWN` 安全降级。

| 服务方法 | 返回与状态语义 |
| --- | --- |
| `ProxyPresenceService#lookup(UUID)` | 异步返回 `CompletableFuture<ProxyPresence>`；缓存命中立即完成，首次/刷新查询等待匹配的代理响应或主动推送；`ONLINE`、`OFFLINE_RECENT`、`OFFLINE_LONG`、`UNKNOWN` 分别表示全局在线、最近断开、长期断开和无法确认。 |

## 配置、命令与排障

本插件没有管理员命令和用户配置。Velocity 数据文件为 `last-disconnects.json`，只保存七天内的断开记录，写入使用后台防抖和原子替换。Presence 主动推送只在登录完成、切服和断开事件触发，不增加周期轮询。

排障顺序：确认两端 JAR 版本一致；确认 `mmm:bridge` 未被网络或其他插件拦截；确认后端有在线玩家作为消息载体；检查代理和后端启动日志中的解析、发送和持久化警告。Paper 的 `/reload` 不作为升级方式，应完整重启服务。
