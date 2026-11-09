package dev.anvilcraft.aerospace.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SableTestStructure implements AutoCloseable {
    private final ServerSubLevelContainer container;
    private ServerSubLevel subLevel;

    private SableTestStructure(ServerSubLevelContainer container, ServerSubLevel subLevel) {
        this.container = container;
        this.subLevel = subLevel;
    }

    static SableTestStructure assemble(
        GameTestHelper helper, BlockPos relativeAnchor, Map<BlockPos, BlockState> blocks
    ) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = Objects.requireNonNull(
            SubLevelContainer.getContainer(level), "测试世界没有 Sable 结构容器"
        );
        BlockPos anchor = helper.absolutePos(relativeAnchor);
        List<BlockPos> positions = new ArrayList<>();
        BlockPos min = anchor;
        BlockPos max = anchor;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos position = anchor.offset(entry.getKey());
            level.setBlock(position, entry.getValue(), 3);
            positions.add(position);
            min = new BlockPos(
                Math.min(min.getX(), position.getX()),
                Math.min(min.getY(), position.getY()),
                Math.min(min.getZ(), position.getZ())
            );
            max = new BlockPos(
                Math.max(max.getX(), position.getX()),
                Math.max(max.getY(), position.getY()),
                Math.max(max.getZ(), position.getZ())
            );
        }
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
            level, anchor, positions,
            new BoundingBox3i(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())
        );
        return new SableTestStructure(container, subLevel);
    }

    ServerSubLevel subLevel() {
        return this.subLevel;
    }

    BlockPos localPos(BlockPos offset) {
        return this.subLevel.getPlot().getCenterBlock().offset(offset);
    }

    RigidBodyHandle physics() {
        return this.container.physicsSystem().getPhysicsHandle(this.subLevel);
    }

    void rotate(Quaterniondc orientation) {
        this.subLevel.logicalPose().orientation().set(orientation);
        this.physics().teleport(this.subLevel.logicalPose().position(), orientation);
        this.subLevel.updateLastPose();
        this.subLevel.updateBoundingBox();
    }

    void advance(Vec3 movement) {
        this.subLevel.updateLastPose();
        this.subLevel.logicalPose().position().add(movement.x, movement.y, movement.z);
        this.physics().teleport(this.subLevel.logicalPose().position(), this.subLevel.logicalPose().orientation());
        this.subLevel.updateBoundingBox();
    }

    SableTestStructure reload() {
        var data = SubLevelSerializer.toData(this.subLevel, List.of());
        this.container.removeSubLevel(this.subLevel, SubLevelRemovalReason.UNLOADED);
        this.subLevel = Objects.requireNonNull(
            SubLevelSerializer.fullyLoad(this.container.getLevel(), data), "Sable 结构重新加载失败"
        );
        return this;
    }

    @Override
    public void close() {
        if (!this.subLevel.isRemoved()) {
            this.container.removeSubLevel(
                this.subLevel.getPlot().plotPos.x - this.container.getOrigin().x,
                this.subLevel.getPlot().plotPos.z - this.container.getOrigin().y,
                SubLevelRemovalReason.REMOVED
            );
        }
    }
}
