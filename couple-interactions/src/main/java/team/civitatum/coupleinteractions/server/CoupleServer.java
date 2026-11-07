package team.civitatum.coupleinteractions.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.coupleinteractions.CarryHooks;
import team.civitatum.coupleinteractions.CoupleConfig;
import team.civitatum.coupleinteractions.CoupleInteractions;
import team.civitatum.coupleinteractions.core.Interaction;
import team.civitatum.coupleinteractions.core.PlayerPreferences;
import team.civitatum.coupleinteractions.core.Consent;
import team.civitatum.coupleinteractions.core.RequestBook;
import team.civitatum.coupleinteractions.network.CoupleNetwork;

/** All mutations run on the logical server thread. No persistent mount survives a session. */
public final class CoupleServer {
    private static RequestBook requests = new RequestBook();
    private static final Map<UUID, Pair> pairs = new HashMap<>();
    private static final Map<UUID, Long> lastRelease = new HashMap<>();
    // Preferences are applied at most once per PREFERENCES_INTERVAL; the newest early upload waits for its turn.
    private static final int PREFERENCES_INTERVAL = 10;
    private static final Map<UUID, Long> lastPreferences = new HashMap<>();
    private static final Map<UUID, CoupleNetwork.Preferences> pendingPreferences = new HashMap<>();
    private static long ticks;
    private static final String CARRY_MARK = "CivitatumCoupleCarried";
    private static final String PREFERENCES = "CivitatumCouplePreferences";
    /**
     * Parsed preferences by player, with the stored JSON they were parsed from. The carry pose is read on every
     * {@code positionRider}, twice a tick per pair; the JSON is parsed again only after it changes. The returned
     * objects are shared and must not be modified.
     */
    private static final Map<UUID, CachedPreferences> preferenceCache = new HashMap<>();
    private record CachedPreferences(String json, PlayerPreferences preferences) {}
    private static final String DENY_CARRY = "CivitatumCoupleDenyCarry";
    private static final String DENY_KISS = "CivitatumCoupleDenyKiss";

    private static final class Pair {
        final ServerPlayer carrier;
        final ServerPlayer passenger;
        Vec3 lastSafe;
        Vec3 previousCarrier;
        boolean closing;
        int obstructedTicks;
        Pair(ServerPlayer carrier, ServerPlayer passenger) {
            this.carrier = carrier; this.passenger = passenger;
            previousCarrier = carrier.position();
            lastSafe = passenger.position();
        }
    }

    public static UUID carrierOf(UUID passenger) {
        Pair p = pairs.get(passenger);
        return p != null && p.passenger.getUUID().equals(passenger) ? p.carrier.getUUID() : null;
    }
    public static UUID passengerOf(UUID carrier) {
        Pair p = pairs.get(carrier);
        return p != null && p.carrier.getUUID().equals(carrier) ? p.passenger.getUUID() : null;
    }

    public static PlayerPreferences preferencesOf(Player p) {
        var data = p.getPersistentData();
        String json = data.getString(PREFERENCES);
        if (json.isEmpty()) return PlayerPreferences.legacy(!data.getBoolean(DENY_CARRY), !data.getBoolean(DENY_KISS));
        var cached = preferenceCache.get(p.getUUID());
        if (cached != null && cached.json.equals(json)) return cached.preferences;
        var parsed = PlayerPreferences.read(json);
        preferenceCache.put(p.getUUID(), new CachedPreferences(json, parsed));
        return parsed;
    }
    public static void request(ServerPlayer sender, CoupleNetwork.Action packet) {
        Interaction action = Interaction.fromWire(packet.action());
        if (action == null) return;
        if (action == Interaction.RELEASE) {
            // RELEASE always acts on the sender's own pair; the supplied target has no authority.
            if (lastRelease.getOrDefault(sender.getUUID(), -1L) == ticks) return;
            lastRelease.put(sender.getUUID(), ticks);
            end(sender, "released");
            return;
        }
        if (!requests.rateLimit(sender.getUUID(), ticks, CoupleConfig.COOLDOWN_TICKS.get())) {
            message(sender, "cooldown"); return;
        }
        ServerPlayer target = sender.server.getPlayerList().getPlayer(packet.target());
        String invalid = validate(sender, target, action);
        if (invalid != null) { message(sender, invalid); return; }
        var preferences=preferencesOf(target);var policy=preferences.policy(action);
        if(policy==Consent.BLOCK){message(sender,"not_accepting");return;}
        if(requests.busy(sender.getUUID()) || requests.busy(target.getUUID())){message(sender,"busy_request");return;}
        if(policy.automatic(preferences.trusted.contains(sender.getUUID()))){startInteraction(sender,target,action);return;}
        int duration = CoupleConfig.REQUEST_SECONDS.get() * 20;
        RequestBook.Request request = requests.create(sender.getUUID(), target.getUUID(), action, ticks + duration);
        if (request == null) { message(sender, "busy_request"); return; }
        send(target, new CoupleNetwork.Invitation(request.id(), sender.getUUID(), sender.getGameProfile().getName(), action.ordinal(), duration));
        message(sender, "sent", target.getDisplayName());
    }

    public static void decide(ServerPlayer recipient, CoupleNetwork.Decision decision) {
        RequestBook.Request request = requests.decide(decision.request(), recipient.getUUID(), ticks);
        if (request == null) { send(recipient, new CoupleNetwork.ClearInvitation(decision.request())); return; }
        send(recipient, new CoupleNetwork.ClearInvitation(request.id()));
        ServerPlayer sender = recipient.server.getPlayerList().getPlayer(request.sender());
        if (sender == null) return;
        if (!decision.accept()) { message(sender, "declined", recipient.getDisplayName()); return; }
        // A menu may remain open while players move, die, sleep or change dimensions.
        String invalid = validate(sender, recipient, request.action());
        if (invalid != null) { message(sender, invalid); message(recipient, invalid); return; }
        if(preferencesOf(recipient).policy(request.action())==Consent.BLOCK){message(sender,"not_accepting");return;}
        startInteraction(sender,recipient,request.action());
    }
    private static void startInteraction(ServerPlayer sender,ServerPlayer recipient,Interaction action){
        boolean success=action==Interaction.CARRY?beginCarry(sender,recipient):SocialSessions.begin(sender,recipient,action);
        if(!success){message(sender,"mount_failed");message(recipient,"mount_failed");}
        else {cancelRequest(sender);cancelRequest(recipient);if(action!=Interaction.CARRY){message(sender,"started_"+action.name().toLowerCase(java.util.Locale.ROOT),recipient.getDisplayName());message(recipient,"started_"+action.name().toLowerCase(java.util.Locale.ROOT),sender.getDisplayName());}}
    }
    public static void preferences(ServerPlayer player,CoupleNetwork.Preferences packet){
        if(ticks-lastPreferences.getOrDefault(player.getUUID(),Long.MIN_VALUE/2)<PREFERENCES_INTERVAL){pendingPreferences.put(player.getUUID(),packet);return;}
        lastPreferences.put(player.getUUID(),ticks);pendingPreferences.remove(player.getUUID());
        var p = PlayerPreferences.read(packet.data());
        player.getPersistentData().putString(PREFERENCES, p.json());
        cancelRequest(player);
        var social = SocialSessions.state(player.getUUID());
        if (social != null && p.policy(Interaction.fromWire(social.action())) == Consent.BLOCK) {
            SocialSessions.end(player.getUUID());
        }
        var carry = pairs.get(player.getUUID());
        if (carry != null) {
            if (p.policy(Interaction.CARRY) == Consent.BLOCK) end(player, "released");
            else broadcast(carry, true);
        }
    }

    public static String validate(ServerPlayer sender, ServerPlayer target, Interaction action) {
        if (target == null || target == sender || sender.isRemoved() || target.isRemoved()) return "missing_player";
        if (!sender.isAlive() || !target.isAlive() || sender.isSpectator() || target.isSpectator()) return "unavailable";
        if (sender.level() != target.level()) return "too_far";
        double reach = CoupleConfig.REACH.get();
        if (sender.distanceToSqr(target) > reach * reach || !sender.hasLineOfSight(target)) return "too_far";
        if (sender.isSleeping() || target.isSleeping()) return "unavailable";
        boolean allowed=switch(action){case CARRY->CoupleConfig.ALLOW_CARRY.get();case KISS->CoupleConfig.ALLOW_KISS.get();case HUG->CoupleConfig.ALLOW_HUG.get();case REST->CoupleConfig.ALLOW_REST.get();default->false;};
        if(!allowed)return "disabled";
        if(SocialSessions.active(sender.getUUID()) || SocialSessions.active(target.getUUID()))return "already_carrying";
        if(action==Interaction.KISS && (CarryHooks.pair(sender,target) || CarryHooks.pair(target,sender)))return null;
        if (pairs.containsKey(sender.getUUID()) || pairs.containsKey(target.getUUID())
                || sender.isPassenger() || target.isPassenger() || sender.isVehicle() || target.isVehicle()) return "already_carrying";
        if (!sender.onGround() || !target.onGround() || sender.isInWaterOrBubble() || target.isInWaterOrBubble()
                || sender.isInLava() || target.isInLava() || sender.isFallFlying() || target.isFallFlying()
                || sender.getAbilities().flying || target.getAbilities().flying) return "ground_only";
        return null;
    }

    /** Also used by integration tests. Preconditions are checked even for internal callers. */
    public static boolean beginCarry(ServerPlayer carrier, ServerPlayer passenger) {
        if (validate(carrier, passenger, Interaction.CARRY) != null) return false;
        Pair pair = new Pair(carrier, passenger);
        pairs.put(carrier.getUUID(), pair);
        pairs.put(passenger.getUUID(), pair);
        passenger.getPersistentData().putBoolean(CARRY_MARK, true);
        passenger.refreshDimensions();
        carrier.stopUsingItem();
        passenger.stopUsingItem();
        carrier.setSprinting(false);
        if (!passenger.startRiding(carrier, true)) {
            finish(pair); return false;
        }
        var speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CoupleInteractions.id("carrying"));
            speed.addTransientModifier(new AttributeModifier(CoupleInteractions.id("carrying"),
                    CoupleConfig.CARRY_SPEED.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        cancelRequest(carrier);
        cancelRequest(passenger);
        broadcast(pair, true);
        message(carrier, "carrying", passenger.getDisplayName());
        message(passenger, "carried", carrier.getDisplayName());
        return true;
    }

    public static void end(Player member, String reason) {
        SocialSessions.end(member.getUUID());
        Pair pair = pairs.get(member.getUUID());
        if (pair == null || pair.closing) return;
        pair.closing = true;
        if (pair.passenger.getVehicle() == pair.carrier) pair.passenger.stopRiding();
        // Respect a third-party mount veto: retain the pair until it is actually detached.
        if (pair.passenger.getVehicle() == pair.carrier) {
            pair.closing = false;
            message(pair.passenger, "release_blocked");
            return;
        }
        finish(pair);
        if (reason != null) { message(pair.carrier, reason); message(pair.passenger, reason); }
    }

    private static void finish(Pair pair) {
        pairs.remove(pair.carrier.getUUID(), pair);
        pairs.remove(pair.passenger.getUUID(), pair);
        pair.passenger.getPersistentData().remove(CARRY_MARK);
        pair.passenger.refreshDimensions();
        var speed = pair.carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CoupleInteractions.id("carrying"));
        broadcast(pair, false);
    }

    public static Vec3 dismount(Player carrier, Player passenger) {
        Pair pair = pairs.get(passenger.getUUID());
        if (pair == null || pair.carrier != carrier) return carrier.position();
        Vec3 nearby = findDismount(carrier, passenger);
        if (nearby != null) return nearby;
        if (safeStanding(passenger, pair.lastSafe)) return pair.lastSafe;
        // No ground nearby (for example, release above a ravine): preserve physical falling.
        Vec3 foot = carrier.position();
        if (safeStanding(passenger, foot)) return foot;
        for (int dy = 1; dy <= 4; dy++) {
            Vec3 above = foot.add(0, dy, 0);
            if (safeStanding(passenger, above)) return above;
        }
        return foot;
    }

    public static Vec3 findDismount(Player carrier, Player passenger) {
        BlockPos origin = carrier.blockPosition();
        for (int radius = 1; radius <= 2; radius++) {
            for (int dy : new int[]{0, 1, -1, -2}) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!carrier.level().hasChunkAt(pos)) continue;
                    Vec3 candidate = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, carrier.level(), pos, true);
                    if (candidate != null && safeStanding(passenger, candidate)
                            && passenger.level().getFluidState(pos).isEmpty()) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean safeStanding(Player player, Vec3 pos) {
        if (!player.level().hasChunkAt(BlockPos.containing(pos))) return false;
        var box = EntityType.PLAYER.getDimensions().scale(player.getScale()).makeBoundingBox(pos);
        return player.level().getWorldBorder().isWithinBounds(box) && player.level().noCollision(player, box);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        ticks++;SocialSessions.tick();
        for (RequestBook.Request request : requests.expire(ticks)) {
            ServerPlayer recipient = event.getServer().getPlayerList().getPlayer(request.recipient());
            ServerPlayer sender = event.getServer().getPlayerList().getPlayer(request.sender());
            if (recipient != null) send(recipient, new CoupleNetwork.ClearInvitation(request.id()));
            if (sender != null) message(sender, "expired");
        }
        lastRelease.entrySet().removeIf(e -> e.getValue() < ticks - 20);
        for (var entry : List.copyOf(pendingPreferences.entrySet())) {
            if (ticks - lastPreferences.getOrDefault(entry.getKey(), Long.MIN_VALUE / 2) < PREFERENCES_INTERVAL) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) pendingPreferences.remove(entry.getKey()); else preferences(player, entry.getValue());
        }
        lastPreferences.entrySet().removeIf(e -> e.getValue() < ticks - PREFERENCES_INTERVAL && !pendingPreferences.containsKey(e.getKey()));
        for (Pair pair : new HashSet<>(pairs.values())) {
            var a = pair.carrier; var b = pair.passenger;
            if (a.isRemoved() || b.isRemoved() || !a.isAlive() || !b.isAlive() || a.level() != b.level()
                    || b.getVehicle() != a || a.isPassenger() || a.isSpectator() || b.isSpectator()
                    || a.isSleeping() || b.isSleeping() || a.isFallFlying() || a.getAbilities().flying
                    || a.isInWaterOrBubble() || a.isInLava() || a.position().distanceToSqr(pair.previousCarrier) > 256) {
                end(a, null); continue;
            }
            a.setSprinting(false);
            a.stopUsingItem(); b.stopUsingItem();
            b.setSwimming(false);
            if (b.getPose() != Pose.STANDING) b.setPose(Pose.STANDING);
            a.positionRider(b);
            pair.previousCarrier = a.position();
            if (ticks % 5 == 0) {
                Vec3 safe = findDismount(a, b);
                if (safe != null) pair.lastSafe = safe;
            }
            if (obstructed(pair)) end(a, "obstructed");
        }
    }

    private static boolean obstructed(Pair pair) {
        var mode = CoupleConfig.CARRY_OBSTACLE_MODE.get();
        if (mode == CoupleConfig.ObstacleMode.OFF) { pair.obstructedTicks = 0; return false; }
        var passenger = pair.passenger;
        var box = passenger.getBoundingBox().deflate(0.02);
        if (mode == CoupleConfig.ObstacleMode.STRICT) {
            pair.obstructedTicks = 0;
            return !passenger.level().noCollision(passenger, box);
        }
        double inset = CoupleConfig.CARRY_OBSTACLE_TOLERANCE.get() * passenger.getScale();
        double xInset = Math.min(inset, (box.maxX - box.minX) * 0.4);
        double zInset = Math.min(inset, (box.maxZ - box.minZ) * 0.4);
        // Feet, hands and a one-block step may touch while the carrier approaches or jumps.
        // Only sustained obstruction of the upper central body triggers a relaxed release.
        double floor = pair.carrier.getY() + Math.max(1.05, 1.05 * pair.carrier.getScale());
        double bottom = Math.max(box.minY + Math.min(inset, 0.15 * passenger.getScale()), floor);
        if (bottom >= box.maxY) { pair.obstructedTicks = 0; return false; }
        var probe = new net.minecraft.world.phys.AABB(box.minX + xInset, bottom, box.minZ + zInset,
                box.maxX - xInset, box.maxY, box.maxZ - zInset);
        if (passenger.level().noCollision(passenger, probe)) { pair.obstructedTicks = 0; return false; }
        return ++pair.obstructedTicks >= CoupleConfig.CARRY_OBSTACLE_GRACE.get();
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getPersistentData().getBoolean(CARRY_MARK)) {
            p.stopRiding(); p.getPersistentData().remove(CARRY_MARK); p.refreshDimensions();
        }
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {speed.removeModifier(CoupleInteractions.id("carrying"));speed.removeModifier(CoupleInteractions.id("social_pose"));}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            cancelRequest(p); end(p, null); requests.forget(p.getUUID());
            lastPreferences.remove(p.getUUID()); pendingPreferences.remove(p.getUUID());
            preferenceCache.remove(p.getUUID());
        }
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        Player p = event.getEntity(), old = event.getOriginal();
        p.getPersistentData().putBoolean(DENY_CARRY, old.getPersistentData().getBoolean(DENY_CARRY));
        p.getPersistentData().putBoolean(DENY_KISS, old.getPersistentData().getBoolean(DENY_KISS));
        p.getPersistentData().putString(PREFERENCES, preferencesOf(old).json());
        if (old instanceof ServerPlayer s) { cancelRequest(s); end(s, null); }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof Player target) {
            Pair pair = pairs.get(target.getUUID());
            if (pair != null) send(watcher, new CoupleNetwork.CarryState(pair.carrier.getUUID(), pair.passenger.getUUID(), true,preferencesOf(pair.passenger).pose.json()));
            SocialSessions.tracking(watcher,target.getUUID());
        }
    }
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) beforeTeleport(p);
    }
    public static void beforeTeleport(ServerPlayer p) { if(SocialSessions.positioning())return;cancelRequest(p);end(p,null); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) { cancelRequest(p); end(p, null); }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getNewDamage() > 0) end(p, "hurt");
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer carrier)) return;
        Pair p = pairs.get(carrier.getUUID());
        if (p == null || p.carrier != carrier || p.closing) return;
        // Vanilla passengers reset fallDistance; explicitly apply their own fall protection.
        p.passenger.causeFallDamage(event.getDistance(), event.getDamageMultiplier(), carrier.damageSources().fall());
    }
    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide) return;
        if (event.getEntityMounting() instanceof Player a && CarryHooks.occupied(a)) {
            if (!(event.getEntityBeingMounted() instanceof Player b && CarryHooks.pair(b, a))) event.setCanceled(true);
        } else if (event.getEntityBeingMounted() instanceof Player b && CarryHooks.occupied(b)) event.setCanceled(true);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (CarryHooks.occupied(event.getEntity())) event.setCanceled(true);
    }
    private static void interact(PlayerInteractEvent event) {
        if (CarryHooks.occupied(event.getEntity()) && event instanceof ICancellableEvent cancel) cancel.setCanceled(true);
    }
    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) { interact(event); }
    @SubscribeEvent public static void rightClickItem(PlayerInteractEvent.RightClickItem event) { interact(event); }
    @SubscribeEvent public static void entityInteract(PlayerInteractEvent.EntityInteract event) { interact(event); }
    @SubscribeEvent public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) { interact(event); }
    @SubscribeEvent public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) { interact(event); }
    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent event) {
        if (CarryHooks.occupied(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent public static void start(ServerAboutToStartEvent event) { clear(); }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (Pair pair : new HashSet<>(pairs.values())) end(pair.carrier, null);
        clear();
    }
    private static void clear() {
        SocialSessions.clear();
        pairs.clear();
        requests = new RequestBook();
        lastRelease.clear();
        lastPreferences.clear();
        pendingPreferences.clear();
        preferenceCache.clear();
        ticks = 0;
    }

    private static void cancelRequest(ServerPlayer member) {
        RequestBook.Request request = requests.cancel(member.getUUID());
        if (request == null) return;
        ServerPlayer recipient = member.server.getPlayerList().getPlayer(request.recipient());
        if (recipient != null) send(recipient, new CoupleNetwork.ClearInvitation(request.id()));
    }
    private static void broadcast(Pair pair, boolean active) {
        var packet = new CoupleNetwork.CarryState(pair.carrier.getUUID(), pair.passenger.getUUID(), active,preferencesOf(pair.passenger).pose.json());
        // State transitions only. All clients receive cleanup even if they stopped tracking the pair.
        for (ServerPlayer p : pair.carrier.server.getPlayerList().getPlayers()) send(p, packet);
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof FakePlayer) && player.connection != null && !player.hasDisconnected()) PacketDistributor.sendToPlayer(player, payload);
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        if (!(player instanceof FakePlayer) && player.connection != null && !player.hasDisconnected()) {
            player.displayClientMessage(Component.translatable("message.civitatum_couple." + key, args), true);
        }
    }
    private CoupleServer() {}
}
