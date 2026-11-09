package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
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

import java.util.Map;

/**
 * 使用真实动态结构验证磁铁悬停、局部吸附范围和世界铁砧捕获。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagnetGameTests {
    @GameTest(template = "empty")
    public static void suspendedAnvilSurvivesAndReleasesWhenPowered(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 9, 7), Map.of(
            BlockPos.ZERO, magnet(false), BlockPos.ZERO.below(), Blocks.ANVIL.defaultBlockState()
        ))) {
            ServerLevel level = helper.getLevel();
            BlockPos anvil = structure.localPos(BlockPos.ZERO.below());
            for (int tick = 0; tick < 40; tick++) {
                level.getBlockState(anvil).tick(level, anvil, level.random);
                helper.assertTrue(level.getBlockState(anvil).is(BlockTags.ANVIL), "悬空且被磁铁吸住的铁砧开始下落");
            }
            level.setBlockAndUpdate(structure.localPos(BlockPos.ZERO), magnet(true));
            level.getBlockState(anvil).tick(level, anvil, level.random);
            helper.assertTrue(level.getBlockState(anvil).isAir(), "磁铁通电后铁砧没有释放");
            for (FallingBlockEntity entity : level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(anvil).inflate(1))) {
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capturesWorldAnvilAfterMovingWithoutNeighborUpdate(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, false)) {
            Vec3 target = worldCenter(structure, new BlockPos(0, -3, 0)).add(3, 0, 0);
            FallingBlockEntity entity = worldAnvil(helper, target, Blocks.ANVIL.defaultBlockState());
            structure.subLevel().getPlot().tick();
            helper.assertTrue(entity.isAlive(), "移动前捕获了吸附范围外的铁砧");
            structure.subLevel().logicalPose().position().add(3, 0, 0);
            structure.physics().teleport(
                structure.subLevel().logicalPose().position(), structure.subLevel().logicalPose().orientation()
            );
            structure.subLevel().updateBoundingBox();
            structure.subLevel().getPlot().tick();
            assertCaptured(helper, structure, entity);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatedMagnetCapturesInsideItsLocalColumn(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, false)) {
            structure.rotate(new Quaterniond().rotationZ(Math.PI / 2));
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            assertCaptured(helper, structure, entity);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsRotatedBoundingBoxCorners(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, false)) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 4));
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)).add(1.1, 0, 1.1), Blocks.ANVIL.defaultBlockState()
            );
            BlockPos cell = structure.localPos(new BlockPos(0, -3, 0));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(cell)).contains(entity),
                "测试铁砧没有进入 Sable 旋转外包盒，无法验证精确范围过滤");
            structure.subLevel().getPlot().tick();
            helper.assertTrue(entity.isAlive(), "磁铁捕获了旋转吸附柱以外、外包盒角落中的铁砧");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poweredMagnetAndNonmagneticAnvilAreIgnored(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, true)) {
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            helper.assertTrue(entity.isAlive(), "通电磁铁仍在吸附");
            entity.discard();
            helper.getLevel().setBlockAndUpdate(structure.localPos(BlockPos.ZERO), magnet(false));
            BlockState nonmagneticState = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", "neoforge"))
                .orElseThrow(() -> new IllegalStateException("缺少非磁性铁砧测试方块 anvilcraft:neoforge"))
                .defaultBlockState();
            helper.assertTrue(nonmagneticState.is(BlockTags.ANVIL) && nonmagneticState.is(ModBlockTags.NON_MAGNETIC),
                "非磁性样本必须同时属于铁砧标签和非磁性标签");
            FallingBlockEntity nonmagnetic = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)), nonmagneticState
            );
            structure.subLevel().getPlot().tick();
            helper.assertTrue(nonmagnetic.isAlive(), "磁铁吸附了非磁性铁砧");
            nonmagnetic.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void respectsObstaclesAndConfiguredRange(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 9, 7), Map.of(
            BlockPos.ZERO, magnet(false),
            new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(),
            new BlockPos(1, -1, 0), Blocks.STONE.defaultBlockState(),
            new BlockPos(1, -2, 0), Blocks.STONE.defaultBlockState(),
            new BlockPos(0, -2, 0), Blocks.STONE.defaultBlockState()
        ))) {
            BlockPos obstruction = structure.localPos(new BlockPos(0, -2, 0));
            FallingBlockEntity blocked = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -4, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE), "侧向支架未保持障碍与磁铁属于同一结构");
            helper.assertTrue(blocked.isAlive(), "磁铁越过结构内障碍吸附铁砧");
            blocked.discard();
            helper.getLevel().removeBlock(obstruction, false);
            int distance = AnvilCraft.CONFIG.equipment.magnetAttractsDistance;
            FallingBlockEntity distant = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -distance - 2, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            helper.assertTrue(distant.isAlive(), "磁铁吸附了配置距离以外的铁砧");
            distant.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoMagnetsCannotDuplicateOneEntity(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 9, 7), Map.of(
            BlockPos.ZERO, magnet(false), new BlockPos(1, 0, 0), magnet(false)
        ))) {
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)).add(0.5, 0, 0), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            int captures = 0;
            for (int x = 0; x < 2; x++) {
                if (helper.getLevel().getBlockState(structure.localPos(new BlockPos(x, -1, 0))).is(BlockTags.ANVIL)) {
                    captures++;
                }
            }
            helper.assertTrue(entity.isRemoved() && captures == 1, "同一个世界铁砧被两个磁铁重复捕获");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capturePreservesWorldFacing(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, false)) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            assertCaptured(helper, structure, entity);
            BlockState captured = helper.getLevel().getBlockState(structure.localPos(BlockPos.ZERO.below()));
            helper.assertTrue(captured.getValue(AnvilBlock.FACING) == Direction.EAST,
                "世界铁砧捕获到旋转结构时朝向没有转换为结构坐标，实际 " + captured.getValue(AnvilBlock.FACING));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void immediateCaptureKeepsMagnetIndexed(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 9, 7), Map.of(
            BlockPos.ZERO, Blocks.STONE.defaultBlockState()
        ))) {
            ServerLevel level = helper.getLevel();
            BlockPos magnetPos = structure.localPos(new BlockPos(1, 0, 0));
            level.setBlockAndUpdate(magnetPos.below(3), Blocks.ANVIL.defaultBlockState());
            level.setBlockAndUpdate(magnetPos, magnet(false));
            helper.assertTrue(level.getBlockState(magnetPos.below()).is(BlockTags.ANVIL), "放置磁铁时没有立即吸起结构内铁砧");
            level.removeBlock(magnetPos.below(), false);
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(1, -3, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            helper.assertTrue(entity.isRemoved(), "首次吸附重入区块更新后，磁铁从持续检查索引中丢失");
            helper.assertTrue(level.getBlockState(magnetPos.below()).is(BlockTags.ANVIL), "持续检查没有重新捕获世界铁砧");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadedStructureRebuildsMagnetIndex(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper, false)) {
            structure.reload();
            FallingBlockEntity entity = worldAnvil(
                helper, worldCenter(structure, new BlockPos(0, -3, 0)), Blocks.ANVIL.defaultBlockState()
            );
            structure.subLevel().getPlot().tick();
            assertCaptured(helper, structure, entity);
        }
        helper.succeed();
    }

    private static SableTestStructure assemble(GameTestHelper helper, boolean powered) {
        return SableTestStructure.assemble(helper, new BlockPos(7, 9, 7), Map.of(BlockPos.ZERO, magnet(powered)));
    }

    private static BlockState magnet(boolean powered) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", "magnet_block"))
            .orElseThrow(() -> new IllegalStateException("缺少测试磁铁方块 anvilcraft:magnet_block"))
            .defaultBlockState().setValue(MagnetBlock.LIT, powered);
    }

    private static Vec3 worldCenter(SableTestStructure structure, BlockPos offset) {
        return structure.subLevel().logicalPose().transformPosition(structure.localPos(offset).getCenter());
    }

    private static FallingBlockEntity worldAnvil(GameTestHelper helper, Vec3 center, BlockState state) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(13, 13, 13));
        level.setBlockAndUpdate(source, state);
        FallingBlockEntity entity = FallingBlockEntity.fall(level, source, state);
        entity.setPos(center.subtract(0, entity.getBbHeight() / 2, 0));
        entity.setNoGravity(true);
        return entity;
    }

    private static void assertCaptured(GameTestHelper helper, SableTestStructure structure, FallingBlockEntity entity) {
        helper.assertTrue(entity.isRemoved(), "世界铁砧实体没有被移除");
        helper.assertTrue(helper.getLevel().getBlockState(structure.localPos(BlockPos.ZERO.below())).is(BlockTags.ANVIL),
            "捕获后结构内没有出现铁砧方块");
    }
}
