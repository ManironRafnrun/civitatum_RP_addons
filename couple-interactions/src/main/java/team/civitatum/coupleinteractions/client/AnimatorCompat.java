package team.civitatum.coupleinteractions.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import team.civitatum.coupleinteractions.CoupleInteractions;
import team.civitatum.coupleinteractions.core.PoseFrame;

/** Loaded only when PlayerAnimator is present. Emotecraft's bundled libraries are reused. */
public final class AnimatorCompat {
    public static void init(){
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CoupleInteractions.id("interactions"),10000,Animation::new);
        PoseEngine.animator=true;CoupleInteractions.LOGGER.info("CCI uses PlayerAnimator; bending enabled: {}",net.neoforged.fml.ModList.get().isLoaded("bendylib"));
    }
    /** Our custom root transform can cancel vanilla setupRotations before the library's RETURN hook. */
    public static void prepare(AbstractClientPlayer player,float partial){var layer=PlayerAnimationAccess.getPlayerAssociatedData(player).get(CoupleInteractions.id("interactions"));if(layer!=null)layer.setupAnim(partial);}
    private static final class Animation implements IAnimation {
        private final AbstractClientPlayer player;private PoseFrame frame;
        Animation(AbstractClientPlayer p){player=p;}
        public boolean isActive(){return PoseEngine.frame(player,0)!=null;}
        public void setupAnim(float partial){frame=PoseEngine.frame(player,partial);}
        public Vec3f get3DTransform(String name,TransformType type,float partial,Vec3f old){
            if(frame==null)return old;var bone=frame.bones.get(name);if(bone==null)return old;float w=frame.weight;
            return switch(type){
                case ROTATION->new Vec3f(mix(old.getX(),bone.pitch(),w),mix(old.getY(),bone.yaw(),w),mix(old.getZ(),bone.roll(),w));
                case BEND->new Vec3f(0,bone.bend()*w,0);
                case POSITION->bone.position()?new Vec3f(mix(old.getX(),bone.x(),w),mix(old.getY(),bone.y(),w),mix(old.getZ(),bone.z(),w)):old;
                default->old;
            };
        }
        private static float mix(float a,float b,float w){return a+(b-a)*w;}
    }
    private AnimatorCompat(){}
}
