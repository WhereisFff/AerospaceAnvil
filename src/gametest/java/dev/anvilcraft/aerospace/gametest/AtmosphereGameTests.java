package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 使用实际加载的维度物理记录验证 Sable 的高度气压和螺旋桨推力。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereGameTests {
    private AtmosphereGameTests() {
    }

    @GameTest(template = "empty")
    public static void overworldLikePressureWeakensThrustWithAltitude(GameTestHelper helper) {
        try (LoadedAtmosphere atmosphere = new LoadedAtmosphere(helper, "overworld_like")) {
            ServerLevel level = atmosphere.level;
            int seaLevel = level.getSeaLevel();
            PropellerProbe low = new PropellerProbe(level, new BlockPos(0, seaLevel, 0));
            PropellerProbe high = new PropellerProbe(level, new BlockPos(0, seaLevel + 120, 0));
            double lowPressure = low.getCurrentAirPressure();
            double highPressure = high.getCurrentAirPressure();
            helper.assertTrue(lowPressure > 0, "类主世界没有可供螺旋桨工作的气压");
            helper.assertTrue(highPressure > 0 && highPressure < lowPressure, "类主世界没有保留 Sable 的高度气压曲线");
            helper.assertTrue(Math.abs(low.getScaledThrust()) > Math.abs(high.getScaledThrust()), "高海拔未降低螺旋桨推力");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void munVacuumStopsPropellerThrust(GameTestHelper helper) {
        assertVacuumStopsThrust(helper, "mun");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void voidVacuumStopsPropellerThrust(GameTestHelper helper) {
        assertVacuumStopsThrust(helper, "void_planet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void higherPriorityPhysicsOverridesThrust(GameTestHelper helper) {
        try (LoadedAtmosphere atmosphere = new LoadedAtmosphere(helper, "overworld_like")) {
            ServerLevel level = atmosphere.level;
            PropellerProbe propeller = new PropellerProbe(level, new BlockPos(0, level.getSeaLevel(), 0));
            double originalThrust = Math.abs(propeller.getScaledThrust());
            helper.assertTrue(originalThrust > 0, "覆盖前的类主世界螺旋桨没有推力");
            DimensionPhysics vacuum = withPressure(atmosphere.physics, atmosphere.physics.priority() + 1, 0);
            DimensionPhysicsData.addPhysicsWithPriority(level.dimension(), vacuum);
            helper.assertTrue(propeller.getScaledThrust() == 0, "高优先级真空设置没有停止螺旋桨推力");
            DimensionPhysicsData.addPhysicsWithPriority(level.dimension(), atmosphere.physics);
            helper.assertTrue(propeller.getScaledThrust() == 0, "低优先级记录覆盖了高优先级真空设置");
            DimensionPhysics dense = withPressure(atmosphere.physics, vacuum.priority() + 1, 2);
            DimensionPhysicsData.addPhysicsWithPriority(level.dimension(), dense);
            helper.assertTrue(Math.abs(propeller.getScaledThrust()) > originalThrust, "更高优先级气压未恢复并提高螺旋桨推力");
        }
        helper.succeed();
    }

    private static void assertVacuumStopsThrust(GameTestHelper helper, String dimension) {
        try (LoadedAtmosphere atmosphere = new LoadedAtmosphere(helper, dimension)) {
            ServerLevel level = atmosphere.level;
            PropellerProbe propeller = new PropellerProbe(level, new BlockPos(0, level.getSeaLevel(), 0));
            helper.assertTrue(propeller.getScaledThrust() == 0, dimension + " 的真空仍允许螺旋桨产生推力");
            DimensionPhysicsData.putPhysics(level.dimension(), DimensionPhysicsData.getDefault(level));
            helper.assertTrue(Math.abs(propeller.getScaledThrust()) > 0, "恢复默认大气后，同一螺旋桨仍然没有推力");
        }
    }

    private static DimensionPhysics withPressure(DimensionPhysics original, int priority, double pressure) {
        return new DimensionPhysics(
            original.dimension(), priority, original.universalDrag(), original.baseGravity(), Optional.of(pressure),
            original.pressureFunction(), original.magneticNorth(), original.ignoreChunks()
        );
    }

    private static final class LoadedAtmosphere implements AutoCloseable {
        private final List<DimensionPhysics> original = List.copyOf(DimensionPhysicsData.compilePacket().dimensionPhysics());
        private final ServerLevel level;
        private final DimensionPhysics physics;

        private LoadedAtmosphere(GameTestHelper helper, String path) {
            ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("anvilcraft", path);
            this.physics = this.original.stream()
                .filter(record -> record.dimension().equals(dimension))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未加载维度物理资源：" + dimension));
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
            ServerLevel actualLevel = helper.getLevel().getServer().getLevel(key);
            this.level = actualLevel == null ? helper.getLevel() : actualLevel;
            if (actualLevel == null) {
                // GameTestServer 只按 FLAT 预设创建维度，因此将已加载的物理记录临时用于测试世界。
                DimensionPhysicsData.putPhysics(this.level.dimension(), this.physics);
            }
        }

        @Override
        public void close() {
            DimensionPhysicsData.clearPhysics();
            for (DimensionPhysics physics : this.original) {
                DimensionPhysicsData.putPhysics(ResourceKey.create(Registries.DIMENSION, physics.dimension()), physics);
            }
        }
    }

    // 机械输出保持不变，推力计算直接使用 Sable 接口中读取实际世界气压的默认实现。
    private record PropellerProbe(Level level, BlockPos blockPos) implements BlockEntityPropeller {
        @Override
        public Direction getBlockDirection() {
            return Direction.UP;
        }

        @Override
        public double getAirflow() {
            return 20;
        }

        @Override
        public double getThrust() {
            return 100;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Level getLevel() {
            return this.level;
        }

        @Override
        public BlockPos getBlockPos() {
            return this.blockPos;
        }
    }
}
