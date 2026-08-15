# 技术指南

## 技术栈与边界

- Java 21、Maven、多模块工程。
- `velocity-plugin` 面向 Velocity 3.5.x，使用 `velocity-api` 编译依赖。
- `paper-plugin` 面向 Purpur 1.21.11 API，运行时名称为 `MMMVelocityBridge-Purpur`。
- PlaceholderAPI 仅用于统计扩展；Presence 服务不依赖它。
- Plugin Message 必须经过在线玩家连接，因此 Purpur 没有在线玩家时不发送请求。

## 模块与入口

- `VelocityBridgePlugin` 注册 `mmm:bridge`、监听登录/断开和 Plugin Message。
- `PresenceStateResolver` 是 Presence 状态判定的唯一入口：代理在线优先，其次按断开时间区分最近/长期离线，缺少历史时返回 `UNKNOWN`。
- `PresenceHistory` 管理内存断开记录和后台持久化。
- `PaperBridgePlugin` 注册统计扩展、MMMFlight Presence 集成和消息监听器。
- `PresenceServiceProvider` 是 Purpur Presence 查询的唯一入口；`PresenceLookupCache` 负责 TTL 和请求去重。

## 协议与安全

频道固定为 `mmm:bridge`。旧统计帧保持原有 UTF 类型和两个整数格式。Presence 帧使用版本 `1`：类型、版本、请求 UUID、目标 UUID；响应再写入状态、断开 epoch 毫秒和当前服务器名。

Velocity 匹配频道后立即将消息标记为 handled，Presence 请求只接受 `ServerConnection` 来源。客户端来源不会参与 UUID 查询或响应。畸形数据、未知类型和发送失败只记录限频警告，不阻塞代理主线程。

## Presence 数据流

Velocity 使用 `ProxyServer#getPlayer(UUID)` 作为在线权威。`DisconnectEvent` 写入最后断开时间，服务器切换不会写入离线状态。查询时动态计算一小时边界，历史缺失返回 `UNKNOWN`。

断开历史保存为插件数据目录的 `last-disconnects.json`，根对象直接映射 UUID 字符串到断开 epoch 毫秒，例如 `{ "uuid": 1723686400000 }`。读取、查询和写入都会忽略超过七天的记录。内存映射使用 O(1) 查询；写入通过单线程防抖、快照复制、临时文件和原子替换完成。旧格式不迁移；文件损坏时记录警告并以空历史运行，管理员应先备份损坏文件，后续成功保存会写入新格式。

MMMFlight 已安装时，`MmmFlightPresenceIntegration` 通过其插件类加载器实现并注册 `local.mmm.flight.api.ProxyPresenceService`，不会复制或打包飞行插件 API。`lookup(UUID)` 只完成当前快照的 Future；未命中时写入无锁发送队列，由 Purpur 主线程任务发送，因此 MMMFlight 后台线程不会访问 Bukkit。相同 UUID 只允许一个在途请求。响应必须匹配请求 UUID 和目标 UUID，超时或无消息载体会缓存 `UNKNOWN`。

## 外部接入

MMMFlight 1.11.0 提供 `local.mmm.flight.api.ProxyPresenceService`，并通过 `ServicesManager#load` 获取桥接实现；服务不存在时按 `UNKNOWN` 处理。桥接仅在 MMMFlight 存在时注册该服务。调用方不得读取桥接私有缓存文件，也不得同步等待网络响应。

## 开发与验证

从工程根目录执行 `mvn test` 运行全部单元测试，执行 `mvn clean verify` 生成两个 JAR。测试覆盖状态边界、Presence v1 编解码、断开历史清理、缓存 TTL、并发去重和未知响应。发布前还需手工验证登录、切服、断开一小时边界、代理重启、无载体和客户端伪造消息场景。

## 维护风险

- Velocity 与 Purpur API 版本必须和目标服务端匹配。
- Bukkit 对象只能在主线程访问；网络结果不得阻塞主线程。
- `mmm:bridge` 是双方共享协议，修改旧统计帧必须先更新兼容性测试。
- 持久化异常只能降级为 `UNKNOWN`，不能伪造最近在线。
- 修改 Presence 状态或缓存语义时，必须同步检查 MMMFlight 的回能速率和部署顺序。
