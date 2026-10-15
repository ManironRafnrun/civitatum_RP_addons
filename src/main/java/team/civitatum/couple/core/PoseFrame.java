package team.civitatum.couple.core;

import java.util.*;

/** Model-space pose; bend is a separate forearm/shin rotation, not a shoulder rotation. */
public final class PoseFrame {
    public record Bone(float pitch,float yaw,float roll,float bend,float x,float y,float z,boolean position){}
    public final Map<String,Bone> bones=new LinkedHashMap<>();
    public float weight=1,rootY,rootPitch,rootRoll;
    public PoseFrame bone(String name,float pitch,float yaw,float roll,float bend){bones.put(name,new Bone(pitch,yaw,roll,bend,0,0,0,false));return this;}
    public PoseFrame position(String name,float x,float y,float z){var b=bones.getOrDefault(name,new Bone(0,0,0,0,0,0,0,false));bones.put(name,new Bone(b.pitch,b.yaw,b.roll,b.bend,x,y,z,true));return this;}
    public PoseFrame overlay(PoseFrame top){top.bones.forEach((name,b)->{var a=bones.get(name);if(a==null){bones.put(name,b);return;}float w=top.weight;bones.put(name,new Bone(a.pitch+(b.pitch-a.pitch)*w,a.yaw+(b.yaw-a.yaw)*w,a.roll+(b.roll-a.roll)*w,a.bend+(b.bend-a.bend)*w,a.x,a.y,a.z,a.position));});return this;}
    public static float envelope(float t,int duration){float a=Math.clamp(t/12,0,1),b=duration==0?1:Math.clamp((duration-t)/12,0,1);float f=Math.min(a,b);return f*f*(3-2*f);}
    public static PoseFrame carry(boolean passenger,PoseSettings p,float walk,float speed){
        var f=new PoseFrame().bone("torso",passenger?-.1f:0,0,0,0).bone("head",passenger?p.headRadians():0,passenger?.25f:0,passenger?-.08f:0,0);
        if(passenger){
            f.bone("rightArm",-.65f,-.35f,.25f,-.7f).bone("leftArm",-.55f,.25f,-p.armRadians(),-.45f);
            f.bone("rightLeg",-.3f,.12f,.04f,.7f).bone("leftLeg",-.4f,-.12f,-.03f,.85f);
        } else {
            f.bone("rightArm",-.65f,-.22f,.04f,-1.05f).bone("leftArm",-.75f,.26f,-.04f,-1.05f);
            f.bone("rightLeg",(float)Math.cos(walk*.6662f)*1.4f*speed,0,0,0).bone("leftLeg",(float)Math.cos(walk*.6662f+Math.PI)*1.4f*speed,0,0,0);
        }
        return f;
    }
    public static PoseFrame social(Interaction action,boolean first,float time,int duration,float self,float other,boolean mounted){
        var f=new PoseFrame();f.weight=envelope(time,duration);
        for(String bone:List.of("head","torso","leftArm","rightArm","leftLeg","rightLeg"))f.bone(bone,0,0,0,0);
        if(action==Interaction.REST){
            f.rootY=-.42f;
            f.bone("rightArm",-.1f,0,.1f,-.65f).bone("leftArm",-.1f,0,-.1f,-.65f);
            if(!first){f.rootRoll=-.18f;f.bone("head",.22f,0,-.38f,0);f.rootY=Math.clamp(.87f*other/self-1.42f,-.61f,-.15f);}
            else f.bone("head",.1f,-.18f,.08f,0);
            float knee=(float)Math.acos(Math.clamp((.703125f+f.rootY-.375f*(float)Math.cos(1.45))/.375f,-.95f,.95f))+1.45f;
            f.bone("rightLeg",-1.45f,.12f,0,knee).bone("leftLeg",-1.45f,-.12f,0,knee);
            return f;
        }
        if(mounted){
            f.bones.clear();f.bone("head",first?.25f:.45f,first?-.25f:.15f,first?.1f:-.1f,0);
            if(first)f.bone("rightArm",-1.3f,-.6f,.15f,-1.0f);
            return f;
        }
        // Taller partners bend their knees to meet at the shorter partner's face height.
        float drop=Math.clamp((self-other)*1.5f/self,0,.61f);
        f.rootY=-drop;
        float knee=(float)(2*Math.acos(Math.clamp(1-drop/.703125f,.12f,1)));
        f.bone("leftLeg",-knee*.5f,0,0,knee).bone("rightLeg",-knee*.5f,0,0,knee);
        f.rootPitch=action==Interaction.KISS?-.08f:-.12f;
        if(action==Interaction.KISS){
            f.bone("head",first?.12f:.06f,first?-.08f:.08f,first?-.09f:.09f,0);
            if(first){
                float gap=.66f*(self+other)*.5f/self;
                float y=-4.8f,z=-gap*16+4.8f*other/self;
                float d=(float)Math.sqrt(y*y+z*z);float bend=(float)(2*Math.acos(Math.clamp(d/12,.15f,.99f)));
                float upper=(float)Math.atan2(z,y)+bend*.5f;
                f.bone("rightArm",upper,-.12f,-.12f,-bend);
            }else f.bone("leftArm",-.25f,0,-.12f,-.3f);
        }else {
            float reach=Math.clamp(other/self,.55f,1.5f);
            f.bone("head",.15f,first?-.2f:.2f,first?-.08f:.08f,0);
            f.bone("rightArm",-1.20f,-.5f,.18f,-.85f*reach).bone("leftArm",-1.12f,.5f,-.18f,-.9f*reach);
        }
        return f;
    }
}
