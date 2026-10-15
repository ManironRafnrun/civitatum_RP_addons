package team.civitatum.couple;

import java.util.UUID;
import team.civitatum.couple.core.Interaction;
import team.civitatum.couple.core.RequestBook;
import team.civitatum.couple.core.RadialLayout;
import team.civitatum.couple.core.CarryPose;
import team.civitatum.couple.core.*;

public final class LogicChecks {
    private static int checks;
    public static void main(String[] args) {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID(), c=UUID.randomUUID();
        RequestBook book = new RequestBook();
        check(book.rateLimit(a, 100, 60), "first request allowed");
        check(!book.rateLimit(a, 101, 60), "repeated request throttled");
        check(book.rateLimit(a, 160, 60), "cooldown boundary allowed");
        check(book.create(a, a, Interaction.KISS, 400) == null, "self-invitation rejected");
        var r = book.create(a, b, Interaction.CARRY, 400);
        check(r != null && book.busy(a) && book.busy(b), "both participants reserved");
        check(book.create(c, b, Interaction.KISS, 400) == null, "incoming invitation cannot be overwritten");
        check(book.create(b, c, Interaction.KISS, 400) == null, "recipient cannot hold simultaneous outgoing request");
        check(book.decide(r.id(), c, 200) == null && book.size()==1, "forged acceptance does not consume real request");
        check(book.decide(UUID.randomUUID(), b, 200)==null, "unknown nonce rejected");
        check(book.decide(r.id(), b, 200).equals(r), "intended recipient can accept");
        check(book.decide(r.id(), b, 201)==null, "replayed response rejected");
        check(!book.busy(a) && !book.busy(b), "reservations released after decision");
        r=book.create(a,b,Interaction.KISS,450);
        check(book.decide(r.id(),b,450)==null && book.size()==0, "exact expiry cannot be accepted");
        r=book.create(a,b,Interaction.KISS,500);
        check(book.expire(499).isEmpty() && book.size()==1, "invitation lives until deadline");
        check(book.expire(500).size()==1 && !book.busy(a) && !book.busy(b), "expiry frees both players");
        r=book.create(a,b,Interaction.CARRY,600);
        check(book.cancel(a).equals(r) && !book.busy(b), "sender disconnect clears recipient reservation");
        r=book.create(a,b,Interaction.CARRY,600);
        check(book.cancel(b).equals(r) && !book.busy(a), "recipient disconnect clears sender reservation");
        check(book.create(a,b,Interaction.RELEASE,600)==null, "release never waits for consent");
        check(Interaction.fromWire(-1)==null && Interaction.fromWire(99)==null, "invalid action IDs rejected");
        for (int i=0;i<3;i++) {
            double angle=RadialLayout.centerAngle(i,3);
            check(RadialLayout.select(Math.cos(angle)*75,Math.sin(angle)*75,40,100,3)==i,"sector center selects matching action " + i);
        }
        check(RadialLayout.select(0,0,40,100,3)==-1,"center cancels");
        check(RadialLayout.select(1000,0,40,100,3)==-1,"outside wheel cancels");
        var front=CarryPose.modelDirection(0,0,-1);
        check(front.up()>0.85,"passenger lies face up instead of on their side");
        check(Math.abs(front.forward())<0.01,"body does not retain the carrier's forward facing");
        var head=CarryPose.modelPoint(0,-0.25,0);
        var hip=CarryPose.modelPoint(0,0.75,0);
        check(head.right()-hip.right()>0.8,"passenger lies across the carrier's arms");
        check(head.up()>hip.up(),"head is raised above the hips");
        var eye=CarryPose.eye();
        check(eye.up()>head.up(),"eyes face up above the head center");
        check(Math.abs(eye.right()-head.right())<.25 && Math.abs(eye.forward())<.25,"camera follows the newly tilted head");
        for(var mode:Consent.values())for(boolean trusted:new boolean[]{false,true})check(mode.automatic(trusted)==(mode==Consent.ALLOW || mode==Consent.TRUSTED && trusted),"consent decision "+mode+trusted);
        var prefs=new PlayerPreferences();check(prefs.policy(Interaction.HUG)==Consent.ASK,"new actions require consent by default");prefs.trusted.add(a);prefs.policies.put(Interaction.HUG,Consent.TRUSTED);prefs.pose=new PoseSettings(99,999,-999,99,-99,Float.NaN);
        var saved=PlayerPreferences.read(prefs.json());check(saved.trusted.contains(a) && !saved.trusted.contains(b),"trust survives as UUID");check(saved.policy(Interaction.HUG)==Consent.TRUSTED,"per-action policy roundtrip");check(saved.pose.head()==65 && saved.pose.arm()==110 && saved.pose.bank()==-80,"pose angles bounded");check(saved.pose.side()==.25f && saved.pose.up()==-.3f && saved.pose.forward()==0,"pose offsets bounded and finite");
        check(PlayerPreferences.read("invalid").policy(Interaction.KISS)==Consent.ASK,"bad policy falls back to asking");
        check(PlayerPreferences.legacy(false,true).policy(Interaction.CARRY)==Consent.BLOCK,"old opt-out migrates");
        for(int i=0;i<6;i++){double angle=RadialLayout.centerAngle(i,6);check(RadialLayout.select(Math.cos(angle)*75,Math.sin(angle)*75,40,100,6)==i,"six-sector wheel "+i);}
        check(CarryPose.HEAD_PITCH>0,"passenger head bends toward chest");
        check(PoseFrame.carry(true,PoseSettings.DEFAULT,0,0).bones.get("leftArm").roll()<-.8f,"passenger left arm clears the carrier face");
        check(PoseFrame.envelope(0,70)==0 && PoseFrame.envelope(12,70)==1 && PoseFrame.envelope(70,70)==0,"social animation blends in and out");
        for(var action:java.util.List.of(Interaction.KISS,Interaction.HUG,Interaction.REST))for(float scale:new float[]{.65f,1,1.5f}){
            var frame=PoseFrame.social(action,true,30,action==Interaction.REST?0:120,scale,1,false);
            check(Float.isFinite(frame.rootY) && frame.rootY>=-.61,"height-aware root stays bounded "+action+scale);
            check(frame.bones.values().stream().allMatch(v->Float.isFinite(v.pitch()) && Float.isFinite(v.bend())),"pose is finite "+action+scale);
        }
        var kiss=PoseFrame.social(Interaction.KISS,true,30,70,1,1,false);check(kiss.bones.get("rightArm").bend()<-.5f,"kiss actually bends the reaching elbow");
        var shortKiss=PoseFrame.social(Interaction.KISS,true,30,70,.9f,1.2f,false);var tallKiss=PoseFrame.social(Interaction.KISS,false,30,70,1.2f,.9f,false);check(tallKiss.rootY<shortKiss.rootY,"taller partner lowers to the other face");
        System.out.println("COUPLE_LOGIC_PASS checks="+checks);
    }
    private static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name);checks++; }
}
