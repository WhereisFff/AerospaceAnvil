package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.anvilcraft.aerospace.physics.FallingBlockOrientation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EntitySubLevelUtil.class, remap = false)
abstract class EntitySubLevelUtilMixin {
    @WrapMethod(method = "kickEntity")
    private static void aerospace$inheritMotion(SubLevel subLevel, Entity entity, Operation<Void> original) {
        if (!(entity instanceof FallingBlockEntity)) {
            original.call(subLevel, entity);
            return;
        }
        Vec3 center = entity.position().add(0, entity.getBbHeight() / 2d, 0);
        Vector3d velocity = Sable.HELPER.getVelocity(
            entity.level(), subLevel, new Vector3d(center.x, center.y, center.z), new Vector3d()
        );
        Quaternionf orientation = new Quaternionf(subLevel.logicalPose().orientation());
        original.call(subLevel, entity);
        // Sable 返回米每秒，Minecraft 实体速度使用格每刻；原方法已经旋转实体自身速度。
        entity.setDeltaMovement(entity.getDeltaMovement().add(velocity.x / 20, velocity.y / 20, velocity.z / 20));
        FallingBlockOrientation falling = (FallingBlockOrientation) entity;
        falling.aerospace$setOrientation(orientation.mul(falling.aerospace$getOrientation()));
    }
}
