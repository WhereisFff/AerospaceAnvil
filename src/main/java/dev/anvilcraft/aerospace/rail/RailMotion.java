package dev.anvilcraft.aerospace.rail;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 使滑轨的吸附与传送回调使用结构坐标，同时保留实体在世界中的空间索引。
 */
public final class RailMotion {
    private RailMotion() {
    }

    public static void inLocalSpace(BlockPos rail, Entity entity, Runnable action) {
        SubLevel subLevel = Sable.HELPER.getContaining(entity.level(), rail);
        if (subLevel == null || Sable.HELPER.getContaining(entity.level(), entity.position()) != null) {
            action.run();
            return;
        }
        Vec3 previousPosition = entity.position();
        SubLevelHelper.pushEntityLocal(subLevel, entity);
        try {
            action.run();
        } finally {
            SubLevelHelper.popEntityLocal(subLevel, entity);
            Vec3 nextPosition = entity.position();
            // 局部计算期间不迁移实体分区，完成坐标还原后仅提交一次世界位置更新。
            ((EntityExtension) entity).sable$setPosSuperRaw(previousPosition);
            entity.setPos(nextPosition);
        }
    }

    public static Vector3f centerOffset(BlockPos rail, Entity entity, Vector3f destination) {
        // Sable 的远端存储坐标超出 float 的格内精度，先用 double 相减再转换。
        Vec3 difference = rail.getCenter().subtract(entity.position());
        return destination.set((float) difference.x, (float) difference.y, (float) difference.z);
    }
}
