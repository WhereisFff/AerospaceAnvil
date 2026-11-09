package dev.anvilcraft.aerospace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.aerospace.physics.FallingBlockOrientation;
import dev.anvilcraft.aerospace.physics.FallingBlockPhysics;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin extends Entity implements FallingBlockOrientation {
    @Unique
    private static final EntityDataAccessor<Quaternionf> AEROSPACE_ANVIL_ORIENTATION = SynchedEntityData.defineId(
        FallingBlockEntity.class, EntityDataSerializers.QUATERNION
    );

    @Shadow
    public BlockState blockState;

    protected FallingBlockEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void aerospace$defineOrientation(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(AEROSPACE_ANVIL_ORIENTATION, new Quaternionf());
    }

    @Override
    public Quaternionf aerospace$getOrientation() {
        return new Quaternionf(this.entityData.get(AEROSPACE_ANVIL_ORIENTATION));
    }

    @Override
    public void aerospace$setOrientation(Quaternionf orientation) {
        this.entityData.set(AEROSPACE_ANVIL_ORIENTATION, new Quaternionf(orientation).normalize());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void aerospace$saveOrientation(CompoundTag tag, CallbackInfo ci) {
        Quaternionf orientation = this.aerospace$getOrientation();
        ListTag components = new ListTag();
        components.add(FloatTag.valueOf(orientation.x));
        components.add(FloatTag.valueOf(orientation.y));
        components.add(FloatTag.valueOf(orientation.z));
        components.add(FloatTag.valueOf(orientation.w));
        tag.put("aerospace_anvil:orientation", components);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void aerospace$loadOrientation(CompoundTag tag, CallbackInfo ci) {
        ListTag components = tag.getList("aerospace_anvil:orientation", Tag.TAG_FLOAT);
        if (components.size() == 4) {
            Quaternionf orientation = new Quaternionf(
                components.getFloat(0), components.getFloat(1), components.getFloat(2), components.getFloat(3)
            );
            if (orientation.isFinite() && orientation.lengthSquared() > 1.0E-6f) {
                this.aerospace$setOrientation(orientation);
            }
        }
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean aerospace$orientLanding(
        Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original
    ) {
        this.blockState = FallingBlockPhysics.stateForPlacement(
            (FallingBlockEntity) (Object) this, Sable.HELPER.getTrackingSubLevel(this)
        );
        this.aerospace$setOrientation(new Quaternionf());
        return original.call(level, pos, this.blockState, flags);
    }
}
