package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.aerospace.rail.RailMotion;
import dev.dubhe.anvilcraft.block.sliding.BaseSlidingRailBlock;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BaseSlidingRailBlock.class, remap = false)
abstract class BaseSlidingRailBlockMixin {
    @WrapMethod(method = "entityInside")
    private void aerospace$handleLocalCollision(
        BlockState state, Level level, BlockPos pos, Entity entity, Operation<Void> original
    ) {
        RailMotion.inLocalSpace(pos, entity, () -> original.call(state, level, pos, entity));
    }

    @WrapOperation(
        method = "entityInside",
        at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;sub(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;")
    )
    private Vector3f aerospace$preserveLocalPrecision(
        Vector3f center, Vector3fc position, Operation<Vector3f> original,
        BlockState state, Level level, BlockPos pos, Entity entity
    ) {
        if (Sable.HELPER.getContaining(level, pos) != null) {
            return RailMotion.centerOffset(pos, entity, center);
        }
        return original.call(center, position);
    }
}
