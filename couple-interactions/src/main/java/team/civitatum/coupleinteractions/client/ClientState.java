package team.civitatum.coupleinteractions.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.coupleinteractions.CarryGeometry;
import team.civitatum.coupleinteractions.CarryHooks;
import team.civitatum.coupleinteractions.CoupleInteractions;
import team.civitatum.coupleinteractions.network.CoupleNetwork;
import team.civitatum.coupleinteractions.core.PoseSettings;

public final class ClientState {
    private static final Map<UUID, UUID> carrierByPassenger = new HashMap<>();
    private static final Map<UUID, UUID> passengerByCarrier = new HashMap<>();
    private static final Map<UUID,PoseSettings> poses=new HashMap<>();
    private static final Map<UUID,Social> socials=new HashMap<>();
    public record Social(CoupleNetwork.SocialState state,long started){}
    public static Social social(UUID id){return socials.get(id);}
    public static PoseSettings pose(UUID id){return poses.getOrDefault(id,PoseSettings.DEFAULT);}
    public static void social(CoupleNetwork.SocialState p){
        if(p.active()){var v=new Social(p,ticks-Math.max(0,p.elapsed()));socials.put(p.first(),v);socials.put(p.second(),v);}
        else {socials.computeIfPresent(p.first(),(id,v)->v.state.second().equals(p.second())?null:v);socials.computeIfPresent(p.second(),(id,v)->v.state.first().equals(p.first())?null:v);}
        refresh(p.first());refresh(p.second());
    }
    public static CoupleNetwork.Invitation invitation;
    public static long invitationEnd;
    public static long ticks;
    public static UUID carrierOf(UUID id) { return carrierByPassenger.get(id); }
    public static UUID passengerOf(UUID id) { return passengerByCarrier.get(id); }

    public static void carry(CoupleNetwork.CarryState state) {
        if (state.active()) {
            removeMember(state.carrier());
            removeMember(state.passenger());
            carrierByPassenger.put(state.passenger(), state.carrier());
            passengerByCarrier.put(state.carrier(), state.passenger());
            poses.put(state.passenger(),PoseSettings.read(state.pose()));
        } else {
            carrierByPassenger.remove(state.passenger(), state.carrier());
            passengerByCarrier.remove(state.carrier(), state.passenger());
            poses.remove(state.passenger());
        }
        refresh(state.passenger());
    }
    private static void removeMember(UUID member) {
        UUID carrier = carrierByPassenger.remove(member);
        if (carrier != null) { passengerByCarrier.remove(carrier, member); refresh(member); }
        UUID passenger = passengerByCarrier.remove(member);
        if (passenger != null) { carrierByPassenger.remove(passenger, member); refresh(passenger); }
    }
    private static void refresh(UUID id) {
        var level = Minecraft.getInstance().level;
        Player p = level == null ? null : level.getPlayerByUUID(id);
        if (p != null) p.refreshDimensions();
    }
    public static void invite(CoupleNetwork.Invitation request) {
        invitation = request;
        invitationEnd = ticks + Math.min(1200, Math.max(20, request.ticks()));
    }
    public static void clearInvite(CoupleNetwork.ClearInvitation p) {
        if (invitation != null && invitation.request().equals(p.request())) invitation = null;
    }
    public static void decide(boolean accept) {
        if (invitation == null) return;
        PacketDistributor.sendToServer(new CoupleNetwork.Decision(invitation.request(), accept));
        invitation = null;
    }
    public static void sendPreferences() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new CoupleNetwork.Preferences(CoupleClientConfig.preferences().json()));
        }
    }
    public static void clear() {
        carrierByPassenger.clear(); passengerByCarrier.clear();poses.clear();socials.clear(); invitation = null; ticks = 0;
    }
    public static void kiss(CoupleNetwork.KissEffect effect) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player first = mc.level.getPlayerByUUID(effect.first()), second = mc.level.getPlayerByUUID(effect.second());
        if (first == null || second == null) return;
        Vec3 position = head(first).add(head(second)).scale(0.5);
        if (CoupleClientConfig.PARTICLES.get()) {
            for (int i = 0; i < 4; i++) {
                mc.level.addParticle(ParticleTypes.HEART, position.x + (mc.level.random.nextDouble() - 0.5) * 0.24,
                        position.y + 0.04 + i * 0.055, position.z + (mc.level.random.nextDouble() - 0.5) * 0.24, 0, 0.02, 0);
            }
        }
        if (CoupleClientConfig.SOUND.get()) {
            mc.level.playLocalSound(position.x, position.y, position.z, CoupleInteractions.KISS.get(), SoundSource.PLAYERS,
                    0.65F, 0.96F + mc.level.random.nextFloat() * 0.08F, false);
        }
    }
    private static Vec3 head(Player player) {
        Player carrier = CarryHooks.carrier(player);
        return carrier == null ? player.getEyePosition() : CarryGeometry.head(player, carrier, 1.0F);
    }
    private ClientState() {}
}
