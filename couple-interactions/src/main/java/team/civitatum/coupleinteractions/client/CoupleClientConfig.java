package team.civitatum.coupleinteractions.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.*;
import team.civitatum.coupleinteractions.core.*;

public final class CoupleClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CARRY_REQUESTS, KISS_REQUESTS, SOUND, PARTICLES;
    public static final Map<Interaction,ModConfigSpec.EnumValue<Consent>> POLICIES=new EnumMap<>(Interaction.class);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRUSTED;
    public static final ModConfigSpec.DoubleValue HEAD,ARM,BANK,SIDE,UP,FORWARD;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CARRY_REQUESTS = b.define("allowCarryRequests", true);
        KISS_REQUESTS = b.define("allowKissRequests", true);
        SOUND = b.define("kissSound", true);
        PARTICLES = b.define("kissParticles", true);
        for(var action:Interaction.values())if(action!=Interaction.RELEASE)POLICIES.put(action,b.defineEnum("consent."+action.name().toLowerCase(Locale.ROOT),Consent.ASK));
        TRUSTED=b.defineListAllowEmpty("trustedPlayers",List.of(),value->value instanceof String s && s.length()<=100 && uuid(s)!=null);
        HEAD=b.defineInRange("pose.headForwardDegrees",24.0,-20.0,65.0);ARM=b.defineInRange("pose.leftArmSpreadDegrees",55.0,20.0,110.0);BANK=b.defineInRange("pose.bankDegrees",-65.0,-80.0,-45.0);
        SIDE=b.defineInRange("pose.sideOffset",0,-.25,.25);UP=b.defineInRange("pose.heightOffset",0,-.3,.3);FORWARD=b.defineInRange("pose.forwardOffset",0,-.25,.35);
        SPEC = b.build();
    }
    public static UUID uuid(String entry){try{int cut=entry.indexOf('|');return UUID.fromString(cut<0?entry:entry.substring(0,cut));}catch(RuntimeException e){return null;}}
    public static PoseSettings pose(){return new PoseSettings(HEAD.get().floatValue(),ARM.get().floatValue(),BANK.get().floatValue(),SIDE.get().floatValue(),UP.get().floatValue(),FORWARD.get().floatValue()).safe();}
    public static void pose(PoseSettings p){p=p.safe();HEAD.set((double)p.head());ARM.set((double)p.arm());BANK.set((double)p.bank());SIDE.set((double)p.side());UP.set((double)p.up());FORWARD.set((double)p.forward());}
    public static PlayerPreferences preferences(){
        var p=new PlayerPreferences();POLICIES.forEach((a,v)->p.policies.put(a,v.get()));
        if(!CARRY_REQUESTS.get())p.policies.put(Interaction.CARRY,Consent.BLOCK);if(!KISS_REQUESTS.get())p.policies.put(Interaction.KISS,Consent.BLOCK);
        for(String entry:TRUSTED.get()){var id=uuid(entry);if(id!=null && p.trusted.size()<64)p.trusted.add(id);}p.pose=pose();return p;
    }
    public static boolean trusted(UUID id){return TRUSTED.get().stream().anyMatch(s->id.equals(uuid(s)));}
    public static void toggleTrusted(UUID id,String name){var list=new ArrayList<String>(TRUSTED.get());if(trusted(id))list.removeIf(s->id.equals(uuid(s)));else if(list.size()<64)list.add(id+"|"+name.substring(0,Math.min(32,name.length())));TRUSTED.set(list);SPEC.save();ClientState.sendPreferences();}
    private CoupleClientConfig() {}
}
