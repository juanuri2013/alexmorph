package com.example.alexmorph.client;

import com.example.alexmorph.MorphManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Runs on the client when a SyncMorphPacket arrives. */
public final class ClientMorphHandler {
    private ClientMorphHandler() {}

    public static void apply(UUID uuid, ResourceLocation id) {
        MorphManager.setActiveRaw(uuid, id);
        MorphRenderer.invalidate(uuid);
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            Player p = level.getPlayerByUUID(uuid);
            if (p != null) p.refreshDimensions();
        }
    }
}
