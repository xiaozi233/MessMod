package lovexyn0827.mess.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import lovexyn0827.mess.fakes.AbstractBoatEntityInterface;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoatEntity.class)
public class AbstractBoatEntityMixin implements AbstractBoatEntityInterface {
    @Unique private static float velocityDecay;

    @Inject(method = "updateVelocity", at = @At("TAIL"))
    private void captureVelocityDecay(CallbackInfo ci, @Local(ordinal = 0) float vD){
        velocityDecay = vD;
    }

    @Override
    public float getVelocityDeacyMCWMEM() {
        return velocityDecay;
    }
}
