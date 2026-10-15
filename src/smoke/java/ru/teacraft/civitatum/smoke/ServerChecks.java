package ru.teacraft.civitatum.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.*;
import ru.teacraft.civitatum.*;

@GameTestHolder("civitatum_smoke")
@PrefixGameTestTemplate(false)
public final class ServerChecks {
    private static net.minecraft.server.level.ServerPlayer survival(GameTestHelper h) {
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"race-survival-test"),false);
        var p=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void persistenceNetworkAndAttributes(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(var r:RaceCatalog.all()) if(!r.family()) {
            var c=RaceCatalog.defaults(r.id,"female");
            p.setData(Civitatum.STATE,new RaceState(c,3,98765));
            RaceService.apply(p);
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE)-c.height/1.8)<.001,"physical scale: "+r.id);
            h.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH)>=4,"health floor: "+r.id);
        }
        var c=RaceCatalog.defaults("jahar","male");
        var expected=new RaceState(c,4,123456);
        p.setData(Civitatum.STATE,expected);
        var saved=p.saveWithoutId(new CompoundTag());
        p.setData(Civitatum.STATE,RaceState.EMPTY);p.load(saved);
        h.assertValueEqual(p.getData(Civitatum.STATE).json(),expected.json(),"persisted character, curse and cooldown");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var payload=new RaceNetwork.Clientbound(0,p.getUUID(),314,expected.json());
            RaceNetwork.Clientbound.CODEC.encode(buf,payload);
            h.assertValueEqual(RaceNetwork.Clientbound.CODEC.decode(buf),payload,"bounded payload roundtrip");
            h.assertValueEqual(buf.readableBytes(),0,"network framing");
        } finally {buf.release();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cooldownFoodAndBoots(GameTestHelper h) {
        var p=survival(h);
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("human","male"),0,0));
        p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(20);
        RaceAbilities.activate(p);
        h.assertValueEqual(p.getFoodData().getFoodLevel(),18,"ability food price");
        long cooldown=p.getData(Civitatum.STATE).cooldownUntil();
        h.assertTrue(cooldown>RaceService.time(p),"cooldown persisted");
        RaceAbilities.activate(p);
        h.assertValueEqual(p.getFoodData().getFoodLevel(),18,"cooldown prevents repeated payment");
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("hobbit","male"),0,0));
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        RaceService.enforceBoots(p);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"boot restriction catches external equipment");
        h.assertTrue(p.getInventory().contains(new ItemStack(Items.IRON_BOOTS)),"removed boots are returned");
        h.assertValueEqual(MetalEating.food(new ItemStack(Items.NETHERITE_INGOT)).food(),8,"forged netherite food");
        h.assertTrue(MetalEating.food(new ItemStack(Items.APPLE))==null,"ordinary food isn't metal");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullBodyMovementStopsAtWalls(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(int x=0;x<8;x++)for(int y=0;y<5;y++)for(int z=0;z<4;z++)h.setBlock(x,y,z,Blocks.AIR);
        var at=h.absoluteVec(new Vec3(1,2,1));p.teleportTo(at.x,at.y,at.z);
        for(int y=1;y<5;y++)for(int z=0;z<4;z++)h.setBlock(3,y,z,Blocks.STONE);
        Vec3 end=RaceAbilities.safeDestination(p,new Vec3(6,0,0));
        h.assertTrue(end.x<at.x+2,"movement cannot cross the collision wall");
        h.assertTrue(h.getLevel().noCollision(p,p.getBoundingBox().move(end.subtract(p.position())).deflate(.001)),"final full hitbox is free");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void damageTraitsAndJaharCurse(GameTestHelper h) {
        var p=survival(h);
        h.runAfterDelay(65,() -> {
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("fire_dragonborn","male"),0,0));
        RaceService.apply(p);p.setHealth(20);p.invulnerableTime=0;
        p.hurt(p.damageSources().onFire(),4);
        h.assertTrue(p.getHealth()>16 && p.getHealth()<20,"fire resistance reduces actual incoming damage");
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("jahar","male"),0,0));
        RaceService.apply(p);p.setHealth(1);p.invulnerableTime=0;
        p.hurt(p.damageSources().onFire(),8);
        h.assertValueEqual(p.getData(Civitatum.STATE).curseDeaths(),1,"fatal fire adds permanent curse");
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("human","male"),1,0));
        RaceService.apply(p);h.assertTrue(Math.abs(p.getMaxHealth()-20)<.001,"curse affects only Jahar");
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("jahar","male"),1,0));
        RaceService.apply(p);h.assertTrue(Math.abs(p.getMaxHealth()-18)<.001,"returning to Jahar keeps curse");
        h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void tallCharacterCanCrouchUnderTwoBlocks(GameTestHelper h) {
        var p=survival(h);
        var c=RaceCatalog.defaults("orthosian","male");c.height=2.5f;
        p.setData(Civitatum.STATE,new RaceState(c,0,0));RaceService.apply(p);
        p.setPose(net.minecraft.world.entity.Pose.STANDING);p.refreshDimensions();
        h.assertTrue(Math.abs(p.getBbHeight()-2.5)<.01,"standing physical height is 2.5 blocks");
        p.setPose(net.minecraft.world.entity.Pose.CROUCHING);p.refreshDimensions();
        h.assertTrue(p.getBbHeight()<=1.81,"crouching fits below two blocks");
        h.assertTrue(p.getEyeHeight()<=1.63,"crouching eye position fits under ceiling");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void resetRemovesOnlyOurModifiers(GameTestHelper h) {
        var p=survival(h);
        var armor=p.getAttribute(Attributes.ARMOR);
        var other=new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("othermod","test"),3,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        armor.addTransientModifier(other);
        p.setData(Civitatum.STATE,new RaceState(RaceCatalog.defaults("forged","female"),2,999999));RaceService.apply(p);
        h.assertTrue(Math.abs(p.getArmorValue()-5)<.01,"race and external armor coexist");
        RaceService.reset(p);
        h.assertTrue(!p.getData(Civitatum.STATE).character().selected,"reset clears character selection");
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE)-1)<.001,"reset restores default scale");
        h.assertValueEqual(p.getArmorValue(),3,"reset retains external modifier");
        h.assertValueEqual(p.getData(Civitatum.STATE).curseDeaths(),2,"reset keeps curse history");
        h.assertValueEqual(p.getData(Civitatum.STATE).cooldownUntil(),999999L,"reset keeps cooldown");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void birthsignReplacementAndDecorativeToggle(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var c=RaceCatalog.defaults("human","female");c.birthsign="lady";
        p.setData(Civitatum.STATE,new RaceState(c,0,0));RaceService.apply(p);RaceService.apply(p);
        h.assertTrue(p.getMaxHealth()==24,"Lady health must apply exactly once");
        var armor=new ItemStack(Items.IRON_CHESTPLATE);p.setItemSlot(EquipmentSlot.CHEST,armor);float before=(float)p.getAttributeValue(Attributes.ARMOR);
        RaceService.naked(p,true);h.assertTrue(p.getData(Civitatum.STATE).character().naked,"naked state");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST)==armor && p.getAttributeValue(Attributes.ARMOR)==before,"decorative toggle must preserve equipment");
        c=p.getData(Civitatum.STATE).character().copy();c.birthsign="thief";p.setData(Civitatum.STATE,new RaceState(c,0,0));RaceService.apply(p);
        h.assertTrue(p.getMaxHealth()==20 && p.getAttributeValue(Attributes.LUCK)==1,"old sign removed and new sign applied");
        h.succeed();
    }
    private static long editToken(net.minecraft.server.level.ServerPlayer p) {
        try {var field=RaceService.class.getDeclaredField("EDITS");field.setAccessible(true);var edits=(java.util.Map<?,?>)field.get(null);var session=edits.get(p.getUUID());var method=session.getClass().getDeclaredMethod("token");method.setAccessible(true);return (long)method.invoke(session);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void mirrorRejectsBodyChangesAndConsumesOnlyOnValidSave(GameTestHelper h) {
        var p=survival(h);h.assertTrue(!p.isCreative(),"survival player required for item consumption");var c=RaceCatalog.defaults("human","female");p.setData(Civitatum.STATE,new RaceState(c,0,0));
        p.getInventory().add(new ItemStack(Civitatum.MIRROR.get()));RaceService.openCosmetic(p,Civitatum.MIRROR.get());long token=editToken(p);
        var forged=c.copy();forged.height=1.65f;RaceService.save(p,token,forged.json());
        h.assertTrue(p.getData(Civitatum.STATE).character().height==c.height,"body change denied");h.assertTrue(p.getInventory().contains(new ItemStack(Civitatum.MIRROR.get())),"rejected edit must not consume item");
        var valid=c.copy();valid.hairAsset="ponytail_streak";valid.hairDecor1=3;RaceService.save(p,token,valid.json());
        h.assertTrue(p.getData(Civitatum.STATE).character().hairDecor1==3,"cosmetic saved");h.assertTrue(!p.getInventory().contains(new ItemStack(Civitatum.MIRROR.get())),"successful edit consumes one mirror");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void fullLooksCanChangeSexButNotRace(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var c=RaceCatalog.defaults("human","female");p.setData(Civitatum.STATE,new RaceState(c,0,0));RaceService.open(p,false,false);long token=editToken(p);
        var invalid=RaceCatalog.defaults("orc","male");RaceService.save(p,token,invalid.json());h.assertTrue(p.getData(Civitatum.STATE).character().race.equals("human"),"race remains locked");
        var valid=RaceCatalog.defaults("human","male");valid.birthsign="steed";RaceService.save(p,token,valid.json());
        h.assertTrue(p.getData(Civitatum.STATE).character().sex.equals("male") && p.getData(Civitatum.STATE).character().birthsign.equals("steed"),"full appearance save");h.succeed();
    }
}
