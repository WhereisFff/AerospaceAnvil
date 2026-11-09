package dev.anvilcraft.aerospace.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 将实体的世界朝向换算为落地或磁铁捕获位置所在坐标系中的方块方向。
 */
public final class FallingBlockPhysics {
    private FallingBlockPhysics() {
    }

    public static BlockState stateForPlacement(FallingBlockEntity entity, @Nullable SubLevel destination) {
        Quaternionf orientation = ((FallingBlockOrientation) entity).aerospace$getOrientation();
        if (destination != null) {
            orientation = new Quaternionf(destination.logicalPose().orientation()).invert().mul(orientation);
        }
        Vector3f north = orientation.transform(new Vector3f(0, 0, -1));
        // 方块只能按格子轴放置，垂直朝向使用模型右侧的水平投影确定最近的四分之一圈。
        if (north.x * north.x + north.z * north.z < 1.0E-6f) {
            Vector3f east = orientation.transform(new Vector3f(1, 0, 0));
            north.set(east.z, 0, -east.x);
        }
        Direction direction = Direction.getNearest(north.x, 0, north.z);
        Rotation rotation = switch (direction) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return entity.getBlockState().rotate(rotation);
    }
}
