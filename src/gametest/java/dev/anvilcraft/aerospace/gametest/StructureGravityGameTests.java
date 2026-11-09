package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通过物理事件与真实刚体冲量验证天体引力作用、质量关系和船载源的运动。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureGravityGameTests {
    @GameTest(template = "empty")
    public static void worldSourcesAttractAndRepel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(12, 3, 7));
        try (SableTestStructure target = assemble(helper, new BlockPos(7, 3, 7), Blocks.IRON_BLOCK)) {
            GravityManager.GravitySourceManager.addSource(level, source, new GravityManager.GravitySourceType(16, 8, 1));
            step(level, 0.05);
            double attraction = velocity(target).x;
            helper.assertTrue(attraction > 0, "世界天体未牵引飞艇刚体");
            resetVelocity(target);
            GravityManager.GravitySourceManager.addSource(level, source, new GravityManager.GravitySourceType(-16, 8, 1));
            step(level, 0.05);
            helper.assertTrue(Math.abs(velocity(target).x + attraction) < 1e-6, "排斥引力的方向或强度没有应用到刚体");
        } finally {
            GravityManager.GravitySourceManager.removeSource(level, source);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void accelerationDoesNotDependOnShipMass(GameTestHelper helper) {
        Vector3d light = measureWorldSource(helper, Blocks.STONE, false, false);
        Vector3d heavy = measureWorldSource(helper, Blocks.IRON_BLOCK, false, false);
        helper.assertTrue(light.x > 0 && light.distance(heavy) < 1e-6, "相同引力场中不同质量飞艇的加速度不同");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatedShipReceivesWorldSpaceForce(GameTestHelper helper) {
        Vector3d ordinary = measureWorldSource(helper, Blocks.IRON_BLOCK, false, false);
        Vector3d rotated = measureWorldSource(helper, Blocks.IRON_BLOCK, true, false);
        helper.assertTrue(ordinary.x > 0 && ordinary.distance(rotated) < 1e-6, "飞艇旋转改变了世界天体的牵引方向");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void physicsSubstepsPreserveImpulse(GameTestHelper helper) {
        Vector3d single = measureWorldSource(helper, Blocks.IRON_BLOCK, false, false);
        Vector3d split = measureWorldSource(helper, Blocks.IRON_BLOCK, false, true);
        helper.assertTrue(single.x > 0 && single.distance(split) < 1e-6, "改变子步数量改变了同一时长内的引力冲量");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingSourceAffectsOtherShipsWithoutSelfPropulsion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(4, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(10, 3, 7), Blocks.STONE)) {
            BlockPos id = sourceShip.localPos(BlockPos.ZERO);
            try {
                Vec3 center = sourceShip.localPos(new BlockPos(2, 0, 0)).getCenter();
                GravityManager.GravitySourceManager.upsertSource(level, id, center, new GravityManager.GravitySourceType(16, 8, 1));
                step(level, 0.05);
                helper.assertTrue(velocity(sourceShip).lengthSquared() < 1e-12, "船载天体牵引了承载自身的飞艇");
                helper.assertTrue(velocity(target).x < 0, "船载天体没有牵引其他飞艇");
                resetVelocity(target);
                sourceShip.advance(new Vec3(8, 0, 0));
                step(level, 0.05);
                helper.assertTrue(velocity(target).x > 0, "飞艇移动后引力源仍停留在旧世界位置");
                helper.assertTrue(velocity(sourceShip).lengthSquared() < 1e-12, "移动后的船载天体产生了自身推进");
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedAndDistantSourcesStopApplyingForce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(4, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(10, 3, 7), Blocks.STONE)) {
            BlockPos id = sourceShip.localPos(BlockPos.ZERO);
            try {
                GravityManager.GravitySourceManager.addSource(level, id, new GravityManager.GravitySourceType(16, 8, 1));
                step(level, 0.05);
                helper.assertTrue(velocity(target).x < 0, "测试引力源未生效");
                resetVelocity(target);
                sourceShip.advance(new Vec3(-20, 0, 0));
                step(level, 0.05);
                helper.assertTrue(velocity(target).lengthSquared() < 1e-12, "移动到半径以外的天体仍在牵引飞艇");
                sourceShip.advance(new Vec3(20, 0, 0));
                GravityManager.GravitySourceManager.removeSource(level, id);
                step(level, 0.05);
                helper.assertTrue(velocity(target).lengthSquared() < 1e-12, "删除天体后仍保留引力");
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyFieldDoesNotDuplicateDimensionGravity(GameTestHelper helper) {
        try (SableTestStructure target = assemble(helper, new BlockPos(7, 3, 7), Blocks.IRON_BLOCK)) {
            step(helper.getLevel(), 0.05);
            helper.assertTrue(velocity(target).lengthSquared() < 1e-12, "没有局部引力源时仍追加了维度重力");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingSourceIncludesBothRadiusEndpoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(7, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(15, 3, 7), Blocks.STONE)) {
            BlockPos id = sourceShip.localPos(BlockPos.ZERO);
            try {
                GravityManager.GravitySourceManager.addSource(level, id, new GravityManager.GravitySourceType(16, 8, 1));
                step(level, 0.05);
                Vector3d positiveEndpoint = velocity(target);
                helper.assertTrue(positiveEndpoint.x < 0, "正方向半径端点丢失了移动源引力");
                resetVelocity(target);
                target.advance(new Vec3(-16, 0, 0));
                step(level, 0.05);
                Vector3d negativeEndpoint = velocity(target);
                helper.assertTrue(negativeEndpoint.x > 0, "负方向半径端点丢失了移动源引力");
                helper.assertTrue(positiveEndpoint.add(negativeEndpoint).lengthSquared() < 1e-12, "移动源两个半径端点的引力不对称");
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatingSourceChangesWorldForceDirection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(7, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(7, 3, 11), Blocks.STONE)) {
            BlockPos id = sourceShip.localPos(BlockPos.ZERO);
            try {
                Vec3 center = sourceShip.localPos(new BlockPos(3, 0, 0)).getCenter();
                GravityManager.GravitySourceManager.upsertSource(level, id, center, new GravityManager.GravitySourceType(16, 8, 1));
                step(level, 0.05);
                Vector3d initial = velocity(target);
                helper.assertTrue(initial.x > 0 && initial.z < 0, "偏心船载源的初始牵引方向错误");
                resetVelocity(target);
                sourceShip.rotate(new Quaterniond().rotationY(Math.PI));
                step(level, 0.05);
                Vector3d rotated = velocity(target);
                helper.assertTrue(rotated.x < 0 && rotated.z < 0, "船载源旋转后没有更新世界牵引方向");
                helper.assertTrue(new Vector3d(-initial.x, initial.y, initial.z).distance(rotated) < 1e-6,
                    "船载源旋转后的引力强度或坐标转换错误");
                helper.assertTrue(velocity(sourceShip).lengthSquared() < 1e-12, "旋转后的船载源牵引了承载自身的飞艇");
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleMovingAndWorldSourcesAddOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        final BlockPos worldSource = helper.absolutePos(new BlockPos(10, 3, 12));
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(4, 3, 4), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(10, 3, 7), Blocks.STONE)) {
            final BlockPos firstId = sourceShip.localPos(BlockPos.ZERO);
            final BlockPos secondId = sourceShip.localPos(new BlockPos(2, 0, 0));
            GravityManager.GravitySourceType firstType = new GravityManager.GravitySourceType(16, 12, 1);
            GravityManager.GravitySourceType secondType = new GravityManager.GravitySourceType(8, 12, 1);
            try {
                GravityManager.GravitySourceManager.addSource(level, firstId, firstType);
                step(level, 0.05);
                final Vector3d first = velocity(target);
                resetVelocity(target);
                GravityManager.GravitySourceManager.removeSource(level, firstId);
                GravityManager.GravitySourceManager.addSource(level, secondId, secondType);
                step(level, 0.05);
                final Vector3d second = velocity(target);
                resetVelocity(target);
                GravityManager.GravitySourceManager.addSource(level, firstId, firstType);
                step(level, 0.05);
                Vector3d both = velocity(target);
                helper.assertTrue(first.lengthSquared() > 0 && second.lengthSquared() > 0, "组合测试的船载源没有分别生效");
                helper.assertTrue(new Vector3d(first).add(second).distance(both) < 1e-6, "同船多个源被重复计算或漏计");
                helper.assertTrue(velocity(sourceShip).lengthSquared() < 1e-12, "同船多个源产生了自身推进");
                resetVelocity(target);
                GravityManager.GravitySourceManager.removeSource(level, firstId);
                GravityManager.GravitySourceManager.removeSource(level, secondId);
                GravityManager.GravitySourceManager.addSource(level, worldSource, firstType);
                step(level, 0.05);
                Vector3d world = velocity(target);
                helper.assertTrue(world.z > 0, "组合测试的世界源没有生效");
                resetVelocity(target);
                GravityManager.GravitySourceManager.addSource(level, firstId, firstType);
                GravityManager.GravitySourceManager.addSource(level, secondId, secondType);
                step(level, 0.05);
                helper.assertTrue(new Vector3d(both).add(world).distance(velocity(target)) < 1e-6,
                    "世界源与移动源没有按实际观测的冲量各叠加一次");
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, firstId);
                GravityManager.GravitySourceManager.removeSource(level, secondId);
                GravityManager.GravitySourceManager.removeSource(level, worldSource);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adjacentStoragePlotsDoNotMixDistantSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure sourceShip = assemble(helper, new BlockPos(4, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure distantShip = assemble(helper, new BlockPos(7, 3, 7), Blocks.IRON_BLOCK);
             SableTestStructure target = assemble(helper, new BlockPos(10, 3, 7), Blocks.STONE)) {
            int boundary = sourceShip.subLevel().getPlot().getChunkMax().getMaxBlockZ() + 1;
            helper.assertTrue(distantShip.subLevel().getPlot().getChunkMin().getMinBlockZ() == boundary
                && distantShip.subLevel().getPlot().getChunkMin().x == sourceShip.subLevel().getPlot().getChunkMin().x,
                "测试的两个源没有分配到相邻存储区");
            BlockPos anchor = sourceShip.localPos(BlockPos.ZERO);
            BlockPos firstId = new BlockPos(anchor.getX(), anchor.getY(), boundary - 8);
            BlockPos secondId = new BlockPos(anchor.getX(), anchor.getY(), boundary + 8);
            Vec3 targetCenter = helper.absolutePos(new BlockPos(10, 3, 7)).getCenter();
            Vec3 firstCenter = sourceShip.subLevel().logicalPose().transformPosition(firstId.getCenter());
            sourceShip.advance(targetCenter.add(0, 0, -8).subtract(firstCenter));
            Vec3 secondCenter = distantShip.subLevel().logicalPose().transformPosition(secondId.getCenter());
            distantShip.advance(targetCenter.add(0, 0, 10000).subtract(secondCenter));
            GravityManager.GravitySourceType type = new GravityManager.GravitySourceType(64, 16, 1);
            try {
                GravityManager.GravitySourceManager.addSource(level, firstId, type);
                step(level, 0.05);
                Vector3d expected = velocity(target);
                helper.assertTrue(expected.z < 0, "边界附近的有效船载源没有牵引目标飞艇");
                resetVelocity(target);
                GravityManager.GravitySourceManager.addSource(level, secondId, type);
                step(level, 0.05);
                helper.assertTrue(expected.distance(velocity(target)) < 1e-6,
                    "物理远离但存储坐标相邻的船载源混入了有效引力，期望 " + expected + "，实际 " + velocity(target));
            } finally {
                GravityManager.GravitySourceManager.removeSource(level, firstId);
                GravityManager.GravitySourceManager.removeSource(level, secondId);
            }
        }
        helper.succeed();
    }

    private static Vector3d measureWorldSource(GameTestHelper helper, Block block, boolean rotated, boolean split) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(12, 3, 7));
        try (SableTestStructure target = assemble(helper, new BlockPos(7, 3, 7), block)) {
            if (rotated) {
                target.rotate(new Quaterniond().rotationZ(Math.PI / 2));
            }
            GravityManager.GravitySourceManager.addSource(level, source, new GravityManager.GravitySourceType(16, 8, 1));
            if (split) {
                step(level, 0.025);
                step(level, 0.025);
            } else {
                step(level, 0.05);
            }
            return velocity(target);
        } finally {
            GravityManager.GravitySourceManager.removeSource(level, source);
        }
    }

    private static SableTestStructure assemble(GameTestHelper helper, BlockPos anchor, Block block) {
        SableTestStructure structure = SableTestStructure.assemble(helper, anchor, Map.of(BlockPos.ZERO, block.defaultBlockState()));
        initializeMasses(helper.getLevel());
        return structure;
    }

    private static void initializeMasses(ServerLevel level) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.require(level);
        PhysicsPipeline pipeline = system.getPipeline();
        List<BodySnapshot> snapshots = new ArrayList<>();
        for (ServerSubLevel body : Objects.requireNonNull(SubLevelContainer.getContainer(level)).getAllSubLevels()) {
            snapshots.add(new BodySnapshot(body, pipeline.readPose(body, new Pose3d()),
                pipeline.getLinearVelocity(body, new Vector3d()), pipeline.getAngularVelocity(body, new Vector3d())));
        }
        try {
            // Rapier 在首个真实步骤中初始化有效逆质量，直接追加冲量会被初始零逆质量消去。
            pipeline.physicsTick(0.05);
        } finally {
            for (BodySnapshot snapshot : snapshots) {
                pipeline.teleport(snapshot.body, snapshot.pose.position(), snapshot.pose.orientation());
                pipeline.resetVelocity(snapshot.body);
                pipeline.addLinearAndAngularVelocity(snapshot.body, snapshot.linearVelocity, snapshot.angularVelocity);
            }
        }
    }

    private static void step(ServerLevel level, double timeStep) {
        NeoForge.EVENT_BUS.post(new ForgeSablePrePhysicsTickEvent(SubLevelPhysicsSystem.require(level), timeStep));
    }

    private static Vector3d velocity(SableTestStructure structure) {
        return structure.physics().getLinearVelocity(new Vector3d());
    }

    private static void resetVelocity(SableTestStructure structure) {
        structure.physics().addLinearAndAngularVelocity(velocity(structure).negate(), new Vector3d());
    }

    private record BodySnapshot(ServerSubLevel body, Pose3d pose, Vector3d linearVelocity, Vector3d angularVelocity) {
    }
}
