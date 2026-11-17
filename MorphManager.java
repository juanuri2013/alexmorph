package com.example.alexmorph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds who is morphed into what. The ACTIVE map is used on both logical sides:
 * the server fills it from saved data, the client fills it from sync packets.
 */
public final class MorphManager {
    /** Only mobs from this mod can be morphed into. Change it to support another mod. */
    public static final String NS = "alexsmobs";
    static final String KEY = "alexmorph";

    private static final Map<UUID, ResourceLocation> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Boolean> CHECKED = new ConcurrentHashMap<>();

    private MorphManager() {}

    // ---------- shared state ----------

    @Nullable
    public static ResourceLocation getActive(UUID player) {
        return ACTIVE.get(player);
    }

    public static void setActiveRaw(UUID player, @Nullable ResourceLocation id) {
        if (id == null) ACTIVE.remove(player);
        else ACTIVE.put(player, id);
    }

    // ---------- validation ----------

    public static boolean isMorphable(ResourceLocation id, Level level) {
        if (!id.getNamespace().equals(NS)) return false;
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return false;
        return CHECKED.computeIfAbsent(id, k -> {
            try {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(k);
                if (type == null) return false;
                Entity e = type.create(level);
                if (e == null) return false;
                boolean ok = e instanceof LivingEntity;
                e.discard();
                return ok;
            } catch (Throwable t) {
                MorphMod.LOGGER.warn("Could not test morph target {}", k, t);
                return false;
            }
        });
    }

    public static List<ResourceLocation> allMorphable(Level level) {
        return ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .filter(id -> isMorphable(id, level))
                .sorted()
                .toList();
    }

    // ---------- persistent data (server) ----------

    static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    public static List<ResourceLocation> unlocked(ServerPlayer p) {
        ListTag list = data(p).getList("Unlocked", Tag.TAG_STRING);
        List<ResourceLocation> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            // Ignore entries from removed/incompatible Alex's Mobs versions instead of
            // letting a stale saved ID break cycling or commands.
            if (id != null && isMorphable(id, p.serverLevel()) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** @return true if newly unlocked */
    public static boolean unlock(ServerPlayer p, ResourceLocation id) {
        if (!isMorphable(id, p.serverLevel()) || unlocked(p).contains(id)) return false;
        data(p).getList("Unlocked", Tag.TAG_STRING); // ensure type
        CompoundTag d = data(p);
        ListTag list = d.getList("Unlocked", Tag.TAG_STRING);
        list.add(StringTag.valueOf(id.toString()));
        d.put("Unlocked", list);
        checkProgress(p);
        return true;
    }

    // ---------- actions (server) ----------

    public static void setActive(ServerPlayer p, @Nullable ResourceLocation id) {
        if (id != null && !isMorphable(id, p.serverLevel())) {
            p.displayClientMessage(Component.literal("That creature is not a valid Alex's Mobs morph."), true);
            return;
        }
        boolean changed = !java.util.Objects.equals(getActive(p.getUUID()), id);
        setActiveRaw(p.getUUID(), id);
        CompoundTag d = data(p);
        if (id == null) d.remove("Active");
        else d.putString("Active", id.toString());
        broadcast(p);
        p.refreshDimensions();
        MorphAbilities.apply(p);
        onMorphChanged(p, id, changed);
    }

    /** Collection milestones, based on how many morphs are unlocked. */
    public static void checkProgress(ServerPlayer p) {
        int n = unlocked(p).size();
        if (n >= 1) award(p, "first_unlock");
        if (n >= 5) award(p, "collector_5");
        if (n >= 15) award(p, "collector_15");
        if (n >= 30) {
            award(p, "collector_30");
            int total = allMorphable(p.serverLevel()).size();
            if (total > 0 && n >= total) award(p, "collector_all");
        }
    }

    /** Branch advancements (ability morphs) and the switch counter. */
    private static void onMorphChanged(ServerPlayer p, @Nullable ResourceLocation id, boolean changed) {
        // Count every actual form change, including returning to the normal human form.
        if (changed) {
            CompoundTag d = data(p);
            int switches = d.getInt("Switches") + 1;
            d.putInt("Switches", switches);
            if (switches >= 25) award(p, "identity_crisis");
        }

        // The remaining advancements require an active creature morph.
        if (id == null) return;
        award(p, "first_unlock"); // root of the tab, so children are visible
        award(p, "first_morph");
        MorphAbilities.Traits tr = MorphAbilities.traits(id, p.serverLevel());
        if (tr.aquatic()) award(p, "aquatic");
        if (tr.fireImmune()) award(p, "fireproof");
        if (tr.flies()) award(p, "aerial");
    }

    /** Grants one of this mod's (criteria-less "impossible") advancements. */
    public static void award(ServerPlayer p, String name) {
        var adv = p.server.getAdvancements().getAdvancement(new ResourceLocation(MorphMod.MODID, name));
        if (adv != null) p.getAdvancements().award(adv, "trigger");
    }

    public static void broadcast(ServerPlayer p) {
        MorphNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SyncMorphPacket(p.getUUID(), getActive(p.getUUID())));
    }

    /** Call on login, respawn and dimension change. */
    public static void onJoin(ServerPlayer p) {
        String s = data(p).getString("Active");
        ResourceLocation id = s.isEmpty() ? null : ResourceLocation.tryParse(s);
        if (id != null && !isMorphable(id, p.serverLevel())) {
            id = null;
            data(p).remove("Active");
        }
        setActiveRaw(p.getUUID(), id);
        broadcast(p);
        p.refreshDimensions();
        MorphAbilities.apply(p);
    }

    public static void cycle(ServerPlayer p, boolean forward) {
        List<ResourceLocation> list = unlocked(p);
        if (list.isEmpty()) {
            p.displayClientMessage(Component.literal("No morphs yet - kill an Alex's Mobs creature first."), true);
            return;
        }
        ResourceLocation cur = getActive(p.getUUID());
        int idx = cur == null ? -1 : list.indexOf(cur);
        int n = list.size() + 1; // slot 0 = normal player
        int slot = ((idx + 1) + (forward ? 1 : -1) + n) % n;
        ResourceLocation next = slot == 0 ? null : list.get(slot - 1);
        setActive(p, next);
        if (next == null) {
            p.displayClientMessage(Component.literal("Human"), true);
        } else {
            EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(next);
            p.displayClientMessage(t == null ? Component.literal(next.toString()) : t.getDescription(), true);
        }
    }
}
