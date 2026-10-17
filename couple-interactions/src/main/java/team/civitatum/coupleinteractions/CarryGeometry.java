package team.civitatum.coupleinteractions;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import team.civitatum.coupleinteractions.core.CarryPose;

/** Shared anchor, deliberately independent of the passenger's look direction. */
public final class CarryGeometry {
    // The sideways body spans the carrier's arms; a standing-width box lets it enter walls.
    public static final EntityDimensions DIMENSIONS = EntityDimensions.scalable(1.6F, 0.9F).withEyeHeight(0.6F);
    public static final double FORWARD = 0.48;
    public static final double HEIGHT = 0.86;

    public static Vec3 anchor(Player carrier, float partialTick) {
        Player passenger=team.civitatum.coupleinteractions.network.ClientBridge.previewPassenger.apply(carrier);
        if(passenger==null){var id=CarryHooks.passengerId(carrier);if(id!=null)passenger=carrier.level().getPlayerByUUID(id);}
        return anchor(carrier,passenger,partialTick);
    }
    public static Vec3 anchor(Player carrier,Player passenger,float partialTick){
        float yaw = Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
        Vec3 base = carrier.getPosition(partialTick);
        double scale = carrier.getScale();
        double other=passenger==null?scale:passenger.getScale();
        var tune=passenger==null?team.civitatum.coupleinteractions.core.PoseSettings.DEFAULT:CarryHooks.tuning(passenger);
        return base.add(local(yaw,tune.side()*scale,(1.10*scale-.24*other)+tune.up()*scale,(FORWARD+tune.forward())*scale+.07*Math.max(0,other-scale)));
    }

    public static Vec3 anchorNow(Player carrier) {
        return anchor(carrier,1);
    }

    public static Vec3 head(Player carried, Player carrier, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
        CarryPose.Point eye = CarryPose.eye(CarryHooks.tuning(carried));
        double scale = carried.getScale();
        return anchor(carrier,carried,partialTick).add(local(yaw, eye.right() * scale, eye.up() * scale, eye.forward() * scale));
    }

    /** right / up / forward in the carrier's coordinate frame. */
    public static Vec3 local(float yaw, double right, double up, double forward) {
        double a = Math.toRadians(yaw);
        return new Vec3(-Math.cos(a) * right - Math.sin(a) * forward, up, -Math.sin(a) * right + Math.cos(a) * forward);
    }
    private CarryGeometry() {}
}
