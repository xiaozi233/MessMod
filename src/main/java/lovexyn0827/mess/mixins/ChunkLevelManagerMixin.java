package lovexyn0827.mess.mixins;

import lovexyn0827.mess.MessMod;
import lovexyn0827.mess.command.LazyLoadCommand;
import lovexyn0827.mess.fakes.ChunkLevelManagerInterface;
import lovexyn0827.mess.log.chunk.ChunkBehaviorLogger;
import lovexyn0827.mess.log.chunk.ChunkEvent;
import lovexyn0827.mess.util.blame.StackTrace;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkLevelManager.class)
public class ChunkLevelManagerMixin implements ChunkLevelManagerInterface {
    @Unique private ServerWorld world;

    @Inject(method = "shouldTickEntities", at = @At("HEAD"), cancellable = true)
	private void tickEntityIfNeeded(long pos, CallbackInfoReturnable<Boolean> cir) {
		if(!LazyLoadCommand.LAZY_CHUNKS.isEmpty()) {
			if(LazyLoadCommand.LAZY_CHUNKS.containsKey(this.world.getRegistryKey())
					|| LazyLoadCommand.LAZY_CHUNKS.get(this.world.getRegistryKey()).contains(pos)) {
				cir.setReturnValue(false);
				cir.cancel();
			}
		}
	}

    @Inject(method = "update", at = @At("HEAD"))
    protected void onTickNoArg(CallbackInfoReturnable<Boolean> cir) {
        if(ChunkBehaviorLogger.shouldSkip()) {
            return;
        }

        MessMod.INSTANCE.getChunkLogger().onEvent(ChunkEvent.CTM_TICK, ChunkPos.MARKER,
                this.getDimensionId(), Thread.currentThread(), StackTrace.blameCurrent(),
                null);
    }

    @Override
    public Identifier getDimensionId() {
        return this.world.getRegistryKey().getValue();
    }

    @Override
    public void initWorld(ServerWorld world) {
        this.world = world;
    }
}
