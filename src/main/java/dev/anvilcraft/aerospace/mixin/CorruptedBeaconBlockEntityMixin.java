package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.aerospace.compat.CorruptedBeam;
import dev.anvilcraft.aerospace.compat.StructureVolume;
import dev.dubhe.anvilcraft.block.entity.CorruptedBeaconBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(value = CorruptedBeaconBlockEntity.class, remap = false)
abstract class CorruptedBeaconBlockEntityMixin {
    @Shadow
    private int checkingBeamHeight;
    @Shadow
    private int lastCheckY;
    @Unique
    private boolean aerospace$wasProjected;

    @Inject(method = "tick", at = @At("HEAD"))
    private static void aerospace$checkMovingObstructions(
        Level level, BlockPos pos, BlockState state, CorruptedBeaconBlockEntity blockEntity, CallbackInfo ci
    ) {
        CorruptedBeaconBlockEntityMixin thiz = (CorruptedBeaconBlockEntityMixin) (Object) blockEntity;
        Optional<CorruptedBeam.Result> result = CorruptedBeam.trace(level, pos, thiz.aerospace$wasProjected);
        thiz.aerospace$wasProjected = result.isPresent() && result.get().includesStructures();
        if (result.isPresent()) {
            thiz.checkingBeamHeight = result.get().height();
            thiz.lastCheckY = level.getMaxBuildHeight();
        }
    }

    @WrapOperation(
        method = "affectEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass("
            + "Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private static <T extends Entity> List<T> aerospace$filterAffectedEntities(
        Level level, Class<T> type, AABB bounds, Operation<List<T>> original,
        @Local(argsOnly = true) BlockPos pos
    ) {
        return new StructureVolume(level, pos, bounds).filter(original.call(level, type, bounds));
    }

    @WrapOperation(
        method = "setBeaconStatus",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass("
            + "Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private static <T extends Entity> List<T> aerospace$filterAdvancementPlayers(
        Level level, Class<T> type, AABB bounds, Operation<List<T>> original,
        @Local(argsOnly = true) BlockPos pos
    ) {
        return new StructureVolume(level, pos, bounds).filter(original.call(level, type, bounds));
    }
}
