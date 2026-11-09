package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;

/**
 * 通过真实碰撞、红石开关和粉碎配方验证铁砧离船后的完整处理流程。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandingGameTests {
    private static final BlockPos INPUT = new BlockPos(0, 1, 0);
    private static final BlockPos LANDING = new BlockPos(0, 2, 0);
    private static final BlockPos RELEASE = new BlockPos(0, 4, 0);
    private static final BlockPos MAGNET = new BlockPos(0, 5, 0);
    private static final BlockPos POWER = new BlockPos(1, 5, 0);

    @GameTest(template = "empty")
    public static void landsOnRotatedStructureAndCrushesBlock(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 3, 7), Map.of(
            BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
            INPUT, Blocks.COBBLESTONE.defaultBlockState()
        ))) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            BlockState source = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.NORTH);
            BlockPos release = structure.localPos(RELEASE);
            helper.getLevel().setBlock(release, source, 3);
            FallingBlockEntity entity = FallingBlockEntity.fall(helper.getLevel(), release, source);
            fallToRest(helper, structure, entity, Vec3.ZERO);
            BlockState landed = helper.getLevel().getBlockState(structure.localPos(LANDING));
            helper.assertTrue(landed.is(BlockTags.ANVIL), "铁砧未落回旋转结构内");
            helper.assertTrue(landed.getValue(AnvilBlock.FACING) == Direction.NORTH, "落回原结构后改变了铁砧局部朝向");
            helper.assertTrue(helper.getLevel().getBlockState(structure.localPos(INPUT)).is(Blocks.GRAVEL),
                "旋转结构内的铁砧落地未触发圆石粉碎配方");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void landingInWorldConvertsFacing(GameTestHelper helper) {
        FallingBlockEntity entity;
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 3, 7),
            Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()))) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            BlockState source = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.NORTH);
            BlockPos release = structure.localPos(RELEASE);
            helper.getLevel().setBlock(release, source, 3);
            entity = FallingBlockEntity.fall(helper.getLevel(), release, source);
        }
        BlockPos floor = BlockPos.containing(entity.getX(), helper.absolutePos(new BlockPos(0, 1, 0)).getY(), entity.getZ());
        helper.getLevel().setBlock(floor, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        for (int tick = 0; tick < 60 && entity.isAlive(); tick++) {
            entity.tick();
        }
        helper.assertTrue(!entity.isAlive(), "铁砧未完成普通世界落地");
        BlockState landed = helper.getLevel().getBlockState(floor.above());
        helper.assertTrue(landed.is(BlockTags.ANVIL), "普通世界落地没有放置铁砧");
        helper.assertTrue(landed.getValue(AnvilBlock.FACING) == Direction.WEST, "世界落地没有按飞艇朝向旋转铁砧");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingMagnetRepeatsCrushingCycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState magnetState = BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", "magnet_block"))
            .orElseThrow(() -> new IllegalStateException("测试前置未注册 anvilcraft:magnet_block"))
            .defaultBlockState();
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(5, 3, 7), Map.of(
            BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
            INPUT, Blocks.COBBLESTONE.defaultBlockState(),
            RELEASE, Blocks.ANVIL.defaultBlockState(),
            MAGNET, magnetState,
            new BlockPos(-1, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(),
            new BlockPos(-1, 1, 0), Blocks.IRON_BLOCK.defaultBlockState(),
            new BlockPos(-1, 2, 0), Blocks.IRON_BLOCK.defaultBlockState(),
            new BlockPos(-1, 3, 0), Blocks.IRON_BLOCK.defaultBlockState(),
            new BlockPos(-1, 4, 0), Blocks.IRON_BLOCK.defaultBlockState(),
            new BlockPos(-1, 5, 0), Blocks.IRON_BLOCK.defaultBlockState()
        ))) {
            BlockPos magnet = structure.localPos(MAGNET);
            BlockPos power = structure.localPos(POWER);
            BlockPos release = structure.localPos(RELEASE);
            Vec3 movement = new Vec3(0.05, 0, 0);
            structure.physics().addLinearAndAngularVelocity(new Vector3d(1, 0, 0), new Vector3d());
            for (int cycle = 0; cycle < 3; cycle++) {
                helper.assertTrue(level.getBlockState(magnet).getBlock() instanceof MagnetBlock,
                    "第 " + (cycle + 1) + " 轮开始时磁铁已离开原结构位置，实际方块为 " + level.getBlockState(magnet));
                level.setBlock(structure.localPos(INPUT), Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.assertTrue(level.getBlockState(magnet).getValue(MagnetBlock.LIT), "红石开关没有关闭磁铁吸附");
                BlockState anvil = level.getBlockState(release);
                helper.assertTrue(anvil.is(BlockTags.ANVIL), "循环开始时磁铁下方没有铁砧");
                anvil.tick(level, release, level.random);
                Vec3 spawn = structure.subLevel().logicalPose().transformPosition(release.getCenter());
                List<FallingBlockEntity> falling = level.getEntitiesOfClass(FallingBlockEntity.class,
                    new AABB(spawn.x - 1, spawn.y - 1, spawn.z - 1, spawn.x + 1, spawn.y + 1, spawn.z + 1),
                    candidate -> candidate.isAlive() && candidate.getBlockState().is(BlockTags.ANVIL));
                helper.assertTrue(falling.size() == 1, "磁铁释放后没有生成唯一铁砧实体");
                fallToRest(helper, structure, falling.getFirst(), movement);
                helper.assertTrue(level.getBlockState(structure.localPos(INPUT)).is(Blocks.GRAVEL),
                    "移动结构第 " + (cycle + 1) + " 次下砸没有完成圆石粉碎");
                helper.assertTrue(level.getBlockState(structure.localPos(LANDING)).is(BlockTags.ANVIL),
                    "移动结构第 " + (cycle + 1) + " 次下砸后没有回到局部落点");
                level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
                level.getBlockState(magnet).tick(level, magnet, level.random);
                structure.subLevel().getPlot().tick();
                helper.assertTrue(level.getBlockState(release).is(BlockTags.ANVIL), "磁铁重新启用后没有回收铁砧");
            }
        }
        helper.succeed();
    }

    private static void fallToRest(
        GameTestHelper helper, SableTestStructure structure, FallingBlockEntity entity, Vec3 movement
    ) {
        try {
            for (int tick = 0; tick < 60 && entity.isAlive(); tick++) {
                // 固定飞行轨迹可分别检查实体碰撞与飞艇运动，避免自由落体改变试验条件。
                structure.advance(movement);
                entity.tick();
            }
            helper.assertTrue(!entity.isAlive(), "下落铁砧在限定时间内没有完成落地");
        } finally {
            entity.discard();
        }
    }
}
