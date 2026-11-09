package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.aerospace.rail.RailMotion;
import dev.dubhe.anvilcraft.block.sliding.SlidingRailStopBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SlidingRailStopBlock.class, remap = false)
abstract class SlidingRailStopBlockMixin {
    @WrapMethod(method = "stepOn")
    private void aerospace$stopInLocalSpace(
        Level level, BlockPos pos, BlockState state, Entity entity, Operation<Void> original
    ) {
        RailMotion.inLocalSpace(pos, entity, () -> original.call(level, pos, state, entity));
    }

    @WrapOperation(
        method = "stepOn",
        at = @At(value = "INVOKE", target = "Ldev/dubhe/anvilcraft/block/sliding/ISlidingRail;"
            + "absorbEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V")
    )
    private void aerospace$preservePullPrecision(BlockPos pos, Entity entity, Operation<Void> original) {
        if (Sable.HELPER.getContaining(entity.level(), pos) == null) {
            original.call(pos, entity);
            return;
        }
        Vec3 acceleration = pos.getCenter().subtract(entity.position()).multiply(0.45f / 0.98f, 0, 0.45f / 0.98f);
        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5).add(acceleration));
    }

    @WrapOperation(
        method = "tryTeleportToSlidingRail",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void aerospace$deferWorldPositionUpdate(Entity entity, Vec3 position, Operation<Void> original) {
        if (Sable.HELPER.getContaining(entity) == null && Sable.HELPER.getContaining(entity.level(), position) != null) {
            ((EntityExtension) entity).sable$setPosSuperRaw(position);
            return;
        }
        original.call(entity, position);
    }
}
