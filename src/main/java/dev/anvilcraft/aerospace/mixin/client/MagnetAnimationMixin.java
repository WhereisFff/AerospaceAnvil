package dev.anvilcraft.aerospace.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.dubhe.anvilcraft.client.support.MagnetAnvilAnimation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MagnetAnvilAnimation.class, remap = false)
abstract class MagnetAnimationMixin {
    @WrapOperation(
        method = "onRender",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void aerospace$transformAnimation(
        PoseStack stack,
        double x,
        double y,
        double z,
        Operation<Void> original,
        @Local(argsOnly = true) RenderLevelStageEvent event
    ) {
        Vec3 camera = event.getCamera().getPosition();
        Vec3 local = new Vec3(x + camera.x + 0.5, y + camera.y, z + camera.z + 0.5);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(Sable.HELPER.getContaining(level, local) instanceof ClientSubLevel subLevel)) {
            original.call(stack, x, y, z);
            return;
        }
        Pose3dc pose = subLevel.renderPose();
        Vec3 world = pose.transformPosition(local);
        original.call(stack, world.x - camera.x, world.y - camera.y, world.z - camera.z);
        stack.mulPose(new Quaternionf(pose.orientation()));
        stack.scale((float) pose.scale().x(), (float) pose.scale().y(), (float) pose.scale().z());
        stack.translate(-0.5, 0, -0.5);
    }
}
