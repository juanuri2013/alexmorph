package com.example.alexmorph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gameplay perks that come from the mob you are morphed into.
 * Traits are read from the mob itself (its AI/navigation, category, attributes), so every
 * Alex's Mobs creature gets sensible abilities without a hand-written list.
 */
public final class MorphAbilities {
    /** Always treated as flyers, even if auto-detection misses them. */
    private static final Set<String> FORCE_FLY = Set.of(
            "crow", "bald_eagle", "hummingbird", "sunbird", "soul_vulture",
            "fly", "crimson_mosquito", "tarantula_hawk");

    private static final UUID SPEED_ID = UUID.fromString("5b0f2a1e-3c1d-4f6a-9d0e-a1e0c0ffee01");
    private static final UUID HEALTH_ID = UUID.fromString("5b0f2a1e-3c1d-4f6a-9d0e-a1e0c0ffee02");

    public record Traits(boolean flies, boolean aquatic, boolean fireImmune, double speed, double health) {
        static final Traits NONE = new Traits(false, false, false, 0.25D, 20.0D);
    }

    private static final Map<ResourceLocation, Traits> CACHE = new ConcurrentHashMap<>();

    private MorphAbilities() {}

    public static Traits traits(ResourceLocation id, Level level) {
        Traits t = CACHE.get(id);
        if (t == null) {
            t = compute(id, level);
            CACHE.put(id, t);
        }
        return t;
    }

    private static Traits compute(ResourceLocation id, Level level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) return Traits.NONE;
        boolean flies = FORCE_FLY.contains(id.getPath());
        MobCategory c = type.getCategory();
        boolean aquatic = c == MobCategory.WATER_CREATURE || c == MobCategory.WATER_AMBIENT
                || c == MobCategory.UNDERGROUND_WATER_CREATURE || c == MobCategory.AXOLOTLS;
        boolean fire = type.fireImmune();
        double speed = 0.25D;
        double health = 20.0D;
        Entity e = null;
        try {
            e = type.create(level);
            if (e instanceof LivingEntity le) {
                AttributeInstance s = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (s != null) speed = s.getBaseValue();
                AttributeInstance h = le.getAttribute(Attributes.MAX_HEALTH);
                if (h != null) health = h.getBaseValue();
            }
            if (e instanceof FlyingAnimal || e instanceof FlyingMob) flies = true;
            if (e instanceof WaterAnimal) aquatic = true;
            if (e instanceof Mob m) {
                MoveControl mc = m.getMoveControl();
                PathNavigation nav = m.getNavigation();
                String n = mc.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                if (mc instanceof FlyingMoveControl || nav instanceof FlyingPathNavigation
                        || n.contains("fly") || n.contains("flight")) {
                    flies = true;
                }
                if (nav instanceof WaterBoundPathNavigation) aquatic = true;
            }
        } catch (Throwable t) {
            MorphMod.LOGGER.warn("Could not read traits of {}", id, t);
        } finally {
            if (e != null) e.discard();
        }
        if (aquatic) flies = false; // water mobs swim, they don't fly
        return new Traits(flies, aquatic, fire, speed, health);
    }

    /** Applies flight, speed and health for the player's current morph (or removes them). Safe to call often. */
    public static void apply(ServerPlayer p) {
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        Traits tr = id == null ? null : traits(id, p.serverLevel());

        // --- flight ---
        boolean fly = tr != null && tr.flies();
        boolean granted = MorphManager.data(p).getBoolean("GrantedFly");
        if (fly) {
            // Only mark flight as ours when we actually enable it. If another mod or
            // the player's game mode already granted flight, do not revoke it later.
            if (!p.getAbilities().mayfly) {
                p.getAbilities().mayfly = true;
                MorphManager.data(p).putBoolean("GrantedFly", true);
                p.onUpdateAbilities();
            }
        } else if (granted) {
            // Do not clear our ownership marker while a game mode itself grants flight.
            // If the player returns to Survival, the next apply() call will revoke only
            // the flight that AlexMorph originally granted.
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                MorphManager.data(p).putBoolean("GrantedFly", false);
                p.onUpdateAbilities();
            }
        }

        // --- speed: mob speed relative to a normal land mob (0.25), kept in a sane range ---
        double speedMod = tr == null ? 0.0D : Mth.clamp(tr.speed() / 0.25D - 1.0D, -0.3D, 0.5D);
        setModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, "Alex's Mobs Morph speed",
                speedMod, AttributeModifier.Operation.MULTIPLY_TOTAL);

        // --- health: follow the mob's max health, between 5 and 20 hearts ---
        double healthMod = tr == null ? 0.0D : Mth.clamp(tr.health(), 10.0D, 40.0D) - 20.0D;
        setModifier(p, Attributes.MAX_HEALTH, HEALTH_ID, "Alex's Mobs Morph health",
                healthMod, AttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void setModifier(ServerPlayer p, Attribute attr, UUID uuid, String name,
                                    double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(uuid);
        if (cur == null && Math.abs(amount) < 1.0E-4D) return;
        if (cur != null && Math.abs(cur.getAmount() - amount) < 1.0E-4D) return;
        if (cur != null) inst.removeModifier(uuid);
        if (Math.abs(amount) >= 1.0E-4D) inst.addTransientModifier(new AttributeModifier(uuid, name, amount, op));
    }

    /** Called once a second on the server. */
    public static void tickEffects(ServerPlayer p) {
        apply(p); // re-asserts flight/attributes (e.g. after respawn or a creative-mode toggle)
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        if (id == null) return;
        Traits tr = traits(id, p.serverLevel());
        if (tr.aquatic()) {
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, false));
            if (p.isInWater()) {
                p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 0, true, false, false));
            }
        }
        if (tr.fireImmune()) {
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false, false));
        }
    }
}
