package dev.anvilcraft.aerospace.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 按腐化信标的透明规则追踪各坐标系中的阻挡，保留信标局部向上的光束方向。
 */
public final class CorruptedBeam {
    private CorruptedBeam() {
    }

    public static OptionalInt height(Level level, BlockPos beacon) {
        Optional<Result> result = trace(level, beacon, false);
        return result.isPresent() ? OptionalInt.of(result.get().height()) : OptionalInt.empty();
    }

    public static Optional<Result> trace(Level level, BlockPos beacon, boolean finishProjection) {
        SubLevel source = Sable.HELPER.getContaining(level, beacon);
        Vec3 localStart = beacon.getCenter().add(0, 0.5, 0);
        Vec3 localEnd = new Vec3(localStart.x, level.getMaxBuildHeight(), localStart.z);
        Vec3 start = source == null ? localStart : source.logicalPose().transformPosition(localStart);
        Vec3 end = source == null ? localEnd : source.logicalPose().transformPosition(localEnd);
        Set<SubLevel> structures = new LinkedHashSet<>();
        if (source != null) {
            structures.add(source);
        }
        for (SubLevel structure : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(start, end))) {
            structures.add(structure);
        }
        if (structures.isEmpty() && !finishProjection) {
            return Optional.empty();
        }
        Vec3 nearest = end;
        Vec3 beaconCenter = source == null ? beacon.getCenter() : source.logicalPose().transformPosition(beacon.getCenter());
        BlockHitResult worldHit = traceFrame(level, BlockPos.containing(beaconCenter), start, end, null);
        if (worldHit != null) {
            nearest = worldHit.getLocation();
        }
        for (SubLevel structure : structures) {
            Vec3 from = structure.logicalPose().transformPositionInverse(start);
            Vec3 to = structure.logicalPose().transformPositionInverse(end);
            BlockPos localBeacon = BlockPos.containing(structure.logicalPose().transformPositionInverse(beaconCenter));
            BlockHitResult hit = traceFrame(level, localBeacon, from, to, structure);
            if (hit != null) {
                Vec3 world = structure.logicalPose().transformPosition(hit.getLocation());
                if (world.distanceToSqr(start) < nearest.distanceToSqr(start)) {
                    nearest = world;
                }
            }
        }
        Vec3 localHit = source == null ? nearest : source.logicalPose().transformPositionInverse(nearest);
        int height = Mth.clamp(Mth.floor(localHit.y + 1.0E-7), beacon.getY() + 1, level.getMaxBuildHeight());
        return Optional.of(new Result(height, !structures.isEmpty()));
    }

    /**
     * 在已确定的坐标系内追踪阻挡，跳过其他结构占用的存储区块。
     */
    public static @Nullable BlockHitResult traceFrame(
        Level level, BlockPos beacon, Vec3 start, Vec3 end, @Nullable SubLevel frame
    ) {
        // Sable 的 clip 会重建 ClipContext 并丢弃自定义透明规则，因此在每个已转换的坐标系内使用原版逐格射线。
        return BlockGetter.traverseBlocks(start, end, level, (view, pos) -> {
            if (Sable.HELPER.getContaining(view, pos) != frame || !view.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = view.getBlockState(pos);
            if (state.getBeaconColorMultiplier(view, pos, beacon) != null
                || state.getLightBlock(view, pos) < 15 || state.is(Blocks.BEDROCK)) {
                return null;
            }
            return Shapes.block().clip(start, end, pos);
        }, view -> null);
    }

    /**
     * 区分本刻仍有结构参与与离开结构后的最后一次完整世界扫描。
     */
    public record Result(int height, boolean includesStructures) {
    }
}
