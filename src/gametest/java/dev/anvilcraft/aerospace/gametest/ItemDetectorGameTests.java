package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.anvilcraft.aerospace.compat.StructureVolume;
import dev.dubhe.anvilcraft.block.ItemDetectorBlock;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.Map;

/**
 * 验证动态结构探测器对掉落物和各坐标系容器的范围、数量与过滤规则。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemDetectorGameTests {
    @GameTest(template = "empty")
    public static void movingDetectorFindsWorldItems(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            ItemDetectorBlockEntity detector = detector(structure);
            final ItemEntity item = item(helper, world(structure, new BlockPos(0, 0, -2)).add(3, 0, 0));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 0, "探测器移动前误检范围外物品");
            structure.advance(new Vec3(3, 0, 0));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 3, "探测器移动后未按真实世界位置统计十个物品");
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatedDetectorRejectsBroadphaseCorner(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 4));
            ItemDetectorBlockEntity detector = detector(structure);
            Vec3 center = structure.subLevel().logicalPose().transformPosition(detector.shape().getCenter());
            ItemEntity outside = item(helper, center.add(1.5, 0, -1.5));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, detector.shape()).contains(outside),
                "旋转范围测试样本未进入 Sable 外包盒");
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 0, "探测器统计了旋转检测柱外包盒角落的物品");
            outside.discard();
            ItemEntity inside = item(helper, world(structure, new BlockPos(0, 0, -2)));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 3, "旋转检测柱内的物品未被统计");
            inside.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detectorReadsWorldContainerAndKeepsFilter(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            ItemDetectorBlockEntity detector = detector(structure);
            BlockPos chestPos = BlockPos.containing(world(structure, new BlockPos(0, 0, -2)));
            helper.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            try {
                ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
                helper.assertTrue(chest != null, "世界容器测试缺少箱子方块实体");
                chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
                detector.setFilter(0, new ItemStack(Items.IRON_INGOT));
                detector.tick();
                helper.assertTrue(detector.getOutputSignal() == 0, "跨坐标系容器扫描忽略了物品过滤");
                detector.setFilter(0, new ItemStack(Items.DIAMOND));
                detector.tick();
                helper.assertTrue(detector.getOutputSignal() == 3, "旋转探测器没有读取世界箱子");
            } finally {
                helper.getLevel().removeBlock(chestPos, false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detectorReadsAnotherMovingStructure(GameTestHelper helper) {
        try (SableTestStructure detectorShip = assemble(helper);
             SableTestStructure chestShip = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
                 Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()))) {
            final ItemDetectorBlockEntity detector = detector(detectorShip);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestShip.localPos(BlockPos.ZERO));
            helper.assertTrue(chest != null, "另一动态结构没有箱子方块实体");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
            Vec3 target = world(detectorShip, new BlockPos(0, 0, -2));
            chestShip.advance(target.subtract(world(chestShip, BlockPos.ZERO)));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 3, "探测器未读取另一动态结构中进入范围的箱子");
            chestShip.advance(new Vec3(4, 0, 0));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 0, "另一动态结构移出范围后仍被统计");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void localInventoryIsCountedOnlyOnce(GameTestHelper helper) {
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 7, 7), Map.of(
            BlockPos.ZERO, detectorState(), new BlockPos(0, 0, -1), Blocks.CHEST.defaultBlockState()
        ))) {
            final ItemDetectorBlockEntity detector = detector(structure);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(structure.localPos(new BlockPos(0, 0, -1)));
            helper.assertTrue(chest != null, "本船容器测试缺少箱子");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
            structure.rotate(new Quaterniond().rotationY(Math.PI / 4));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 3, "本船容器被重复统计或遗漏");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stationaryWorldDetectorReadsShipContainer(GameTestHelper helper) {
        BlockPos detectorPos = helper.absolutePos(new BlockPos(7, 7, 7));
        helper.getLevel().setBlockAndUpdate(detectorPos, detectorState());
        try (SableTestStructure chestShip = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
            Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()))) {
            ItemDetectorBlockEntity detector = (ItemDetectorBlockEntity) helper.getLevel().getBlockEntity(detectorPos);
            helper.assertTrue(detector != null, "普通世界缺少物品探测器");
            detector.setRange(4);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestShip.localPos(BlockPos.ZERO));
            helper.assertTrue(chest != null, "动态结构缺少箱子");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
            chestShip.rotate(new Quaterniond().rotationY(Math.PI / 4));
            chestShip.advance(detectorPos.north(2).getCenter().subtract(world(chestShip, BlockPos.ZERO)));
            detector.tick();
            helper.assertTrue(detector.getOutputSignal() == 3, "普通世界探测器未读取范围内的动态结构容器");
        } finally {
            helper.getLevel().removeBlock(detectorPos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void escapedLocalVolumeCannotReadAnotherPlot(GameTestHelper helper) {
        try (SableTestStructure source = assemble(helper);
             SableTestStructure foreign = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
                 Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()))) {
            BlockPos foreignPos = foreign.localPos(BlockPos.ZERO);
            AABB escapedBounds = new AABB(foreignPos);
            StructureVolume projected = new StructureVolume(helper.getLevel(), source.localPos(BlockPos.ZERO), escapedBounds);
            for (BlockPos pos : projected.blocks()) {
                helper.assertTrue(!pos.equals(foreignPos), "越过本船plot边界的局部范围错误读取了另一船的存储容器");
            }
            StructureVolume world = new StructureVolume(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), escapedBounds);
            for (BlockPos pos : world.blocks()) {
                helper.assertTrue(!pos.equals(foreignPos), "普通世界范围将plot存储容器误当作世界方块");
            }
        }
        helper.succeed();
    }

    private static SableTestStructure assemble(GameTestHelper helper) {
        return SableTestStructure.assemble(helper, new BlockPos(7, 7, 7), Map.of(
            BlockPos.ZERO, detectorState(), BlockPos.ZERO.below(), Blocks.STONE.defaultBlockState()
        ));
    }

    private static BlockState detectorState() {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", "item_detector"))
            .orElseThrow().defaultBlockState().setValue(ItemDetectorBlock.FACING, Direction.NORTH);
    }

    private static ItemDetectorBlockEntity detector(SableTestStructure structure) {
        ItemDetectorBlockEntity detector = (ItemDetectorBlockEntity) structure.subLevel().getLevel()
            .getBlockEntity(structure.localPos(BlockPos.ZERO));
        if (detector == null) {
            throw new IllegalStateException("动态结构缺少物品探测器方块实体，方块 "
                + structure.subLevel().getLevel().getBlockState(structure.localPos(BlockPos.ZERO))
                + "，结构已移除 " + structure.subLevel().isRemoved());
        }
        detector.setRange(4);
        detector.recalcDetectionRange();
        return detector;
    }

    private static Vec3 world(SableTestStructure structure, BlockPos offset) {
        return structure.subLevel().logicalPose().transformPosition(structure.localPos(offset).getCenter());
    }

    private static ItemEntity item(GameTestHelper helper, Vec3 center) {
        ItemEntity entity = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, new ItemStack(Items.DIAMOND, 10));
        entity.setPos(center.subtract(0, entity.getBbHeight() / 2, 0));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
