# 航空级铁砧（Aerospace-grade Anvil）

航空级铁砧为铁砧工艺的机器、下落方块、天体引力与 Sable 动态结构提供兼容。

当前开发目标为 Minecraft 1.21.1、Java 21 和 NeoForge。26.1.2 移植等待 Sable、机械动力与航空学发布对应版本。

构建命令为 `./gradlew.bat build --console=plain`，客户端测试使用 `./gradlew.bat runClient --console=plain`，服务端游戏测试使用 `./gradlew.bat runGameTestServer --console=plain`。

依赖版本固定在 `gradle/libs.versions.toml`：铁砧工艺 `1.6.0+snapshot.2842`、Sable `2.0.6`、机械动力 `6.0.10`、航空学 `1.3.2`，均使用 Modrinth 官方发行文件。机械动力与航空学加入开发运行环境；兼容实现直接依赖铁砧工艺与 Sable。

构建结构参考 [Anvil-Dev 附属模板](https://github.com/Anvil-Dev/AnvilCraftAddonTemplate/tree/java) 与 [铁砧工艺](https://github.com/Anvil-Dev/AnvilCraft)。Gradle Wrapper 和 `style.xml` 来自铁砧工艺仓库；Wrapper 保留 Apache 2.0 许可声明，`style.xml` 保留其作者声明与规则。

功能包括：

- 复用铁砧工艺的磁铁悬停与红石释放规则，保持结构内被磁铁吸住的铁砧。
- 下落方块离开结构时继承该点的线速度、旋转产生的切向速度与模型朝向，随后使用普通世界物理运动。
- 动态结构磁铁持续检查自身局部吸附柱，可捕获普通世界中的铁砧实体，并保留遮挡、距离、非磁性和占用限制。
- 吸附动画跟随结构渲染姿态；铁砧落地或被捕获时按目标坐标系换算方块朝向。

- 物品探测器按随船旋转的范围统计普通世界、本船和其他飞艇中的物品与容器，保留过滤和信号规则。
- 腐化信标的光束与作用范围随船旋转，识别世界及其他飞艇的障碍，保留原有透明方块和腐化配方规则。
- 滑轨搬运结构保留在飞艇局部空间；动力滑轨、探测滑轨、激活滑轨与滑轨站正确处理世界实体的方向和传送索引。
- 天体引力作用于动态结构质心，船载引力源随船移动和旋转，影响其他飞艇并排除自身牵引。
- 类主世界维度显式提供气压基准，沿用 Sable 的高度曲线；月球和虚空星球沿用铁砧工艺已有的零气压数据。
- 同一刻拆装并复用结构存储区时读取当前区块，避免旧区块缓存使机器状态混入另一结构。

源码使用 [LGPL-3.0-or-later](LICENSE)，资源许可与铁砧工艺一致，见 [ASSETS_LICENSE](ASSETS_LICENSE)。

验证范围和结果见 [验证记录](docs/validation.md)。
