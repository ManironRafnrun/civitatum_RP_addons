package ru.teacraft.civitatum;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sparse Emotecraft tracks from the supplied idle.json, with its return tick and quadratic easing. */
public final class IdleAnimation {
    private record Key(float tick,float value) {}
    private static final Map<String,List<Key>> TRACKS=new LinkedHashMap<>();
    public static final float BEGIN,RETURN,END;
    static {
        try(var in=IdleAnimation.class.getResourceAsStream("/assets/civitatum/animations/idle.json")) {
            var emote=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("emote");
            BEGIN=emote.get("beginTick").getAsFloat();RETURN=emote.get("returnTick").getAsFloat();END=emote.get("endTick").getAsFloat();
            boolean degrees=emote.get("degrees").getAsBoolean();
            for(var move:emote.getAsJsonArray("moves")) {
                var m=move.getAsJsonObject();float tick=m.get("tick").getAsFloat();
                for(var part:m.entrySet())if(part.getValue().isJsonObject())for(var axis:part.getValue().getAsJsonObject().entrySet()) {
                    float value=axis.getValue().getAsFloat();
                    if(degrees && List.of("pitch","yaw","roll").contains(axis.getKey()))value=(float)Math.toRadians(value);
                    TRACKS.computeIfAbsent(part.getKey()+"/"+axis.getKey(),k->new ArrayList<>()).add(new Key(tick,value));
                }
            }
            TRACKS.values().forEach(keys->keys.sort(Comparator.comparingDouble(Key::tick)));
        } catch(Exception e){throw new IllegalStateException("Cannot load supplied idle animation",e);}
    }
    public static float loopTime(float elapsed) {if(!Float.isFinite(elapsed))return BEGIN;float t=Math.max(BEGIN,elapsed);return t<=END?t:RETURN+(t-END)%(END-RETURN);}
    public static float rest(String part,String axis) {
        if(axis.equals("x"))return switch(part){case "leftLeg"->1.9f;case "rightLeg"->-1.9f;case "leftArm"->5;case "rightArm"->-5;default->0;};
        if(axis.equals("y"))return part.endsWith("Leg")?12:part.endsWith("Arm")?2:0;
        return 0;
    }
    public static float offset(String part,String axis,float elapsed){return value(part,axis,elapsed)-rest(part,axis);}
    public static float value(String part,String axis,float elapsed) {
        var keys=TRACKS.get(part+"/"+axis);if(keys==null || keys.isEmpty())return rest(part,axis);
        float t=loopTime(elapsed);Key previous=new Key(BEGIN,rest(part,axis));
        for(var key:keys) {
            if(key.tick>=t) {
                if(key.tick==previous.tick)return key.value;
                float f=Math.clamp((t-previous.tick)/(key.tick-previous.tick),0,1);
                float eased=f<.5f?2*f*f:1-(float)Math.pow(-2*f+2,2)/2;
                return previous.value+(key.value-previous.value)*eased;
            }
            previous=key;
        }
        return previous.value;
    }
}
