package com.example.alexmorph.client;

import com.example.alexmorph.MorphAbilities;
import com.example.alexmorph.MorphMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps one dummy client-side mob per morphed player and renders it with the mob's own renderer
 * in place of the player model. The dummy is ticked every client tick, so mods that drive their
 * animations from entity state (like Alex's Mobs) animate properly.
 */
public final class MorphRenderer {
    private record Entry(ResourceLocation id, LivingEntity mob, Level level) {}

    private static final Map<UUID, Entry> CACHE = new HashMap<>();
    /** Morph targets that failed to create or render; we fall back to the normal player model. */
    private static final Set<ResourceLocation> BROKEN = new HashSet<>();
    /** Morph targets whose tick() threw; they still render, just without ticked animations. */
    private static final Set<ResourceLocation> TICK_BROKEN = new HashSet<>();
    private static final Map<Class<?>, Optional<Method>> FLY_SETTERS = new HashMap<>();

    private MorphRenderer() {}

    public static void invalidate(UUID player) {
        Entry removed = CACHE.remove(player);
        if (removed != null) removed.mob().discard();
    }

    public static void clear() {
        for (Entry entry : CACHE.values()) entry.mob().discard();
        CACHE.clear();
    }

    public static void retainOnly(Collection<UUID> alive) {
        CACHE.entrySet().removeIf(entry -> {
            if (alive.contains(entry.getKey())) return false;
            entry.getValue().mob().discard();
            return true;
        });
    }

    public static boolean isBroken(ResourceLocation id) {
        return BROKEN.contains(id);
    }

    private static LivingEntity get(Player p, ResourceLocation id) {
        Entry e = CACHE.get(p.getUUID());
        if (e != null && e.id().equals(id) && e.level() == p.level()) return e.mob();
        if (e != null) {
            CACHE.remove(p.getUUID());
            e.mob().discard();
        }
        if (BROKEN.contains(id)) return null;
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.containsKey(id)
                    ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
            Entity ent = type == null ? null : type.create(p.level());
            if (!(ent instanceof LivingEntity le)) {
                BROKEN.add(id);
                return null;
            }
            if (le instanceof Mob m) m.setNoAi(true);
            le.setSilent(true);
            CACHE.put(p.getUUID(), new Entry(id, le, p.level()));
            return le;
        } catch (Throwable t) {
            BROKEN.add(id);
            MorphMod.LOGGER.error("Could not create morph model for {}", id, t);
            return null;
        }
    }

    /** Alex's Mobs flyers pick their flap/glide pose from setFlying(boolean); call it if the mob has it. */
    private static void setFlying(LivingEntity m, boolean value) {
        Optional<Method> om = FLY_SETTERS.computeIfAbsent(m.getClass(), c -> {
            try {
                return Optional.of(c.getMethod("setFlying", boolean.class));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
        om.ifPresent(method -> {
            try {
                method.invoke(m, value);
            } catch (Throwable ignored) {
                // not every setFlying is safe to call on a dummy; just skip it
            }
        });
    }

    /** Called every client tick for each player: ticks the dummy so its animations run. */
    public static void tickMob(Player p) {
        Entry e = CACHE.get(p.getUUID());
        if (e == null) return;
        if (e.level() != p.level()) {
            invalidate(p.getUUID());
            return;
        }
        LivingEntity m = e.mob();
        ResourceLocation id = e.id();
        boolean flier = MorphAbilities.traits(id, p.level()).flies();

        sync(p, m);
        m.setDeltaMovement(p.getDeltaMovement());
        if (flier) setFlying(m, !p.onGround() && !p.isInWater());

        if (!TICK_BROKEN.contains(id)) {
            try {
                m.tick();
            } catch (Throwable t) {
                TICK_BROKEN.add(id);
                MorphMod.LOGGER.warn("Ticking morph model {} failed; animations will be basic", id, t);
            }
        }

        // The dummy never runs travel(), so drive the limb-swing animation ourselves.
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        double dy = (flier || p.isInWater()) ? p.getY() - p.yo : 0;
        float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        m.walkAnimation.update(Math.min(speed * 4.0F, 1.0F), 0.4F);
    }

    private static void sync(Player p, LivingEntity m) {
        m.setPos(p.getX(), p.getY(), p.getZ());
        m.xo = p.xo; m.yo = p.yo; m.zo = p.zo;
        m.xOld = p.xOld; m.yOld = p.yOld; m.zOld = p.zOld;
        m.setYRot(p.getYRot());
        m.yRotO = p.yRotO;
        m.setXRot(p.getXRot());
        m.xRotO = p.xRotO;
        m.yBodyRot = p.yBodyRot;
        m.yBodyRotO = p.yBodyRotO;
        m.yHeadRot = p.yHeadRot;
        m.yHeadRotO = p.yHeadRotO;
        m.tickCount = p.tickCount;
        m.hurtTime = p.hurtTime;
        m.hurtDuration = p.hurtDuration;
        m.deathTime = p.deathTime;
        m.swinging = p.swinging;
        m.swingTime = p.swingTime;
        m.attackAnim = p.attackAnim;
        m.oAttackAnim = p.oAttackAnim;
        m.setOnGround(p.onGround());
        m.setInvisible(p.isInvisible());
        m.setPose(p.getPose());
        m.setSprinting(p.isSprinting());
        m.setShiftKeyDown(p.isShiftKeyDown());
        m.setSwimming(p.isSwimming());
    }

    /** @return true if the player model was replaced (caller should cancel vanilla rendering). */
    @SuppressWarnings("unchecked")
    public static boolean render(Player p, ResourceLocation id, float partialTick,
                                 PoseStack stack, MultiBufferSource buffers, int light) {
        LivingEntity mob = get(p, id);
        if (mob == null) return false;
        try {
            sync(p, mob);
            float yaw = Mth.lerp(partialTick, p.yRotO, p.getYRot());
            EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>)
                    Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
            renderer.render(mob, yaw, partialTick, stack, buffers, light);
            return true;
        } catch (Throwable t) {
            BROKEN.add(id);
            CACHE.remove(p.getUUID());
            MorphMod.LOGGER.error("Rendering morph {} failed, falling back to player model", id, t);
            return false;
        }
    }
}
