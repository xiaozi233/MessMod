package lovexyn0827.mess.mixins;

import net.minecraft.server.world.ChunkLevelManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.mess.MessMod;
import lovexyn0827.mess.fakes.ChunkLevelManagerInterface;
import lovexyn0827.mess.log.chunk.ChunkBehaviorLogger;
import lovexyn0827.mess.log.chunk.ChunkEvent;
import lovexyn0827.mess.util.blame.StackTrace;

@Mixin(targets = "net.minecraft.server.world.ChunkLevelManager$NearbyChunkTicketUpdater")
public class ChunkLevelManagerNearbyChunkTicketUpdaterMixin {
	@Shadow @Final ChunkLevelManager field_17463;
	
	@Inject(method = "updateTicket", at = @At("HEAD"))
	private void updateTicket(long pos, int distance, 
			boolean oldWithinViewDistance, boolean withinViewDistance, CallbackInfo ci) {
		if(ChunkBehaviorLogger.shouldSkip()) {
			return;
		}
		
		MessMod.INSTANCE.getChunkLogger().onEvent(ChunkEvent.PLAYER_TICKER_UPDATE, pos, 
				((ChunkLevelManagerInterface) this.field_17463).getDimensionId(), Thread.currentThread(),
				StackTrace.blameCurrent(), 
				String.format("%s -> %s[%d]", oldWithinViewDistance, withinViewDistance, distance));
	}
}
