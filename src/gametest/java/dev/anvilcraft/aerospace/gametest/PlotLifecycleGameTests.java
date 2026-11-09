package dev.anvilcraft.aerospace.gametest;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * 验证同一游戏刻重新组装结构时读取当前存储区，而不复用已卸载的区块对象。
 */
@GameTestHolder(AerospaceAnvil.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlotLifecycleGameTests {
    @GameTest(template = "empty")
    public static void reusedPlotUsesCurrentChunk(GameTestHelper helper) {
        BlockPos previousPosition;
        LevelChunk previousChunk;
        try (SableTestStructure previous = SableTestStructure.assemble(helper, new BlockPos(5, 5, 7),
            Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))) {
            previousPosition = previous.localPos(BlockPos.ZERO);
            previousChunk = helper.getLevel().getChunkAt(previousPosition);
        }
        try (SableTestStructure replacement = SableTestStructure.assemble(helper, new BlockPos(10, 5, 7),
            Map.of(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState()))) {
            BlockPos position = replacement.localPos(BlockPos.ZERO);
            helper.assertTrue(position.equals(previousPosition), "测试没有复用同一结构存储区");
            ChunkPos chunkPos = new ChunkPos(position);
            LevelChunk current = helper.getLevel().getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            helper.assertTrue(current != null && current != previousChunk, "结构没有创建新的区块对象");
            helper.assertTrue(helper.getLevel().getChunkAt(position) == current, "原版快速缓存仍返回上一结构的区块");
            helper.assertTrue(current.getBlockState(position).is(Blocks.GOLD_BLOCK), "组装方块被写入已卸载的旧区块");
        }
        helper.succeed();
    }
}
