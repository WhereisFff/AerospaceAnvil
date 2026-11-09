package dev.anvilcraft.aerospace.physics;

import org.joml.Quaternionf;

/**
 * 保存下落方块脱离结构时的模型朝向，使服务端放置和客户端渲染使用同一旋转。
 */
public interface FallingBlockOrientation {
    Quaternionf aerospace$getOrientation();

    void aerospace$setOrientation(Quaternionf orientation);
}
