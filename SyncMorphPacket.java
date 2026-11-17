package com.example.alexmorph;

import com.example.alexmorph.client.ClientMorphHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: tells clients which mob (or none) a player is morphed into. */
public record SyncMorphPacket(UUID player, @Nullable ResourceLocation id) {

    public static void encode(SyncMorphPacket m, FriendlyByteBuf b) {
        b.writeUUID(m.player);
        b.writeBoolean(m.id != null);
        if (m.id != null) b.writeResourceLocation(m.id);
    }

    public static SyncMorphPacket decode(FriendlyByteBuf b) {
        UUID u = b.readUUID();
        ResourceLocation r = b.readBoolean() ? b.readResourceLocation() : null;
        return new SyncMorphPacket(u, r);
    }

    public static void handle(SyncMorphPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMorphHandler.apply(m.player(), m.id())));
        c.setPacketHandled(true);
    }
}
