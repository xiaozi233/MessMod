package lovexyn0827.mess.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lovexyn0827.mess.MessMod;
import lovexyn0827.mess.fakes.ChunkLevelManagerInterface;
import lovexyn0827.mess.log.chunk.ChunkBehaviorLogger;
import lovexyn0827.mess.log.chunk.ChunkEvent;
import lovexyn0827.mess.options.OptionManager;
import lovexyn0827.mess.util.blame.BlamingMode;
import lovexyn0827.mess.util.blame.StackTrace;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkPos;

@Mixin(ChunkTicketManager.class)
public class ChunkTicketManagerMixin {
//	@Unique private ServerWorld world;
	
	@Inject(method = "addTicket(Lnet/minecraft/server/world/ChunkTicket;Lnet/minecraft/util/math/ChunkPos;)V",
			at = @At(value = "HEAD"),
			cancellable = true
	)
	private void returnIfNeeded(ChunkTicket ticket, ChunkPos pos, CallbackInfo ci) {
		if(OptionManager.rejectChunkTicket.contains(ticket.getType())) {
			ci.cancel();
		}
	}

	@Inject(method = "addTicket(JLnet/minecraft/server/world/ChunkTicket;)Z",
			at = @At(value = "RETURN")
	)
	private void onTicketAdded(long pos, ChunkTicket ticket, CallbackInfoReturnable<Boolean> cir) {
		if(!ChunkBehaviorLogger.shouldSkip()) {
			MessMod.INSTANCE.getChunkLogger().onEvent(ChunkEvent.TICKET_ADDITION, pos, ((ChunkLevelManagerInterface) this).getDimensionId(),
					Thread.currentThread(), 
					OptionManager.blamingMode == BlamingMode.DISABLED ? null : StackTrace.current().blame(), 
					ticket);
		}
	}
	
	@Inject(method = "removeTicket(JLnet/minecraft/server/world/ChunkTicket;)Z",
			at = @At(value = "RETURN")
	)
	private void onTicketRemoved(long pos, ChunkTicket ticket, CallbackInfoReturnable<Boolean> cir) {
		if(!ChunkBehaviorLogger.shouldSkip()) {
			MessMod.INSTANCE.getChunkLogger().onEvent(ChunkEvent.TICKET_REMOVAL, pos, ((ChunkLevelManagerInterface) this).getDimensionId(),
					Thread.currentThread(), StackTrace.blameCurrent(), ticket);
		}
	}

//	@Inject(method = "shouldTickEntities", at = @At("HEAD"), cancellable = true)
//	private void tickEntityIfNeeded(long pos, CallbackInfoReturnable<Boolean> cir) {
//		if(!LazyLoadCommand.LAZY_CHUNKS.isEmpty()) {
//			if(LazyLoadCommand.LAZY_CHUNKS.containsKey(this.world.getRegistryKey())
//					|| LazyLoadCommand.LAZY_CHUNKS.get(this.world.getRegistryKey()).contains(pos)) {
//				cir.setReturnValue(false);
//				cir.cancel();
//			}
//		}
//	}
	
//	@Inject(method = "update", at = @At("HEAD"))
//	protected void onTickNoArg(CallbackInfoReturnable<Boolean> cir) {
//		if(ChunkBehaviorLogger.shouldSkip()) {
//			return;
//		}
//
//		MessMod.INSTANCE.getChunkLogger().onEvent(ChunkEvent.CTM_TICK, ChunkPos.MARKER,
//				this.getDimesionId(), Thread.currentThread(), StackTrace.blameCurrent(),
//				null);
//	}

//	@Override
//	public Identifier getDimesionId() {
//		return this.world.getRegistryKey().getValue();
//	}
//
//	@Override
//	public void initWorld(ServerWorld world) {
//		this.world = world;
//	}
}
