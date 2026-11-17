package com.example.alexmorph;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MorphNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MorphMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private MorphNetwork() {}

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, SyncMorphPacket.class,
                SyncMorphPacket::encode, SyncMorphPacket::decode, SyncMorphPacket::handle);
        CHANNEL.registerMessage(i++, CycleMorphPacket.class,
                CycleMorphPacket::encode, CycleMorphPacket::decode, CycleMorphPacket::handle);
    }
}
