package com.example.alexmorph.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyMappings {
    public static final KeyMapping CYCLE = new KeyMapping(
            "key.alexmorph.cycle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.alexmorph");

    private KeyMappings() {}
}
