package team.civitatum.couple.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import team.civitatum.couple.*;
import team.civitatum.couple.core.Interaction;
import team.civitatum.couple.network.CoupleNetwork;

/** One server-owned session per player. Sessions never survive a disconnect or teleport. */
public final class SocialSessions {
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static long clock;
    private static boolean positioning;
    private static final class Session {
        final ServerPlayer a,b;final Interaction action;final long start;final int duration;final float yaw;final boolean mounted;
        final Vec3 first,second;
        boolean effect;
        Session(ServerPlayer a,ServerPlayer b,Interaction action,float yaw,boolean mounted,Vec3 first,Vec3 second){this.a=a;this.b=b;this.action=action;this.yaw=yaw;this.mounted=mounted;this.first=first;this.second=second;start=clock;duration=action==Interaction.KISS?70:action==Interaction.HUG?120:0;}
        CoupleNetwork.SocialState packet(boolean active){return new CoupleNetwork.SocialState(a.getUUID(),b.getUUID(),action.ordinal(),active,(int)(clock-start),duration,yaw,mounted);}
    }
    public static boolean positioning(){return positioning;}
    public static boolean active(UUID id){return ACTIVE.containsKey(id);}
    public static CoupleNetwork.SocialState state(UUID id){var s=ACTIVE.get(id);return s==null?null:s.packet(true);}
    public static boolean begin(ServerPlayer a,ServerPlayer b,Interaction action){
        if(action!=Interaction.KISS && action!=Interaction.HUG && action!=Interaction.REST)return false;
        if(CoupleServer.validate(a,b,action)!=null)return false;
        if(active(a.getUUID()) || active(b.getUUID()))return false;
        boolean mounted=CarryHooks.pair(a,b) || CarryHooks.pair(b,a);
        if(mounted && action!=Interaction.KISS)return false;
        float yaw=mounted?a.yBodyRot:(float)Math.toDegrees(Math.atan2(b.getZ()-a.getZ(),b.getX()-a.getX()))-90;
        double scale=(a.getScale()+b.getScale())*.5;
        Vec3 first=a.position(),second=action==Interaction.REST?first.add(CarryGeometry.local(yaw,-.64*scale,0,0)):first.add(CarryGeometry.local(yaw,0,0,.66*scale));
        if(!mounted && (!safe(b,second) || !a.onGround() || !b.onGround()))return false;
        var s=new Session(a,b,action,yaw,mounted,first,second);
        ACTIVE.put(a.getUUID(),s);ACTIVE.put(b.getUUID(),s);
        a.stopUsingItem();b.stopUsingItem();a.setSprinting(false);b.setSprinting(false);
        if(!mounted){place(a,first,yaw);place(b,second,action==Interaction.REST?yaw:yaw+180);}
        freeze(a,true);freeze(b,true);a.refreshDimensions();b.refreshDimensions();broadcast(s.packet(true));
        return true;
    }
    private static boolean safe(ServerPlayer p,Vec3 at){var box=p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at);return p.serverLevel().getWorldBorder().isWithinBounds(box) && p.serverLevel().noCollision(p,box) && p.serverLevel().getFluidState(net.minecraft.core.BlockPos.containing(at)).isEmpty();}
    private static void place(ServerPlayer p,Vec3 pos,float yaw){
        positioning=true;try{p.connection.teleport(pos.x,pos.y,pos.z,yaw,0);p.yBodyRot=p.yBodyRotO=yaw;p.yHeadRot=p.yHeadRotO=yaw;}finally{positioning=false;}
    }
    private static void freeze(ServerPlayer p,boolean enable){var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(CoupleInteractions.id("social_pose"));if(enable)speed.addTransientModifier(new AttributeModifier(CoupleInteractions.id("social_pose"),-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}
    public static void end(UUID member){
        var s=ACTIVE.remove(member);if(s==null)return;
        ACTIVE.remove(s.a.getUUID(),s);ACTIVE.remove(s.b.getUUID(),s);
        freeze(s.a,false);freeze(s.b,false);s.a.refreshDimensions();s.b.refreshDimensions();broadcast(s.packet(false));
    }
    public static void tick(){
        clock++;
        for(var s:new HashSet<>(ACTIVE.values())){
            if(s.a.isRemoved() || s.b.isRemoved() || !s.a.isAlive() || !s.b.isAlive() || s.a.level()!=s.b.level()
                || s.a.isSpectator() || s.b.isSpectator() || s.a.isSleeping() || s.b.isSleeping()
                || s.a.isInWaterOrBubble() || s.b.isInWaterOrBubble() || s.a.isInLava() || s.b.isInLava() || s.a.isFallFlying() || s.b.isFallFlying()
                || s.a.isShiftKeyDown() || s.b.isShiftKeyDown() || (s.duration>0 && clock-s.start>=s.duration)
                || (!s.mounted && (s.a.isPassenger() || s.b.isPassenger() || s.a.position().distanceToSqr(s.first)>.36 || s.b.position().distanceToSqr(s.second)>.36))
                || (s.mounted && !CarryHooks.pair(s.a,s.b) && !CarryHooks.pair(s.b,s.a))){end(s.a.getUUID());continue;}
            s.a.stopUsingItem();s.b.stopUsingItem();s.a.setSprinting(false);s.b.setSprinting(false);
            if(!s.mounted){s.a.setDeltaMovement(Vec3.ZERO);s.b.setDeltaMovement(Vec3.ZERO);}
            if(s.action==Interaction.KISS && !s.effect && clock-s.start>=30){s.effect=true;broadcast(new CoupleNetwork.KissEffect(s.a.getUUID(),s.b.getUUID()));}
        }
    }
    public static void tracking(ServerPlayer watcher,UUID member){var state=state(member);if(state!=null)CoupleServer.send(watcher,state);}
    private static void broadcast(CustomPacketPayload packet){for(var s:new HashSet<>(ACTIVE.values())){for(var p:s.a.server.getPlayerList().getPlayers())CoupleServer.send(p,packet);return;}}
    // Ending the last session still needs to reach every observer.
    private static void broadcast(CoupleNetwork.SocialState packet){var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null)for(var p:server.getPlayerList().getPlayers())CoupleServer.send(p,packet);}
    public static void clear(){for(var s:new HashSet<>(ACTIVE.values()))end(s.a.getUUID());ACTIVE.clear();clock=0;}
    private SocialSessions(){}
}
