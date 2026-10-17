package team.civitatum.charactercreation;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Sparse Emotecraft tracks from the supplied idle.json, with its return tick and quadratic easing.
 *
 * <p>The file is read straight from the jar, so it must not lie in {@code assets/<namespace>/animations/}: GeckoLib
 * loads every JSON in such folders of every mod as one of its animations, and a file in another format makes it drop
 * all GeckoLib animations of every mod.
 */
public final class IdleAnimation {
    private record Key(float tick,float value) {}
    private static final Map<String,List<Key>> TRACKS=new LinkedHashMap<>();
    public static final float BEGIN,RETURN,END;
    public static final float PLAYBACK_RATE=.5f,HOLD_TICKS=40;
    static {
        try(var in=IdleAnimation.class.getResourceAsStream("/emote/idle.json")) {
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
    /** Real game ticks -> authored ticks: half speed, then two seconds holding the final pose. */
    public static float playbackTime(float elapsed) {
        if(!Float.isFinite(elapsed) || elapsed<=0)return BEGIN;
        float first=(END-BEGIN)/PLAYBACK_RATE;
        if(elapsed<first)return BEGIN+elapsed*PLAYBACK_RATE;
        if(elapsed<first+HOLD_TICKS)return END;
        float movement=(END-RETURN)/PLAYBACK_RATE;
        float cycle=(elapsed-first-HOLD_TICKS)%(movement+HOLD_TICKS);
        return cycle<movement?RETURN+cycle*PLAYBACK_RATE:END;
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
