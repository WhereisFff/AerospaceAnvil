package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.anvilcraft.aerospace.compat.CorruptedBeam;
import dev.anvilcraft.aerospace.compat.StructureVolume;
import dev.dubhe.anvilcraft.block.entity.CorruptedBeaconBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 验证腐化信标随船转动的光束、跨坐标系遮挡和原有实体转化配方。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CorruptedBeaconGameTests {
    @GameTest(template = "empty")
    public static void tiltedBeamStopsAtWorldBlock(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            structure.rotate(new Quaterniond().rotationZ(Math.PI / 2));
            BlockPos obstruction = BlockPos.containing(world(structure, new BlockPos(0, 4, 0)));
            helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            try {
                int height = CorruptedBeam.height(helper.getLevel(), structure.localPos(BlockPos.ZERO)).orElseThrow();
                helper.assertTrue(height == structure.localPos(new BlockPos(0, 4, 0)).getY(),
                    "倾斜光束没有在普通世界阻挡方块前结束");
                structure.advance(new Vec3(0, 0, 3));
                int movedHeight = CorruptedBeam.height(helper.getLevel(), structure.localPos(BlockPos.ZERO)).orElseThrow();
                helper.assertTrue(movedHeight == helper.getLevel().getMaxBuildHeight(), "飞艇移开世界障碍后仍沿用旧的光束截断位置");
            } finally {
                helper.getLevel().removeBlock(obstruction, false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transparentWorldBlocksPreserveBeaconRules(GameTestHelper helper) {
        try (SableTestStructure structure = assemble(helper)) {
            BlockPos glass = BlockPos.containing(world(structure, new BlockPos(0, 2, 0)));
            BlockPos bedrock = BlockPos.containing(world(structure, new BlockPos(0, 3, 0)));
            BlockPos stone = BlockPos.containing(world(structure, new BlockPos(0, 5, 0)));
            helper.getLevel().setBlockAndUpdate(glass, Blocks.RED_STAINED_GLASS.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            try {
                int height = CorruptedBeam.height(helper.getLevel(), structure.localPos(BlockPos.ZERO)).orElseThrow();
                helper.assertTrue(height == structure.localPos(new BlockPos(0, 5, 0)).getY(),
                    "跨坐标系光束未保留染色玻璃和基岩的穿透规则");
            } finally {
                helper.getLevel().removeBlock(glass, false);
                helper.getLevel().removeBlock(bedrock, false);
                helper.getLevel().removeBlock(stone, false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beamStopsAtAnotherStructure(GameTestHelper helper) {
        try (SableTestStructure beaconShip = assemble(helper);
             SableTestStructure obstacleShip = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
                 Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))) {
            Vec3 destination = world(beaconShip, new BlockPos(0, 4, 0));
            obstacleShip.advance(destination.subtract(world(obstacleShip, BlockPos.ZERO)));
            int height = CorruptedBeam.height(helper.getLevel(), beaconShip.localPos(BlockPos.ZERO)).orElseThrow();
            helper.assertTrue(height == beaconShip.localPos(new BlockPos(0, 4, 0)).getY(), "光束没有被另一动态结构遮挡");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldBeaconDetectsMovingStructureObstruction(GameTestHelper helper) {
        BlockPos beacon = helper.absolutePos(new BlockPos(7, 5, 7));
        try (SableTestStructure obstacleShip = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
            Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))) {
            obstacleShip.advance(beacon.above(4).getCenter().subtract(world(obstacleShip, BlockPos.ZERO)));
            int height = CorruptedBeam.height(helper.getLevel(), beacon).orElseThrow();
            helper.assertTrue(height == beacon.above(4).getY(), "普通世界信标没有检测到进入光束的动态结构");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void escapedRayCannotHitAnotherPlotStorage(GameTestHelper helper) {
        try (SableTestStructure source = assemble(helper);
             SableTestStructure foreign = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
                 Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))) {
            BlockPos foreignPos = foreign.localPos(BlockPos.ZERO);
            Vec3 start = foreignPos.below(2).getCenter();
            Vec3 end = foreignPos.above(2).getCenter();
            helper.assertTrue(CorruptedBeam.traceFrame(helper.getLevel(), source.localPos(BlockPos.ZERO), start, end,
                source.subLevel()) == null, "越过本船plot边界的射线错误读取了另一船的存储方块");
            helper.assertTrue(CorruptedBeam.traceFrame(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), start, end,
                null) == null, "普通世界射线将plot存储方块误当作世界障碍");
            helper.assertTrue(CorruptedBeam.traceFrame(helper.getLevel(), foreignPos.below(3), start, end,
                foreign.subLevel()) != null, "目标船自身坐标系中的真实障碍被遗漏");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldBeamImmediatelyRecoversAfterShipLeaves(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(7, 5, 7));
        helper.getLevel().setBlockAndUpdate(pos, block("corrupted_beacon"));
        try (SableTestStructure obstacle = SableTestStructure.assemble(helper, new BlockPos(12, 7, 12),
            Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))) {
            CorruptedBeaconBlockEntity beacon = (CorruptedBeaconBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(beacon != null, "普通世界缺少腐化信标方块实体");
            obstacle.advance(pos.above(4).getCenter().subtract(world(obstacle, BlockPos.ZERO)));
            CorruptedBeaconBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), beacon);
            helper.assertTrue(beacon.getBeamHeight() == pos.above(4).getY(), "移动障碍没有截断世界信标");
            obstacle.advance(new Vec3(4, 0, 0));
            CorruptedBeaconBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), beacon);
            helper.assertTrue(beacon.getBeamHeight() == helper.getLevel().getMaxBuildHeight(), "最后的遮挡船离开后光束未立即恢复");
        } finally {
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void rotatedBeaconAffectsInsideEntitiesAndRunsTransformation(GameTestHelper helper) {
        helper.onEachTick(() -> {
            if (helper.getLevel().getGameTime() % 80 != 0) {
                return;
            }
            try (SableTestStructure structure = assemble(helper)) {
                structure.rotate(new Quaterniond().rotationY(Math.PI / 4));
                final Sheep inside = spawn(helper, EntityType.SHEEP, world(structure, new BlockPos(0, 3, 0)));
                final Sheep outside = spawn(helper, EntityType.SHEEP, world(structure, new BlockPos(0, 3, 0)).add(1.05, 0, 1.05));
                final Cow cow = spawn(helper, EntityType.COW, world(structure, new BlockPos(0, 6, 0)));
                final Vec3 conversionPosition = cow.position();
                try {
                    BlockPos origin = structure.localPos(BlockPos.ZERO);
                    AABB beam = new AABB(origin).expandTowards(0, helper.getLevel().getMaxBuildHeight() - origin.getY(), 0);
                    helper.assertTrue(new StructureVolume(helper.getLevel(), origin, beam).filter(List.of(outside)).isEmpty(),
                        "测试羊实际上与旋转光束范围相交，实体边界 " + outside.getBoundingBox());
                    helper.assertTrue(!outside.hasEffect(MobEffects.WITHER), "测试羊在信标执行前已有凋零效果");
                    tick(helper, structure);
                    helper.assertTrue(inside.hasEffect(MobEffects.WITHER), "旋转信标范围内的世界实体没有凋零效果");
                    helper.assertTrue(!outside.hasEffect(MobEffects.WITHER), "信标影响了旋转范围外包盒角落的实体");
                    helper.assertTrue(cow.isRemoved(), "范围内的牛没有执行原有腐化配方");
                    var transformed = helper.getLevel().getEntitiesOfClass(
                        Ravager.class, new AABB(conversionPosition, conversionPosition).inflate(1)
                    );
                    helper.assertTrue(transformed.size() == 1, "腐化配方没有在世界位置生成唯一劫掠兽");
                    transformed.forEach(Mob::discard);
                } finally {
                    inside.discard();
                    outside.discard();
                    cow.discard();
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void worldObstructionStopsCorruption(GameTestHelper helper) {
        helper.onEachTick(() -> {
            if (helper.getLevel().getGameTime() % 80 != 0) {
                return;
            }
            try (SableTestStructure structure = assemble(helper)) {
                structure.rotate(new Quaterniond().rotationZ(Math.PI / 2));
                BlockPos obstruction = BlockPos.containing(world(structure, new BlockPos(0, 3, 0)));
                helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                Sheep beyond = spawn(helper, EntityType.SHEEP, world(structure, new BlockPos(0, 6, 0)));
                try {
                    tick(helper, structure);
                    helper.assertTrue(!beyond.hasEffect(MobEffects.WITHER), "腐化信标越过世界障碍影响了后方实体");
                } finally {
                    beyond.discard();
                    helper.getLevel().removeBlock(obstruction, false);
                }
            }
            helper.succeed();
        });
    }

    private static SableTestStructure assemble(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(BlockPos.ZERO, block("corrupted_beacon"));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                blocks.put(new BlockPos(x, -1, z), block("cursed_gold_block"));
            }
        }
        return SableTestStructure.assemble(helper, new BlockPos(7, 5, 7), blocks);
    }

    private static BlockState block(String name) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("anvilcraft", name))
            .orElseThrow().defaultBlockState();
    }

    private static Vec3 world(SableTestStructure structure, BlockPos offset) {
        return structure.subLevel().logicalPose().transformPosition(structure.localPos(offset).getCenter());
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, Vec3 center) {
        T entity = Objects.requireNonNull(type.create(helper.getLevel()), "测试生物创建失败");
        entity.setPos(center.subtract(0, entity.getBbHeight() / 2, 0));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void tick(GameTestHelper helper, SableTestStructure structure) {
        BlockPos pos = structure.localPos(BlockPos.ZERO);
        CorruptedBeaconBlockEntity beacon = (CorruptedBeaconBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (beacon == null) {
            throw new IllegalStateException("动态结构缺少腐化信标方块实体");
        }
        CorruptedBeaconBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), beacon);
    }
}
