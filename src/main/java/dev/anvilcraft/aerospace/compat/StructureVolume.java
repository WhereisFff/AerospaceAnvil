package dev.anvilcraft.aerospace.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将机器的局部范围与世界及其他动态结构中的实体、方块做精确相交检测。
 */
public final class StructureVolume {
    private final Level level;
    private final @Nullable SubLevel source;
    private final AABB localBounds;
    private final AABB worldBounds;
    private @Nullable Geometry geometry;

    public StructureVolume(Level level, BlockPos origin, AABB bounds) {
        this.level = level;
        this.source = Sable.HELPER.getContaining(level, origin);
        this.localBounds = bounds;
        this.worldBounds = this.source == null ? bounds
            : new BoundingBox3d(bounds).transform(this.source.logicalPose(), new BoundingBox3d()).toMojang();
    }

    public <T extends Entity> List<T> filter(List<T> entities) {
        return entities.stream().filter(entity -> this.intersects(entity.getBoundingBox(), Sable.HELPER.getContaining(entity)))
            .distinct().toList();
    }

    public Iterable<BlockPos> blocks() {
        Set<BlockPos> result = new LinkedHashSet<>();
        this.collectBlocks(null, this.worldBounds, result);
        if (this.source != null) {
            this.collectBlocks(this.source, this.localBounds, result);
        }
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(this.worldBounds))) {
            if (subLevel != this.source) {
                AABB local = new BoundingBox3d(this.worldBounds)
                    .transformInverse(subLevel.logicalPose(), new BoundingBox3d()).toMojang();
                this.collectBlocks(subLevel, local, result);
            }
        }
        return result;
    }

    private void collectBlocks(@Nullable SubLevel frame, AABB bounds, Set<BlockPos> result) {
        for (BlockPos pos : BlockPos.betweenClosed(
            Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
            Mth.ceil(bounds.maxX) - 1, Mth.ceil(bounds.maxY) - 1, Mth.ceil(bounds.maxZ) - 1
        )) {
            if (Sable.HELPER.getContaining(this.level, pos) == frame
                && this.level.hasChunkAt(pos) && this.intersects(new AABB(pos), frame)) {
                result.add(pos.immutable());
            }
        }
    }

    private boolean intersects(AABB bounds, @Nullable SubLevel frame) {
        if (frame == this.source) {
            return this.localBounds.intersects(bounds);
        }
        if (this.geometry == null) {
            this.geometry = new Geometry(this.localBounds, this.source);
        }
        return this.geometry.intersects(bounds, frame);
    }

    private static final class Geometry {
        private final LevelReusedVectors vectors = new LevelReusedVectors();
        private final OrientedBoundingBox3d volume = new OrientedBoundingBox3d(this.vectors);
        private final OrientedBoundingBox3d candidate = new OrientedBoundingBox3d(this.vectors);

        private Geometry(AABB bounds, @Nullable SubLevel frame) {
            setBox(this.volume, bounds, frame);
        }

        private boolean intersects(AABB bounds, @Nullable SubLevel frame) {
            setBox(this.candidate, bounds, frame);
            return OrientedBoundingBox3d.sat(this.volume, this.candidate).lengthSquared() > 0;
        }

        private static void setBox(OrientedBoundingBox3d box, AABB bounds, @Nullable SubLevel frame) {
            Vec3 center = bounds.getCenter();
            Vector3d size = new Vector3d(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
            if (frame == null) {
                box.set(new Vector3d(center.x, center.y, center.z), size, new Quaterniond());
            } else {
                center = frame.logicalPose().transformPosition(center);
                box.set(new Vector3d(center.x, center.y, center.z), size.mul(frame.logicalPose().scale()),
                    frame.logicalPose().orientation());
            }
        }
    }
}
