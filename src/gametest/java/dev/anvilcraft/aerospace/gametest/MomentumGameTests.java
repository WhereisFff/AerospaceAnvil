package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.anvilcraft.aerospace.physics.FallingBlockOrientation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;

/**
 * 使用真实 Sable 结构验证下落方块的离船速度、世界运动和朝向持久化。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MomentumGameTests {
    private static final double EPSILON = 1e-5;

    @GameTest(template = "empty")
    public static void stationaryStructurePreservesVelocity(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            FallingBlockEntity entity = fallingBlock(structure, new Vec3(0, 4, 0), new Vec3(0, -0.3, 0));
            Vec3 expectedCenter = structure.subLevel().logicalPose().transformPosition(center(entity));
            EntitySubLevelUtil.kickEntity(structure.subLevel(), entity);
            assertVector(helper, new Vec3(0, -0.3, 0), entity.getDeltaMovement(), "静止结构改变了原有下落速度");
            assertVector(helper, expectedCenter, center(entity), "下落方块中心没有转换到世界坐标");
            helper.assertTrue(Sable.HELPER.getContaining(entity) == null, "离船后仍处于结构存储坐标中");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inheritsLinearVelocity(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.physics().addLinearAndAngularVelocity(new Vector3d(6, 2, -4), new Vector3d());
            FallingBlockEntity entity = fallingBlock(structure, new Vec3(0, 4, 0), new Vec3(0, -0.3, 0));
            EntitySubLevelUtil.kickEntity(structure.subLevel(), entity);
            assertVector(helper, new Vec3(0.3, -0.2, -0.2), entity.getDeltaMovement(), "离船时未继承完整线速度");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inheritsOppositeTangentialVelocities(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.physics().addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, 2, 0));
            FallingBlockEntity east = fallingBlock(structure, new Vec3(4, 0, 0), Vec3.ZERO);
            FallingBlockEntity west = fallingBlock(structure, new Vec3(-4, 0, 0), Vec3.ZERO);
            EntitySubLevelUtil.kickEntity(structure.subLevel(), east);
            EntitySubLevelUtil.kickEntity(structure.subLevel(), west);
            assertVector(helper, new Vec3(0, 0, -0.4), east.getDeltaMovement(), "东侧离船方块缺少切向速度");
            assertVector(helper, new Vec3(0, 0, 0.4), west.getDeltaMovement(), "西侧离船方块切向速度方向错误");
            east.discard();
            west.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void angularVelocityUsesBlockCenter(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.physics().addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d(0, 0, 2));
            FallingBlockEntity entity = fallingBlock(structure, new Vec3(0, 4, 0), Vec3.ZERO);
            EntitySubLevelUtil.kickEntity(structure.subLevel(), entity);
            assertVector(helper, new Vec3(-0.3, 0, 0), entity.getDeltaMovement(), "切向速度没有使用方块中心或没有叠加线速度");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatesLocalVelocityAndOrientation(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.rotate(new Quaterniond().rotationZ(Math.PI / 2));
            structure.physics().addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            FallingBlockEntity entity = fallingBlock(structure, new Vec3(0, 4, 0), new Vec3(0, -0.2, 0));
            EntitySubLevelUtil.kickEntity(structure.subLevel(), entity);
            assertVector(helper, new Vec3(0.3, 0, 0), entity.getDeltaMovement(), "结构旋转后的下落方向错误");
            Quaternionf orientation = ((FallingBlockOrientation) entity).aerospace$getOrientation();
            Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
            assertVector(helper, new Vec3(-1, 0, 0), new Vec3(up.x, up.y, up.z), "离船方块没有继承结构朝向");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticFallingSpawnInheritsVelocityOnce(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            BlockPos position = structure.localPos(new BlockPos(0, 4, 0));
            helper.getLevel().setBlock(position, Blocks.ANVIL.defaultBlockState(), 3);
            structure.physics().addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            FallingBlockEntity entity = FallingBlockEntity.fall(helper.getLevel(), position, Blocks.ANVIL.defaultBlockState());
            assertVector(helper, new Vec3(0.2, 0, 0), entity.getDeltaMovement(), "正常下落生成流程丢失或重复添加了飞艇速度");
            helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "下落后结构内仍保留原铁砧");
            helper.assertTrue(Sable.HELPER.getContaining(entity) == null, "正常下落流程没有进入世界坐标");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airborneMotionRemainsInertial(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.physics().addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            FallingBlockEntity released = fallingBlock(structure, new Vec3(0, 6, 0), new Vec3(0, -0.1, 0));
            EntitySubLevelUtil.kickEntity(structure.subLevel(), released);
            FallingBlockEntity control = new FallingBlockEntity(EntityType.FALLING_BLOCK, helper.getLevel());
            control.setPos(released.position().add(0, 0, 3));
            control.setDeltaMovement(released.getDeltaMovement());
            structure.physics().addLinearAndAngularVelocity(new Vector3d(-20, 10, 0), new Vector3d(0, 8, 0));
            released.tick();
            control.tick();
            assertVector(helper, control.getDeltaMovement(), released.getDeltaMovement(), "离船后飞艇加速继续影响了方块");
            helper.assertTrue(Sable.HELPER.getTrackingSubLevel(released) == null, "悬空方块仍被强制跟随飞艇");
            released.discard();
            control.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryWorldFallKeepsVanillaMotion(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(8, 8, 8));
        helper.getLevel().setBlock(position, Blocks.SAND.defaultBlockState(), 3);
        FallingBlockEntity entity = FallingBlockEntity.fall(helper.getLevel(), position, Blocks.SAND.defaultBlockState());
        assertVector(helper, Vec3.ZERO, entity.getDeltaMovement(), "普通世界下落方块获得了额外速度");
        assertVector(helper, new Vec3(position.getX() + 0.5, position.getY(), position.getZ() + 0.5),
            entity.position(), "普通世界下落方块发生了位置偏移");
        Quaternionf orientation = ((FallingBlockOrientation) entity).aerospace$getOrientation();
        helper.assertTrue(orientation.equals(new Quaternionf(), (float) EPSILON), "普通世界方块被附加了旋转");
        entity.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void orientationSurvivesEntityReload(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.rotate(new Quaterniond().rotationXYZ(0.2, 0.7, -0.4));
            FallingBlockEntity entity = fallingBlock(structure, new Vec3(0, 5, 0), Vec3.ZERO);
            EntitySubLevelUtil.kickEntity(structure.subLevel(), entity);
            Quaternionf expected = new Quaternionf(((FallingBlockOrientation) entity).aerospace$getOrientation());
            CompoundTag saved = entity.saveWithoutId(new CompoundTag());
            FallingBlockEntity restored = new FallingBlockEntity(EntityType.FALLING_BLOCK, helper.getLevel());
            restored.load(saved);
            Quaternionf actual = ((FallingBlockOrientation) restored).aerospace$getOrientation();
            helper.assertTrue(expected.equals(actual, (float) EPSILON), "保存并加载后丢失方块朝向");
            assertVector(helper, entity.position(), restored.position(), "保存并加载后改变了方块世界坐标");
            entity.discard();
            restored.discard();
        }
        helper.succeed();
    }

    private static SableTestStructure assemble(GameTestHelper helper) {
        return SableTestStructure.assemble(helper, new BlockPos(7, 3, 7), Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
    }

    private static FallingBlockEntity fallingBlock(SableTestStructure structure, Vec3 centerOffset, Vec3 velocity) {
        FallingBlockEntity entity = new FallingBlockEntity(EntityType.FALLING_BLOCK, structure.subLevel().getLevel());
        Vector3d rotationPoint = structure.subLevel().logicalPose().rotationPoint();
        entity.setPos(rotationPoint.x + centerOffset.x,
            rotationPoint.y + centerOffset.y - entity.getBbHeight() / 2,
            rotationPoint.z + centerOffset.z);
        entity.setDeltaMovement(velocity);
        return entity;
    }

    private static Vec3 center(FallingBlockEntity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2, 0);
    }

    private static void assertVector(GameTestHelper helper, Vec3 expected, Vec3 actual, String message) {
        helper.assertTrue(expected.distanceToSqr(actual) < EPSILON * EPSILON,
            message + "，期望 " + expected + "，实际 " + actual);
    }
}
