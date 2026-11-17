package com.example.alexmorph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/** Forge-bus events that run on both sides or on the server. */
public class MorphEvents {

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        MorphCommands.register(e.getDispatcher());
    }

    /** Kill an Alex's Mobs creature -> unlock its morph. */
    @SubscribeEvent
    public void onKill(LivingDeathEvent e) {
        // getEntity() is the causing entity (normally the shooter for arrows), while
        // getDirectEntity() is the projectile itself. Fall back to Projectile#getOwner
        // for damage sources that do not expose the shooter as the causing entity.
        net.minecraft.world.entity.Entity attacker = e.getSource().getEntity();
        // Prefer the projectile owner when available. This covers arrows, tridents,
        // thrown potions and other Projectile subclasses even if the damage source's
        // causing-entity field is populated differently by another mod.
        if (e.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
        }
        if (!(attacker instanceof ServerPlayer p)) return;
        if (e.getEntity() instanceof Player) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType());
        if (id == null || !MorphManager.isMorphable(id, p.serverLevel())) return;
        if (MorphManager.unlock(p, id)) {
            MorphManager.award(p, "first_unlock");
            p.displayClientMessage(Component.literal("Unlocked morph: ")
                    .append(e.getEntity().getType().getDescription()), false);
        }
    }

    /** Keep unlocks + active morph across death / End return. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(MorphManager.KEY, Tag.TAG_COMPOUND)) {
            e.getEntity().getPersistentData().put(MorphManager.KEY, old.getCompound(MorphManager.KEY).copy());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) MorphManager.onJoin(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) MorphManager.onJoin(p);
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) MorphManager.onJoin(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        MorphManager.setActiveRaw(e.getEntity().getUUID(), null);
    }

    /** When another player comes into view, tell them what this player is morphed into. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer watcher) {
            ResourceLocation id = MorphManager.getActive(target.getUUID());
            // Always send the current state, including null, so a client cannot retain
            // an old morph state if it starts tracking this UUID again.
            MorphNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                    new SyncMorphPacket(target.getUUID(), id));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && p.tickCount % 20 == 0) {
            MorphAbilities.tickEffects(p);
        }
    }

    /** Hitbox + eye height follow the mob (clamped so huge mobs don't wedge you in walls). */
    @SubscribeEvent
    public void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getPose() == Pose.SLEEPING || e.getPose() == Pose.DYING) return;
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        if (id == null) return;
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (t == null) return;
        EntityDimensions d = t.getDimensions();
        float w = Math.max(0.4F, Math.min(d.width, 2.5F));
        float h = Math.max(0.4F, Math.min(d.height, 3.0F));
        e.setNewSize(EntityDimensions.scalable(w, h), false);
        // Player.getStandingEyeHeight is hard-coded (1.62), so set the eye height ourselves.
        e.setNewEyeHeight(h * 0.85F);
    }

    /** Flying morphs don't take fall damage. */
    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ResourceLocation id = MorphManager.getActive(p.getUUID());
        if (id != null && MorphAbilities.traits(id, p.level()).flies()) {
            e.setDamageMultiplier(0.0F);
        }
    }
}
