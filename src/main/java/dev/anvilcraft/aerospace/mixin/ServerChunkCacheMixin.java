package dev.anvilcraft.aerospace.mixin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(
        method = "getChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;asLong(II)J"),
        cancellable = true
    )
    private void aerospace$readCurrentPlotChunk(
        int x, int z, ChunkStatus status, boolean requireChunk, CallbackInfoReturnable<ChunkAccess> cir
    ) {
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null || !container.inBounds(x, z)) {
            return;
        }
        // 保留原版的主线程调度，但在四项缓存之前查询结构，避免同刻复用存储区时取得已卸载的区块。
        LevelChunk chunk = container.getChunk(new ChunkPos(x, z));
        cir.setReturnValue(chunk != null ? chunk
            : ((ServerChunkCache) (Object) this).getChunkFuture(x, z, status, requireChunk).join().orElse(null));
    }
}
