package dev.anvilcraft.aerospace.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 保存结构中需要检查世界铁砧的磁铁位置，允许放置回调在邻居更新重入前登记。
 */
public interface MagnetIndex {
    void aerospace$updateMagnet(BlockPos pos, BlockState state);
}
