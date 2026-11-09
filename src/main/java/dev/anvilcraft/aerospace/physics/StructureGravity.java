package dev.anvilcraft.aerospace.physics;

import dev.anvilcraft.aerospace.AerospaceAnvil;
import dev.anvilcraft.aerospace.mixin.accessor.GravitySourceAccess;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在结构质心采样铁砧工艺的局部引力，并按 Sable 物理子步施加冲量。
 */
@EventBusSubscriber(modid = AerospaceAnvil.MOD_ID)
public final class StructureGravity {
    private static final Map<ServerLevel, Map<BlockPos, GravityManager.GravitySource>> MOVING_SOURCES = new HashMap<>();

    private StructureGravity() {
    }

    public static void updateSource(Level level, BlockPos id, Vec3 center, GravityManager.GravitySourceType type) {
        if (!(level instanceof ServerLevel serverLevel) || !type.isValid()
            || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            return;
        }
        if (Sable.HELPER.getContaining(level, id) == null) {
            removeSource(level, id);
            return;
        }
        MOVING_SOURCES.computeIfAbsent(serverLevel, ignored -> new HashMap<>())
            .put(id.immutable(), new GravityManager.GravitySource(id.immutable(), center, type));
    }

    public static void removeSource(Level level, BlockPos id) {
        Map<BlockPos, GravityManager.GravitySource> sources = MOVING_SOURCES.get(level);
        if (sources != null) {
            sources.remove(id);
            if (sources.isEmpty()) {
                MOVING_SOURCES.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        MOVING_SOURCES.remove(event.getLevel());
    }

    @SubscribeEvent
    public static void onPhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        SubLevelPhysicsSystem system = event.getPhysicsSystem();
        ServerLevel level = system.getLevel();
        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        List<MovingSource> sources = movingSources(level);
        // 局部引力沿用 Sable 的基准强度，零重力维度中的天体仍可产生引力。
        double referenceGravity = DimensionPhysics.DEFAULT_GRAVITY.length();
        for (ServerSubLevel target : container.getAllSubLevels()) {
            double mass = target.getMassTracker().getMass();
            Vector3dc localCenter = target.getMassTracker().getCenterOfMass();
            if (target.isRemoved() || mass <= 0 || localCenter == null) {
                continue;
            }
            Vec3 position = target.logicalPose().transformPosition(new Vec3(localCenter.x(), localCenter.y(), localCenter.z()));
            Vec3 acceleration = GravityManager.GravitySourceManager.calculateGravityVector(level, position, referenceGravity);
            for (MovingSource source : sources) {
                SubLevel owner = source.owner;
                if (owner == target || position.distanceToSqr(source.worldCenter) > source.radius * source.radius) {
                    continue;
                }
                Vec3 localPosition = owner.logicalPose().transformPositionInverse(position);
                Vec3 localGravity = GravitySourceAccess.aerospace$calculate(source.source, localPosition, referenceGravity);
                acceleration = acceleration.add(owner.logicalPose().transformNormal(localGravity));
            }
            if (acceleration.lengthSqr() == 0) {
                continue;
            }
            // 原生管线已经施加维度重力，此处只加入局部引力；刚体接口接收局部坐标冲量。
            Vector3d impulse = new Vector3d(acceleration.x, acceleration.y, acceleration.z).mul(mass * event.getTimeStep());
            target.logicalPose().orientation().transformInverse(impulse);
            system.getPhysicsHandle(target).applyLinearImpulse(impulse);
        }
    }

    private static List<MovingSource> movingSources(ServerLevel level) {
        List<MovingSource> fields = new ArrayList<>();
        Map<BlockPos, GravityManager.GravitySource> sources = MOVING_SOURCES.get(level);
        if (sources == null) {
            return fields;
        }
        for (GravityManager.GravitySource source : sources.values()) {
            SubLevel owner = Sable.HELPER.getContaining(level, source.id());
            if (owner == null || owner.isRemoved()) {
                continue;
            }
            Vec3 center = owner.logicalPose().transformPosition(source.center());
            Vector3dc scale = owner.logicalPose().scale();
            double radius = source.type().radius() * Math.max(Math.abs(scale.x()), Math.max(Math.abs(scale.y()), Math.abs(scale.z())));
            fields.add(new MovingSource(owner, source, center, radius));
        }
        return fields;
    }

    private record MovingSource(SubLevel owner, GravityManager.GravitySource source, Vec3 worldCenter, double radius) {
    }
}
