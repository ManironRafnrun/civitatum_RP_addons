package team.civitatum.coupleinteractions.core;

import com.google.gson.*;
import java.util.*;

/** Only the recipient can upload this policy. UUIDs, never display names, grant trust. */
public final class PlayerPreferences {
    public final EnumMap<Interaction,Consent> policies=new EnumMap<>(Interaction.class);
    public final Set<UUID> trusted=new LinkedHashSet<>();
    public PoseSettings pose=PoseSettings.DEFAULT;
    public PlayerPreferences(){for(var action:Interaction.values())if(action!=Interaction.RELEASE)policies.put(action,Consent.ASK);}
    public Consent policy(Interaction action){return policies.getOrDefault(action,Consent.ASK);}
    public String json(){
        var root=new JsonObject();var actions=new JsonObject();policies.forEach((k,v)->actions.addProperty(k.name(),v.name()));root.add("actions",actions);
        var friends=new JsonArray();trusted.stream().limit(64).forEach(id->friends.add(id.toString()));root.add("trusted",friends);
        root.add("pose",JsonParser.parseString(pose.json()));return root.toString();
    }
    public static PlayerPreferences read(String text){
        var p=new PlayerPreferences();
        try {
            var root=JsonParser.parseString(text).getAsJsonObject();
            if(root.has("actions"))for(var action:p.policies.keySet())try{p.policies.put(action,Consent.valueOf(root.getAsJsonObject("actions").get(action.name()).getAsString()));}catch(RuntimeException ignored){}
            if(root.has("trusted"))for(var value:root.getAsJsonArray("trusted")){if(p.trusted.size()>=64)break;try{p.trusted.add(UUID.fromString(value.getAsString()));}catch(RuntimeException ignored){}}
            if(root.has("pose"))p.pose=PoseSettings.read(root.get("pose").toString());
        } catch(RuntimeException ignored){}
        return p;
    }
    public static PlayerPreferences legacy(boolean carry,boolean kiss){var p=new PlayerPreferences();p.policies.put(Interaction.CARRY,carry?Consent.ASK:Consent.BLOCK);p.policies.put(Interaction.KISS,kiss?Consent.ASK:Consent.BLOCK);return p;}
}
