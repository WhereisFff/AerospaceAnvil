package dev.anvilcraft.aerospace.mixin;

import dev.anvilcraft.aerospace.magnet.MagnetCapture;
import dev.anvilcraft.aerospace.magnet.MagnetIndex;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MagnetBlock.class, remap = false)
abstract class MagnetBlockMixin {
    @Inject(method = "attract", at = @At("HEAD"), cancellable = true)
    private void aerospace$attractWorldAnvil(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel subLevel) {
            // 吸附会重入区块方块更新，提前登记可避免外层 Sable 变更通知被内层更新覆盖。
            ((MagnetIndex) subLevel.getPlot()).aerospace$updateMagnet(pos, state);
            MagnetCapture.attract(subLevel, pos);
            ci.cancel();
        }
    }
}
