# FTB RS Bridge

FTB RS Bridge 是一个面向 Minecraft 1.20.1 Forge 整合包的服务器侧兼容 Mod。
它把玩家直接携带的 Refined Storage 无线终端所能访问的网络物品，以及直接携带的 Sophisticated Backpacks 物品，接入 FTB Quests 的物品判定和原版 `minecraft:inventory_changed` Advancement 判定。

当前发布版本是 `1.0.6`，最低兼容 FTB Quests `2001.4.11`，并已用 `2001.4.22` 回归。

## 功能

- 支持 Refined Storage 无线终端、创造无线终端、无线流体终端、创造无线流体终端、无线合成终端和创造无线合成终端。
- 只从玩家身上直接携带的终端读取绑定网络，不扫描附近网络、末影箱或嵌套容器。
- 支持 RS 网络的维度、距离、电量、运行状态和 `MODIFY`/`EXTRACT` 权限检查。
- 支持 RSInfinityBooster 的 Infinity Card 和 Dimension Card 行为。
- 支持 Sophisticated Backpacks `1.20.1-3.24.35.1675` 与 Sophisticated Core `1.20.1-1.3.21.1676` 的软兼容。
- 精妙背包读取主存储格和升级模块槽；不读取模块内部库存、过滤器缓存、合成缓存、流体或能量，也不递归读取嵌套背包。
- 普通非消耗 FTB `ItemTask` 的进度单调增加，完成后不再轮询。
- 可重复非消耗任务完全交还 FTB Quests 原生玩家主物品栏逻辑，避免网络库存引发重复奖励循环。
- 消耗任务只有在玩家手动点击 FTB Quests 提交按钮后才会处理；轮询不会自动提交或扣除物品。
- 原版 `inventory_changed` Advancement、挑战和成就可以读取背包与可访问 RS 网络中的物品，但不会消耗网络物品。
- 默认每 60 tick（约 3 秒）轮询，并使用共享快照、Item 索引、增量 Advancement 状态和指纹跳过降低服务端开销。

## 版本要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | `1.20.1` |
| Forge | `[47.3.0,48)` |
| Java | `17` |
| FTB Quests | `2001.4.11` 及以上；已验证 `2001.4.11`、`2001.4.22` |
| FTB Library | `2001.2.12` |
| FTB Teams | `2001.3.1` |
| Item Filters | `2001.1.0-build.59` |
| Refined Storage | `1.12.4` |
| Refined Storage Addons | `0.10.0` |
| RSInfinityBooster | `1.20.1-1.0+41`，可选软兼容 |
| Sophisticated Backpacks/Core | `1.20.1-3.24.35.1675` / `1.3.21.1676`，可选软兼容 |
| Curios | 可选软兼容 |

Sophisticated Backpacks/Core 缺失或 API 不兼容时，只禁用背包适配，RS、FTB Quests 和 Advancement 桥接仍然加载。

## 安装

1. 确认客户端或独立服务器使用 Minecraft 1.20.1、Forge 47.3.0 或更高的 47.x 版本和 Java 17。
2. 安装上表中的必需依赖。
3. 从 [`dist/ftbrsbridge-1.0.6.jar`](dist/ftbrsbridge-1.0.6.jar) 下载 Mod，放入整合包的 `mods/`。
4. 单人游戏把 JAR 放进客户端整合包；独立服务器只需要服务端安装，普通客户端不需要安装本 Mod。
5. 首次启动后会生成 `config/ftbrsbridge-server.toml`。

## 配置

```toml
[scanning]
scanIntervalTicks = 60
debugLogging = false
```

- `scanIntervalTicks`：被动扫描间隔，范围 `1`–`1200` tick，默认 `60`。
- `debugLogging`：输出限流诊断日志，默认 `false`。

配置改动在服务器重启后生效。

## 任务与物品来源边界

普通非消耗任务和 `inventory_changed` Advancement 可以合并玩家主物品栏、精妙背包贡献和可访问 RS 网络。

可重复非消耗任务只使用 FTB Quests 原生玩家主物品栏判定。精妙背包和 RS 网络不会推进这类任务，也不会由本 Mod 保存额外的闩锁状态。

消耗任务的手动提交顺序是：玩家主物品栏、精妙背包主存储、RS 网络、精妙背包升级模块槽。提交前会先模拟总量；数量不足或任一来源异常时不修改任务和库存，并执行回滚保护。

## 构建

项目默认使用 FTB Quests `2001.4.11` 编译基线：

```powershell
.\gradlew.bat clean test build
```

测试较新的 FTB Quests `2001.4.22`：

```powershell
.\gradlew.bat clean test build "-PftbQuestsVersion=2001.4.22"
```

构建产物输出到 `build/libs/`。发布 JAR 和校验文件位于 `dist/`。

当前发布件 SHA-256 记录在 [`dist/SHA256SUMS.txt`](dist/SHA256SUMS.txt)。

当前验证结果：19 个 JUnit 测试通过；默认 `.11` 和覆盖 `.22` 均完成编译与测试；两个版本都在 Forge 47.3.0 userdev 中启动到 `Done`，required Mixin 加载成功。

## 项目结构

```text
src/main/java/       Mod、RS 网络访问、FTB 任务、Advancement 和背包适配
src/main/resources/  mods.toml、Mixin 配置和 pack.mcmeta
src/test/java/       进度、提交策略、虚拟库存、索引和增量扫描测试
libs/                本地 ForgeGradle 编译依赖
dist/                当前发布 JAR 与 SHA256SUMS.txt
```

## 排查

如果网络物品没有推进任务，依次检查：

1. 终端是否为支持的六个 ID，且直接位于玩家槽位中。
2. 终端是否已绑定，普通终端电量是否达到正常打开门槛。
3. 网络和无线发射器是否运行。
4. 玩家是否同时拥有 `MODIFY` 与 `EXTRACT` 权限。
5. 当前维度、距离和 Infinity/Dimension Card 是否满足 RS 规则。
6. 任务是否是可判定的普通非消耗 `ItemTask`，消耗任务是否由玩家手动点击提交。
7. 临时将 `debugLogging` 改为 `true`，查看服务端日志中的阶段耗时和来源诊断。

## 卸载与回滚

1. 停止服务器。
2. 从 `mods/` 删除 `ftbrsbridge-1.0.6.jar`。
3. 可选删除 `config/ftbrsbridge-server.toml`。
4. 重新启动服务器。

卸载后 FTB Quests 恢复原生库存判定。已经保存的任务进度不会自动下降，消耗任务已经扣除的物品也不会自动返还。

源码中的类名、测试和配置文件是当前实现的行为依据。

## 许可证

本项目使用 MIT License，见 [`LICENSE`](LICENSE)。
