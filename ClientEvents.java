package com.example.alexmorph.client;

import com.example.alexmorph.CycleMorphPacket;
import com.example.alexmorph.MorphManager;
import com.example.alexmorph.MorphMod;
import com.example.alexmorph.MorphNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MorphMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        while (KeyMappings.CYCLE.consumeClick()) {
            MorphNetwork.CHANNEL.sendToServer(new CycleMorphPacket(!Screen.hasShiftDown()));
        }

        Set<UUID> alive = new HashSet<>();
        for (Player p : mc.level.players()) {
            alive.add(p.getUUID());
            MorphRenderer.tickMob(p);
            // Safety net: make sure hitbox matches the morph even if a sync packet raced the level load.
            if (p.tickCount % 20 == 0 && MorphManager.getActive(p.getUUID()) != null) p.refreshDimensions();
        }
        MorphRenderer.retainOnly(alive);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        if (id == null) return;
        if (MorphRenderer.render(p, id, e.getPartialTick(), e.getPoseStack(),
                e.getMultiBufferSource(), e.getPackedLight())) {
            e.setCanceled(true);
        }
    }

    /** Hide the first-person arm while morphed. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        if (id != null && !MorphRenderer.isBroken(id)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                MorphManager.setActiveRaw(player.getUUID(), null);
            }
        }
        MorphRenderer.clear();
    }
}
