package dev.anvilcraft.aerospace.mixin;

import dev.anvilcraft.aerospace.physics.StructureGravity;
import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GravityManager.GravitySourceManager.class, remap = false)
abstract class GravitySourceManagerMixin {
    @Inject(method = "upsertSource", at = @At("RETURN"))
    private static void aerospace$trackMovingSource(
        Level level, BlockPos id, Vec3 center, GravityManager.GravitySourceType type, CallbackInfo ci
    ) {
        StructureGravity.updateSource(level, id, center, type);
    }

    @Inject(method = "removeSource", at = @At("HEAD"))
    private static void aerospace$removeMovingSource(Level level, BlockPos id, CallbackInfo ci) {
        StructureGravity.removeSource(level, id);
    }
}
