package dev.anvilcraft.aerospace.mixin.accessor;

import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 复用单个天体的原始引力公式，防止把相邻存储区中另一艘船的引力源按错误姿态计入。
 */
@Mixin(value = GravityManager.GravitySourceManager.class, remap = false)
public interface GravitySourceAccess {
    @Invoker("calculateGravityVector")
    static Vec3 aerospace$calculate(GravityManager.GravitySource source, Vec3 position, double gravity) {
        throw new AssertionError("Mixin 引力访问器未应用");
    }
}
