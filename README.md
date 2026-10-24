NeoFabric
===========

## 关于本项目

NeoFabric 本质上就是一个图一乐的东西，基本上就是一个 Fabric。
若您发现此加载器与 Fabric 存在致命的一致性，请不要怀疑您的发现。

## 使用前须知

- 支持 Minecraft 1.20.1 至 1.20.6 的六个正式版本，均内置对应版本的 Fabric API，并通过独立 JVM 集成回归。
- Minecraft 1.20.1 至 1.20.4 使用 Java 17；1.20.5 和 1.20.6 使用 Java 21。
- 本加载器仅支持特别定制的 NeoFabric 模组（尽管一个都没有），请勿把普通的 Fabric 模组放入其中。
- 不需要单独安装 Fabric API，里面已内置，并按游戏版本选择。如果对应版本的内置 API 资源缺失，将直接停止启动，绝不回退使用 1.20.1 的 API。
- 本仓库发布的是 Loader；旧版 NeoFabric Installer 仍固定安装 Minecraft 1.20.1 和旧版 Loader，需要另行同步升级。
- 这东西本质是图一乐，若有报错，请谅解。

## 许可证

原项目 Fabric Loader 版权归 FabricMC 所有，遵循 Apache-2.0 许可证。
本项目保留原 LICENSE 和版权声明，修改部分同样遵循 Apache-2.0。
