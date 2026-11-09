package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.aerospace.compat.StructureVolume;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = ItemDetectorBlockEntity.class, remap = false)
abstract class ItemDetectorBlockEntityMixin extends BlockEntity {
    protected ItemDetectorBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass("
            + "Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    )
    private <T extends Entity> List<T> aerospace$filterItems(
        Level level, Class<T> type, AABB bounds, Predicate<? super T> predicate, Operation<List<T>> original
    ) {
        return new StructureVolume(level, this.getBlockPos(), bounds).filter(original.call(level, type, bounds, predicate));
    }

    @WrapOperation(
        method = "getOutput",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities("
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    )
    private List<Entity> aerospace$filterInventories(
        Level level, @Nullable Entity except, AABB bounds, Predicate<? super Entity> predicate, Operation<List<Entity>> original
    ) {
        return new StructureVolume(level, this.getBlockPos(), bounds).filter(original.call(level, except, bounds, predicate));
    }

    @WrapOperation(
        method = "getOutput",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;betweenClosed(IIIIII)Ljava/lang/Iterable;")
    )
    private Iterable<BlockPos> aerospace$scanIntersectingInventories(
        int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
        Operation<Iterable<BlockPos>> original,
        @Local(argsOnly = true) Level level,
        @Local(argsOnly = true) AABB bounds
    ) {
        return new StructureVolume(level, this.getBlockPos(), bounds).blocks();
    }
}
