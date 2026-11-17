package com.example.alexmorph;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: keybind pressed, go to next/previous unlocked morph. */
public record CycleMorphPacket(boolean forward) {

    public static void encode(CycleMorphPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.forward);
    }

    public static CycleMorphPacket decode(FriendlyByteBuf b) {
        return new CycleMorphPacket(b.readBoolean());
    }

    public static void handle(CycleMorphPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> {
            if (sender != null) MorphManager.cycle(sender, m.forward());
        });
        c.setPacketHandled(true);
    }
}
