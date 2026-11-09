package dev.anvilcraft.aerospace.mixin;

import dev.anvilcraft.aerospace.magnet.MagnetCapture;
import dev.anvilcraft.aerospace.magnet.MagnetIndex;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = ServerLevelPlot.class, remap = false)
abstract class ServerLevelPlotMixin implements MagnetIndex {
    @Unique
    private final Set<BlockPos> aerospace$activeMagnets = new HashSet<>();

    @Shadow
    public abstract ServerSubLevel getSubLevel();

    @Inject(method = "onBlockChange", at = @At("TAIL"))
    private void aerospace$updateMagnets(BlockPos pos, BlockState state, CallbackInfo ci) {
        this.aerospace$updateMagnet(pos, state);
    }

    @Override
    public void aerospace$updateMagnet(BlockPos pos, BlockState state) {
        if (MagnetCapture.isActive(state)) {
            this.aerospace$activeMagnets.add(pos.immutable());
        } else {
            this.aerospace$activeMagnets.remove(pos);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aerospace$attractNearbyAnvils(CallbackInfo ci) {
        // 吸附触发的邻居更新可能修改集合，因此只遍历当前刻的磁铁快照。
        for (BlockPos pos : this.aerospace$activeMagnets.toArray(BlockPos[]::new)) {
            ServerSubLevel subLevel = this.getSubLevel();
            if (MagnetCapture.isActive(subLevel.getLevel().getBlockState(pos))) {
                MagnetCapture.attract(subLevel, pos);
            } else {
                this.aerospace$activeMagnets.remove(pos);
            }
        }
    }
}
