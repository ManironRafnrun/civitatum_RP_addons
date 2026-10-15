package team.civitatum.couple.test;

import java.util.ArrayList;
import java.util.List;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.connection.ConnectionType;
import team.civitatum.couple.CarryGeometry;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.CoupleInteractions;
import team.civitatum.couple.core.Interaction;
import team.civitatum.couple.network.CoupleNetwork;
import team.civitatum.couple.server.CoupleServer;

@GameTestHolder("couple_test")
@PrefixGameTestTemplate(false)
public final class ServerChecks {
    public static final class Recorder extends ServerGamePacketListenerImpl {
        public final List<CustomPacketPayload> payloads = new ArrayList<>();
        public Recorder(ServerPlayer p) {
            super(p.server, connectionOf(p), p, new CommonListenerCookie(p.getGameProfile(),0,
                    net.minecraft.server.level.ClientInformation.createDefault(),false,connectionType(p)));
        }
        private static Connection connectionOf(ServerPlayer p) {
            if(p.connection!=null&&p.connection.getConnection().channel()!=null)return p.connection.getConnection();
            return embedded();
        }
        private static ConnectionType connectionType(ServerPlayer p) {
            if(p.connection!=null&&p.connection.getConnection().channel()!=null){var type=ChannelAttributes.getConnectionType(p.connection.getConnection());if(type!=null)return type;}
            return ConnectionType.OTHER;
        }
        private static Connection embedded() {
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            return connection;
        }
        @Override public void send(Packet<?> p) { if(p instanceof ClientboundCustomPayloadPacket payload) payloads.add(payload.payload()); }
        @Override public void send(Packet<?> p, PacketSendListener listener) { send(p); }
        @Override public void tick() { player.doTick(); }
        public CoupleNetwork.Invitation lastInvite() {
            return payloads.stream().filter(p->p instanceof CoupleNetwork.Invitation).map(p->(CoupleNetwork.Invitation)p).reduce((a,b)->b).orElseThrow();
        }
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void carryConsentReleaseAndCleanup(GameTestHelper h) {
        var level=h.getLevel();
        for(int x=0;x<9;x++) for(int z=0;z<9;z++) h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        ServerPlayer a=h.makeMockServerPlayerInLevel(), b=h.makeMockServerPlayerInLevel(), c=h.makeMockServerPlayerInLevel();
        a.connection=new Recorder(a); b.connection=new Recorder(b); c.connection=new Recorder(c);
        var oldMode=team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.get();
        try {
            Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4)));
            put(a,origin);put(b,origin.add(1,0,0));put(c,origin.add(0,0,1));
            double speed=a.getAttributeValue(Attributes.MOVEMENT_SPEED);
            CoupleServer.request(a,new CoupleNetwork.Action(b.getUUID(),Interaction.CARRY.ordinal()));
            h.assertTrue(!b.isPassenger(),"request alone must not carry anyone");
            var invite=((Recorder)b.connection).lastInvite();
            CoupleServer.decide(c,new CoupleNetwork.Decision(invite.request(),true));
            h.assertTrue(!b.isPassenger(),"unrelated player cannot accept");
            CoupleServer.decide(b,new CoupleNetwork.Decision(invite.request(),true));
            h.assertTrue(b.getVehicle()==a && CarryHooks.pair(a,b),"recipient acceptance establishes pair");
            h.assertTrue(b.getBbHeight()<1 && b.getEyeHeight()<1,"carried dimensions and eye height applied");
            h.assertTrue(a.getAttributeValue(Attributes.MOVEMENT_SPEED)<speed,"carrier speed modifier applied");
            a.yBodyRot=90;a.positionRider(b);
            h.assertTrue(b.position().distanceTo(CarryGeometry.anchorNow(a))<0.001,"passenger follows rotated carrier anchor");
            CoupleServer.tick(new ServerTickEvent.Post(() -> true, a.server));
            h.assertTrue(b.getVehicle()==a,"pair survives a regular server tick");
            CoupleServer.request(c,new CoupleNetwork.Action(a.getUUID(),Interaction.RELEASE.ordinal()));
            h.assertTrue(b.getVehicle()==a,"third player cannot release another pair");
            CoupleServer.request(b,new CoupleNetwork.Action(a.getUUID(),Interaction.RELEASE.ordinal()));
            h.assertTrue(!b.isPassenger()&&!CarryHooks.occupied(a)&&!CarryHooks.occupied(b),"passenger releases without approval");
            h.assertTrue(b.getBbHeight()>1.7,"normal dimensions restored");
            h.assertTrue(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<0.000001,"speed modifier restored exactly");
            h.assertTrue(level.noCollision(b,b.getBoundingBox()),"dismount position is collision-free");
            put(a,origin);put(b,origin.add(1,0,0));
            h.assertTrue(CoupleServer.beginCarry(a,b),"second carry starts");
            b.stopRiding();CoupleServer.tick(new ServerTickEvent.Post(() -> true, a.server));
            h.assertTrue(!CarryHooks.occupied(a)&&b.getBbHeight()>1.7,"vanilla sneak dismount cleans state");
            put(a,origin);put(b,origin.add(1,0,0));
            h.assertTrue(CoupleServer.beginCarry(a,b),"third carry starts");
            a.teleportTo(origin.x+2,origin.y,origin.z);
            h.assertTrue(!b.isPassenger()&&!CarryHooks.occupied(a),"teleport stops carrying before moving");
            put(a,origin);put(b,origin.add(1,0,0));
            h.assertTrue(CoupleServer.beginCarry(a,b),"fourth carry starts");
            h.assertTrue(!CoupleServer.beginCarry(c,a),"nested carrying rejected");
            CoupleServer.end(a,null);
            put(a,origin);put(b,origin.add(6,0,0));
            h.assertTrue("too_far".equals(CoupleServer.validate(a,b,Interaction.KISS)),"far-away kisses rejected");
            put(b,origin.add(2,0,0));
            h.setBlock(new BlockPos(5,3,4),Blocks.STONE);
            h.assertTrue("too_far".equals(CoupleServer.validate(a,b,Interaction.KISS)),"wall blocks interaction");
            h.setBlock(new BlockPos(5,3,4),Blocks.AIR);
            put(a,origin);put(b,origin.add(1,0,0));
            h.assertTrue(CoupleServer.beginCarry(a,b),"carry starts for lateral collision test");a.positionRider(b);
            team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.set(team.civitatum.couple.CoupleConfig.ObstacleMode.STRICT);
            h.setBlock(new BlockPos(5,3,4),Blocks.STONE);
            CoupleServer.tick(new ServerTickEvent.Post(() -> true, a.server));
            h.assertTrue(!CarryHooks.occupied(a)&&!b.isPassenger(),"sideways body cannot remain inside a wall");
            h.setBlock(new BlockPos(5,3,4),Blocks.AIR);
            System.out.println("COUPLE_SERVER_PASS consent,forgery,release,anchor,dimensions,speed,sneak,teleport,nesting,distance,wall,lateral_collision");
            h.succeed();
        } finally {
            team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.set(oldMode);
            CoupleServer.end(a,null);CoupleServer.end(b,null);CoupleServer.end(c,null);
            for(ServerPlayer p:List.of(a,b,c)) { level.getServer().getPlayerList().remove(p); p.discard(); }
        }
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void carryObstacleModes(GameTestHelper h){
        var level=h.getLevel();for(int x=0;x<9;x++)for(int z=0;z<9;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        ServerPlayer a=h.makeMockServerPlayerInLevel(),b=h.makeMockServerPlayerInLevel();a.connection=new Recorder(a);b.connection=new Recorder(b);
        var old=team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.get();
        var origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4)));
        try{
            team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.set(team.civitatum.couple.CoupleConfig.ObstacleMode.RELAXED);
            put(a,origin);put(b,origin.add(1,0,0));h.assertTrue(CoupleServer.beginCarry(a,b),"relaxed carry starts");a.positionRider(b);
            h.setBlock(new BlockPos(4,2,5),Blocks.STONE);
            h.assertTrue(!level.noCollision(b,b.getBoundingBox()),"test step really intersects passenger hitbox");
            for(int i=0;i<30;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            h.assertTrue(b.getVehicle()==a,"approaching one-block step does not drop passenger");
            a.setPos(origin.add(0,.55,.4));CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            a.setPos(origin.add(0,1,1));CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            h.assertTrue(b.getVehicle()==a,"jump onto step preserves carry");
            CoupleServer.end(a,null);put(a,origin);put(b,origin.add(1,0,0));
            h.setBlock(new BlockPos(4,2,5),Blocks.AIR);
            h.assertTrue(CoupleServer.beginCarry(a,b),"wall test starts");h.setBlock(new BlockPos(4,3,5),Blocks.STONE);
            CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));h.assertTrue(b.getVehicle()==a,"brief wall contact is tolerated");
            h.setBlock(new BlockPos(4,3,5),Blocks.AIR);CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            h.setBlock(new BlockPos(4,3,5),Blocks.STONE);
            for(int i=0;i<team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_GRACE.get()-1;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            h.assertTrue(b.getVehicle()==a,"clear tick resets obstruction timer");
            CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));h.assertTrue(!b.isPassenger(),"sustained upper-body obstruction releases in relaxed mode");
            h.setBlock(new BlockPos(4,3,5),Blocks.AIR);put(a,origin);put(b,origin.add(1,0,0));
            team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.set(team.civitatum.couple.CoupleConfig.ObstacleMode.OFF);
            h.assertTrue(CoupleServer.beginCarry(a,b),"off mode starts");h.setBlock(new BlockPos(4,3,5),Blocks.STONE);
            for(int i=0;i<30;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));h.assertTrue(b.getVehicle()==a,"OFF disables obstacle release");
            b.stopRiding();CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));h.assertTrue(!CarryHooks.occupied(a),"OFF still permits normal release");
            System.out.println("COUPLE_OBSTACLES_PASS step,jump,grace,reset,wall,off,release");h.succeed();
        }finally{team.civitatum.couple.CoupleConfig.CARRY_OBSTACLE_MODE.set(old);CoupleServer.end(a,null);CoupleServer.end(b,null);for(var p:List.of(a,b)){level.getServer().getPlayerList().remove(p);p.discard();}}
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void socialPoliciesAndCleanup(GameTestHelper h){
        var level=h.getLevel();for(int x=0;x<9;x++)for(int z=0;z<9;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        ServerPlayer a=h.makeMockServerPlayerInLevel(),b=h.makeMockServerPlayerInLevel(),c=h.makeMockServerPlayerInLevel();
        a.connection=new Recorder(a);b.connection=new Recorder(b);c.connection=new Recorder(c);
        Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4)));
        try {
            put(a,origin);put(b,origin.add(0,0,1.5));put(c,origin.add(2,0,0));
            var p=new team.civitatum.couple.core.PlayerPreferences();p.policies.put(Interaction.HUG,team.civitatum.couple.core.Consent.TRUSTED);p.trusted.add(c.getUUID());
            CoupleServer.preferences(b,new CoupleNetwork.Preferences(p.json()));
            CoupleServer.request(a,new CoupleNetwork.Action(b.getUUID(),Interaction.HUG.ordinal()));
            h.assertTrue(!team.civitatum.couple.SocialHooks.active(a),"non-favorite must confirm");
            var invite=((Recorder)b.connection).lastInvite();CoupleServer.decide(c,new CoupleNetwork.Decision(invite.request(),true));
            h.assertTrue(!team.civitatum.couple.SocialHooks.active(a),"third player cannot accept hug");
            double speed=a.getAttributeValue(Attributes.MOVEMENT_SPEED);
            CoupleServer.decide(b,new CoupleNetwork.Decision(invite.request(),true));
            h.assertTrue(team.civitatum.couple.SocialHooks.active(a) && team.civitatum.couple.SocialHooks.active(b),"accepted hug reserves both players");
            h.assertTrue(a.getAttributeValue(Attributes.MOVEMENT_SPEED)==0,"standing pair stays still");
            CoupleServer.request(c,new CoupleNetwork.Action(a.getUUID(),Interaction.RELEASE.ordinal()));
            h.assertTrue(team.civitatum.couple.SocialHooks.active(a),"third player cannot release hug");
            b.setShiftKeyDown(true);team.civitatum.couple.server.SocialSessions.tick();b.setShiftKeyDown(false);
            h.assertTrue(!team.civitatum.couple.SocialHooks.active(a) && a.getAttributeValue(Attributes.MOVEMENT_SPEED)==speed,"either partner can leave and restore speed");
            put(a,origin);put(b,origin.add(0,0,1.5));
            p.trusted.add(a.getUUID());p.policies.put(Interaction.REST,team.civitatum.couple.core.Consent.ALLOW);p.policies.put(Interaction.KISS,team.civitatum.couple.core.Consent.BLOCK);CoupleServer.preferences(b,new CoupleNetwork.Preferences(p.json()));
            for(int i=0;i<61;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            int before=((Recorder)b.connection).payloads.size();CoupleServer.request(a,new CoupleNetwork.Action(b.getUUID(),Interaction.HUG.ordinal()));
            h.assertTrue(team.civitatum.couple.SocialHooks.active(a),"favorite accepted without invitation");
            h.assertTrue(((Recorder)b.connection).payloads.subList(before,((Recorder)b.connection).payloads.size()).stream().noneMatch(v->v instanceof CoupleNetwork.Invitation),"auto consent sends no question");
            CoupleServer.beforeTeleport(a);h.assertTrue(!team.civitatum.couple.SocialHooks.active(b),"teleport releases social session");
            put(a,origin);put(b,origin.add(0,0,1.5));for(int i=0;i<61;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            CoupleServer.request(a,new CoupleNetwork.Action(b.getUUID(),Interaction.REST.ordinal()));
            h.assertTrue(team.civitatum.couple.SocialHooks.resting(a) && a.getBbHeight()<1.2,"rest has seated dimensions");
            CoupleServer.end(b,null);h.assertTrue(a.getBbHeight()>1.7,"rest restores full dimensions");
            put(a,origin);put(b,origin.add(0,0,1.5));for(int i=0;i<61;i++)CoupleServer.tick(new ServerTickEvent.Post(()->true,a.server));
            CoupleServer.request(a,new CoupleNetwork.Action(b.getUUID(),Interaction.KISS.ordinal()));h.assertTrue(!team.civitatum.couple.SocialHooks.active(a),"kiss blocking is independent of hug/rest");
            h.assertTrue(team.civitatum.couple.server.SocialSessions.begin(a,b,Interaction.KISS),"kiss starts through checked internal API");
            for(int i=0;i<71;i++)team.civitatum.couple.server.SocialSessions.tick();
            h.assertTrue(!team.civitatum.couple.SocialHooks.active(a),"kiss ends automatically");
            System.out.println("COUPLE_SOCIAL_SERVER_PASS policies,trusted,forgery,release,shift,teleport,dimensions,kiss_timeout");h.succeed();
        }finally{CoupleServer.end(a,null);CoupleServer.end(b,null);CoupleServer.end(c,null);for(var player:List.of(a,b,c)){level.getServer().getPlayerList().remove(player);player.discard();}}
    }
    private static void put(ServerPlayer p,Vec3 pos) {
        p.setPos(pos);p.setOnGround(true);p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;
        p.setDeltaMovement(Vec3.ZERO);p.setHealth(p.getMaxHealth());p.yBodyRot=0;
    }
}
