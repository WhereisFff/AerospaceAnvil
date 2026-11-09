package dev.anvilcraft.aerospace.magnet;

import dev.anvilcraft.aerospace.physics.FallingBlockPhysics;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.entity.IAnvilCraftEntityExtension;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.util.MagnetUtil;
import dev.dubhe.anvilcraft.util.TriggerUtil;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 在动态结构的局部吸附柱中选择铁砧，并使用结构坐标完成方块更新。
 */
public final class MagnetCapture {
    private MagnetCapture() {
    }

    public static boolean isActive(BlockState state) {
        return state.getBlock() instanceof MagnetBlock && !state.getValue(MagnetBlock.LIT);
    }

    public static void attract(ServerSubLevel subLevel, BlockPos magnetPos) {
        ServerLevel level = subLevel.getLevel();
        if (subLevel.isRemoved() || !isActive(level.getBlockState(magnetPos))) {
            return;
        }
        BlockPos destination = magnetPos.below();
        if (level.getBlockState(destination).is(BlockTags.ANVIL)) {
            return;
        }
        int distance = AnvilCraft.CONFIG.equipment.magnetAttractsDistance;
        if (distance <= 0) {
            return;
        }
        AABB column = new AABB(destination).expandTowards(0, 1 - distance, 0);
        List<FallingBlockEntity> entities = level.getEntitiesOfClass(
            FallingBlockEntity.class, column, entity -> entity.isAlive()
                && !(entity instanceof IAnvilCraftEntityExtension) && isMagneticAnvil(entity.getBlockState())
        );
        @Nullable CaptureGeometry geometry = null;
        for (int offset = 1; offset <= distance; offset++) {
            BlockPos pos = magnetPos.below(offset);
            BlockState state = level.getBlockState(pos);
            if (isMagneticAnvil(state)) {
                level.destroyBlock(destination, true);
                level.setBlockAndUpdate(destination, state);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                MagnetUtil.animateAnvilFromMagnet(level, pos.getBottomCenter(), state, magnetPos);
                TriggerUtil.liftingAnvil(level, pos);
                return;
            }
            for (FallingBlockEntity entity : entities) {
                if (!entity.isAlive()) {
                    continue;
                }
                if (geometry == null) {
                    geometry = new CaptureGeometry();
                }
                if (!geometry.intersects(subLevel, pos, entity)) {
                    continue;
                }
                final Vec3 start = Sable.HELPER.getContaining(entity) == subLevel
                    ? entity.position() : subLevel.logicalPose().transformPositionInverse(entity.position());
                BlockState capturedState = Sable.HELPER.getContaining(entity) == subLevel
                    ? entity.getBlockState() : FallingBlockPhysics.stateForPlacement(entity, subLevel);
                // 邻居更新会再次触发其他磁铁，先移除实体可避免同一铁砧被重复捕获。
                entity.discard();
                level.destroyBlock(destination, true);
                level.setBlockAndUpdate(destination, capturedState);
                MagnetUtil.animateAnvilFromMagnet(level, start, capturedState, magnetPos, entity.getId());
                TriggerUtil.liftingAnvil(level, pos);
                return;
            }
            if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
                return;
            }
        }
    }

    private static boolean isMagneticAnvil(BlockState state) {
        return state.is(BlockTags.ANVIL) && !state.is(ModBlockTags.NON_MAGNETIC);
    }

    private static final class CaptureGeometry {
        private final LevelReusedVectors vectors = new LevelReusedVectors();
        private final OrientedBoundingBox3d captureBox = new OrientedBoundingBox3d(this.vectors);
        private final OrientedBoundingBox3d entityBox = new OrientedBoundingBox3d(this.vectors);

        private boolean intersects(ServerSubLevel subLevel, BlockPos pos, FallingBlockEntity entity) {
            SubLevel containing = Sable.HELPER.getContaining(entity);
            AABB bounds = entity.getBoundingBox();
            if (containing == subLevel) {
                return bounds.intersects(new AABB(pos));
            }
            if (containing != null) {
                return false;
            }
            Vec3 center = subLevel.logicalPose().transformPosition(pos.getCenter());
            this.captureBox.set(
                new Vector3d(center.x, center.y, center.z),
                subLevel.logicalPose().scale(),
                subLevel.logicalPose().orientation()
            );
            Vec3 entityCenter = bounds.getCenter();
            this.entityBox.set(
                new Vector3d(entityCenter.x, entityCenter.y, entityCenter.z),
                new Vector3d(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()),
                new Quaterniond()
            );
            // Sable 的查询只使用旋转后的外包 AABB；分离轴检测排除旋转吸附柱外的候选实体。
            return OrientedBoundingBox3d.sat(this.captureBox, this.entityBox).lengthSquared() > 0;
        }
    }
}
