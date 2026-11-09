package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.dubhe.anvilcraft.block.entity.ActivatorSlidingRailBlockEntity;
import dev.dubhe.anvilcraft.block.sliding.ISlidingRail;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 使用真实滑轨搬运、实体碰撞和停站传送验证结构坐标与世界坐标间的交互。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailGameTests {
    @GameTest(template = "empty")
    public static void carriageMovesAndStopsInsideRotatingShip(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = track(5);
        blocks.put(new BlockPos(2, 0, 0), rail("detector_sliding_rail").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        blocks.put(new BlockPos(5, 0, 0), rail("sliding_rail_stop"));
        blocks.put(new BlockPos(0, 1, 0), Blocks.SLIME_BLOCK.defaultBlockState());
        BlockState payload = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        blocks.put(new BlockPos(0, 2, 0), payload);
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(4, 5, 7), blocks)) {
            structure.rotate(new Quaterniond().rotationXYZ(0.2, Math.PI / 2, -0.15));
            SlidingBlockEntity carriage = startCarriage(helper, structure);
            helper.assertTrue(Sable.HELPER.getContaining(carriage) == structure.subLevel(), "滑动方块生成时被踢出飞艇结构");
            helper.assertTrue(carriage.getBlockCount() == 2, "滑轨没有完整收集粘连的两个方块");
            try {
                for (int tick = 0; tick < 30 && carriage.isAlive(); tick++) {
                    structure.advance(new Vec3(0.02, 0, 0.01));
                    carriage.tickCount++;
                    carriage.tick();
                }
                helper.assertTrue(!carriage.isAlive(), "滑动方块未在飞艇滑轨站停靠");
                helper.assertTrue(helper.getLevel().getBlockState(structure.localPos(new BlockPos(5, 1, 0))).is(Blocks.SLIME_BLOCK),
                    "停靠后没有将粘液块放回飞艇局部位置");
                helper.assertTrue(helper.getLevel().getBlockState(structure.localPos(new BlockPos(5, 2, 0))).equals(payload),
                    "停靠后丢失载荷方块或改变了载荷朝向");
                helper.assertTrue(helper.getLevel().getBlockState(structure.localPos(new BlockPos(2, 0, 0)))
                    .getValue(BlockStateProperties.POWERED), "滑动载荷经过探测滑轨时没有输出信号");
            } finally {
                carriage.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activatorRailStopsAndPulsesInsideShip(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = track(3);
        blocks.put(new BlockPos(2, 0, 0), powered("activator_sliding_rail"));
        blocks.put(new BlockPos(2, -1, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
        blocks.put(new BlockPos(0, 1, 0), Blocks.IRON_BLOCK.defaultBlockState());
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(5, 5, 7), blocks)) {
            structure.rotate(new Quaterniond().rotationY(0.7));
            SlidingBlockEntity carriage = startCarriage(helper, structure);
            try {
                for (int tick = 0; tick < 20 && carriage.isAlive(); tick++) {
                    carriage.tickCount++;
                    carriage.tick();
                }
                BlockPos activator = structure.localPos(new BlockPos(2, 0, 0));
                helper.assertTrue(!carriage.isAlive(), "充能激活滑轨未停止载荷");
                helper.assertTrue(helper.getLevel().getBlockState(activator.above()).is(Blocks.IRON_BLOCK), "激活时未恢复载荷方块");
                helper.assertTrue(helper.getLevel().getBlockEntity(activator) instanceof ActivatorSlidingRailBlockEntity blockEntity
                    && blockEntity.getShouldPower() == TriState.TRUE, "结构内激活滑轨未启动激活脉冲");
            } finally {
                carriage.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldItemLandsOnRotatedPoweredRail(GameTestHelper helper) {
        try (SableTestStructure structure = poweredStructure(helper)) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            ItemEntity item = item(helper, worldPosition(structure, new Vec3(0.5, 1.8, 0.5)));
            Vec3 direction = structure.subLevel().logicalPose().transformNormal(new Vec3(1, 0, 0));
            boolean launched = false;
            try {
                for (int tick = 0; tick < 30 && !launched; tick++) {
                    item.tick();
                    launched = item.getDeltaMovement().dot(direction) > 0.1;
                }
                helper.assertTrue(launched, "世界物品落上旋转动力滑轨后没有沿船内轨道方向加速");
                helper.assertTrue(Sable.HELPER.getContaining(item) == null, "动力滑轨把世界物品移进了远端存储空间");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poweredRailRotatesLivingEntityMotion(GameTestHelper helper) {
        try (SableTestStructure structure = poweredStructure(helper)) {
            structure.rotate(new Quaterniond().rotationXYZ(0.3, 0.7, 0.2));
            Pig pig = Objects.requireNonNull(EntityType.PIG.create(helper.getLevel()));
            pig.setPos(worldPosition(structure, new Vec3(0.5, 0.4, 0.5)));
            Vec3 initialPosition = pig.position();
            BlockPos railPos = structure.localPos(BlockPos.ZERO);
            helper.getLevel().getBlockState(railPos).entityInside(helper.getLevel(), railPos, pig);
            Vec3 localVelocity = structure.subLevel().logicalPose().transformNormalInverse(pig.getDeltaMovement());
            helper.assertTrue(localVelocity.x > 0.1 && Math.abs(localVelocity.y) < 1e-6 && Math.abs(localVelocity.z) < 1e-6,
                "生物受到的动力滑轨推力没有按结构朝向转换");
            assertPosition(helper, initialPosition, pig.position(), "动力滑轨碰撞回调改变了生物位置");
            pig.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unpoweredRailCentersWorldItemPrecisely(GameTestHelper helper) {
        BlockState state = rail("powered_sliding_rail").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), Map.of(BlockPos.ZERO, state))) {
            structure.rotate(new Quaterniond().rotationY(0.7));
            ItemEntity item = item(helper, worldPosition(structure, new Vec3(0.7, 0.375, 0.5)));
            try {
                BlockPos railPos = structure.localPos(BlockPos.ZERO);
                state.getBlock().stepOn(helper.getLevel(), railPos, state, item);
                Vec3 localVelocity = structure.subLevel().logicalPose().transformNormalInverse(item.getDeltaMovement());
                helper.assertTrue(localVelocity.x < -0.01 && localVelocity.x > -0.1 && Math.abs(localVelocity.z) < 1e-5,
                    "未供能滑轨没有以格内精度把物品拉回中心，实际速度 " + localVelocity);
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stopCentersWorldItemWithoutPositionJump(GameTestHelper helper) {
        BlockState state = rail("sliding_rail_stop");
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), Map.of(BlockPos.ZERO, state))) {
            structure.rotate(new Quaterniond().rotationY(0.7));
            ItemEntity item = item(helper, worldPosition(structure, new Vec3(0.8, 0.375, 0.6)));
            try {
                Vec3 initialPosition = item.position();
                state.getBlock().stepOn(helper.getLevel(), structure.localPos(BlockPos.ZERO), state, item);
                Vec3 localVelocity = structure.subLevel().logicalPose().transformNormalInverse(item.getDeltaMovement());
                helper.assertTrue(localVelocity.x < -0.05 && localVelocity.x > -0.3 && localVelocity.z < -0.01,
                    "滑轨站没有按局部中心吸附物品，实际速度 " + localVelocity);
                assertPosition(helper, initialPosition, item.position(), "滑轨站吸附造成了实体位置跳变");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stopTransfersItemWithoutLosingWorldIndex(GameTestHelper helper) {
        BlockState stop = rail("sliding_rail_stop");
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), Map.of(
            BlockPos.ZERO, stop,
            new BlockPos(1, 0, 0), powered("powered_sliding_rail"),
            new BlockPos(1, -1, 0), Blocks.REDSTONE_BLOCK.defaultBlockState()
        ))) {
            structure.rotate(new Quaterniond().rotationY(Math.PI / 2));
            ItemEntity item = item(helper, worldPosition(structure, new Vec3(0.5, 0.375, 0.5)));
            try {
                stop.getBlock().stepOn(helper.getLevel(), structure.localPos(BlockPos.ZERO), stop, item);
                Vec3 target = worldPosition(structure, new Vec3(1.5, 0.375, 0.5));
                assertPosition(helper, target, item.position(), "滑轨站没有将物品传送到相邻船内动力滑轨");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, item.getBoundingBox().inflate(0.1)).contains(item),
                    "滑轨站传送后丢失了实体的世界空间索引");
                helper.assertTrue(Sable.HELPER.getContaining(item) == null, "滑轨站传送后实体停留在远端存储坐标");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedItemTransferUpdatesChunkIndex(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(0, 5, 7), Map.of(
            BlockPos.ZERO, Blocks.STONE.defaultBlockState(),
            new BlockPos(15, 0, 0), Blocks.STONE.defaultBlockState()
        ))) {
            int edgeOffset = 15 - Math.floorMod(structure.localPos(BlockPos.ZERO).getX(), 16);
            BlockPos stopPos = structure.localPos(new BlockPos(edgeOffset, 0, 0));
            BlockPos targetPos = stopPos.east();
            BlockState stop = rail("sliding_rail_stop");
            level.setBlockAndUpdate(targetPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(stopPos, stop);
            level.setBlockAndUpdate(targetPos, powered("powered_sliding_rail"));
            ItemEntity item = item(helper, helper.absolutePos(new BlockPos(7, 8, 7)).getCenter());
            try {
                // 先注册世界实体，再正式迁入 plot，直接执行站点回调以保留真实局部空间索引。
                item.setPos(stopPos.getBottomCenter().add(0, 0.375, 0));
                item.setYRot(-90);
                item.setDeltaMovement(new Vec3(0.1, 0, 0));
                final AABB oldBounds = item.getBoundingBox().inflate(0.1);
                helper.assertTrue(Sable.HELPER.getContaining(item) == structure.subLevel(), "测试物品尚未进入结构空间索引");
                helper.assertTrue(!item.chunkPosition().equals(new ChunkPos(targetPos)), "测试站点与目的地没有跨越区块边界");
                stop.getBlock().stepOn(level, stopPos, stop, item);
                assertPosition(helper, targetPos.getBottomCenter().add(0, 0.375, 0), item.position(), "局部物品没有传送到相邻动力滑轨");
                helper.assertTrue(item.chunkPosition().equals(new ChunkPos(targetPos)), "已在结构内的物品传送后仍保留旧区块索引");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, item.getBoundingBox().inflate(0.1)).contains(item),
                    "目标区块查询未找到已传送的局部物品");
                helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, oldBounds).contains(item), "旧站点范围仍查询到已传走的物品");
                helper.assertTrue(Sable.HELPER.getContaining(item) == structure.subLevel(), "局部物品传送后脱离了原结构");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void detectorRailDetectsWorldItem(GameTestHelper helper) {
        BlockState state = rail("detector_sliding_rail");
        try (SableTestStructure structure = SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), Map.of(BlockPos.ZERO, state))) {
            structure.rotate(new Quaterniond().rotationXYZ(0.1, 0.7, 0.2));
            ItemEntity item = item(helper, worldPosition(structure, new Vec3(0.5, 0.45, 0.5)));
            try {
                BlockPos railPos = structure.localPos(BlockPos.ZERO);
                state.entityInside(helper.getLevel(), railPos, item);
                helper.assertTrue(helper.getLevel().getBlockState(railPos).getValue(BlockStateProperties.POWERED),
                    "旋转探测滑轨未识别世界空间物品");
            } finally {
                item.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryWorldPoweredRailRemainsUnchanged(GameTestHelper helper) {
        BlockState state = powered("powered_sliding_rail");
        BlockPos position = helper.absolutePos(new BlockPos(7, 5, 7));
        ItemEntity item = item(helper, position.getBottomCenter().add(0, 0.375, 0));
        try {
            Vec3 initialPosition = item.position();
            state.getBlock().stepOn(helper.getLevel(), position, state, item);
            assertPosition(helper, new Vec3(0.35, 0, 0), item.getDeltaMovement(), "普通世界动力滑轨速度发生变化");
            assertPosition(helper, initialPosition, item.position(), "普通世界动力滑轨改变了实体位置");
        } finally {
            item.discard();
        }
        helper.succeed();
    }

    private static SableTestStructure poweredStructure(GameTestHelper helper) {
        return SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), Map.of(
            BlockPos.ZERO, powered("powered_sliding_rail"),
            BlockPos.ZERO.below(), Blocks.REDSTONE_BLOCK.defaultBlockState()
        ));
    }

    private static SlidingBlockEntity startCarriage(GameTestHelper helper, SableTestStructure structure) {
        BlockPos source = structure.localPos(BlockPos.ZERO.above());
        helper.assertTrue(ISlidingRail.moveBlocks(helper.getLevel(), source, Direction.EAST), "滑轨未启动结构搬运");
        List<SlidingBlockEntity> carriages = helper.getLevel().getEntitiesOfClass(SlidingBlockEntity.class, new AABB(source).inflate(1));
        helper.assertTrue(carriages.size() == 1, "启动滑轨后没有生成唯一滑动结构实体");
        return carriages.getFirst();
    }

    private static Map<BlockPos, BlockState> track(int length) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int index = 0; index < length; index++) {
            blocks.put(new BlockPos(index, 0, 0), rail("sliding_rail").setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        }
        return blocks;
    }

    private static BlockState powered(String name) {
        return rail(name).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST).setValue(BlockStateProperties.POWERED, true);
    }

    private static BlockState rail(String name) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", name))
            .orElseThrow(() -> new IllegalStateException("测试前置未注册滑轨：" + name)).defaultBlockState();
    }

    private static Vec3 worldPosition(SableTestStructure structure, Vec3 localOffset) {
        Vec3 position = Vec3.atLowerCornerOf(structure.localPos(BlockPos.ZERO)).add(localOffset);
        return structure.subLevel().logicalPose().transformPosition(position);
    }

    private static ItemEntity item(GameTestHelper helper, Vec3 position) {
        ServerLevel level = helper.getLevel();
        ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(Items.IRON_INGOT), 0, 0, 0);
        level.addFreshEntity(item);
        return item;
    }

    private static void assertPosition(GameTestHelper helper, Vec3 expected, Vec3 actual, String message) {
        helper.assertTrue(expected.distanceToSqr(actual) < 1e-8, message + "，期望 " + expected + "，实际 " + actual);
    }
}
