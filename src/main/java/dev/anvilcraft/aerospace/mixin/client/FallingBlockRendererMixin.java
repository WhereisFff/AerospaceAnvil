package dev.anvilcraft.aerospace.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.aerospace.physics.FallingBlockOrientation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockRenderer.class)
abstract class FallingBlockRendererMixin {
    @Inject(
        method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private void aerospace$rotateModel(
        FallingBlockEntity entity, float yaw, float partialTick, PoseStack poses,
        MultiBufferSource buffers, int light, CallbackInfo ci
    ) {
        double center = entity.getBbHeight() / 2d;
        poses.translate(0, center, 0);
        poses.mulPose(((FallingBlockOrientation) entity).aerospace$getOrientation());
        poses.translate(0, -center, 0);
    }
}
