package lovexyn0827.mess.mixins;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.mess.options.OptionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Mixin(TntEntity.class)
public abstract class TntEntityMixin extends Entity{
//	private static final ChunkTicketType<? super Entity> ENTITY_TICKET = ChunkTicketType.create("tnt", (a, b) -> 1, 3);
//	private static final ChunkTicketType<? super Entity> PERMANENT_ENTITY_TICKET = ChunkTicketType.create("tnt_permanent", (a, b) -> 1);
	@Unique
	private static final ChunkTicketType ENTITY_TICKET = Registry.register(
		Registries.TICKET_TYPE,
		Identifier.of("messmod", "tnt"),
		new ChunkTicketType(
				3L,  // 3 ticks后过期
				false, // 不持久化
				ChunkTicketType.Use.LOADING_AND_SIMULATION
		)
	);
	@Unique
	private static final ChunkTicketType PERMANENT_ENTITY_TICKET = Registry.register(
			Registries.TICKET_TYPE,
			Identifier.of("messmod", "tnt_permanent"),
			new ChunkTicketType(
					0L,  // 永不过期
					true, // 持久化
					ChunkTicketType.Use.LOADING_AND_SIMULATION
			)
	);
	
	private TntEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@SuppressWarnings("resource")
	@Inject(method = "tick",
			at = @At("TAIL")
			)
	private void loadChunkIfNeeded(CallbackInfo ci) {
		if(!this.getWorld().isClient) {
			if(OptionManager.tntChunkLoading) {
				ServerWorld world = (ServerWorld)this.getWorld();
				Vec3d nextPos = this.getPos();
				ChunkTicketType tt = OptionManager.tntChunkLoadingPermanence ? PERMANENT_ENTITY_TICKET : ENTITY_TICKET;
				world.getServer().submitAndJoin(() -> world.getChunkManager().addTicket(tt,
						new ChunkPos((int)(nextPos.x / 16), (int)(nextPos.z / 16)), OptionManager.tntChunkLoadingRange));
			}
		}
	}
	
	@Override
	public boolean handleAttack(Entity attacker) {
		if(OptionManager.attackableTnt) {
			this.remove(RemovalReason.KILLED);
			if(attacker.isSneaking()) {
				this.getWorld().getEntitiesByType(EntityType.TNT, this.getBoundingBox(), (e) -> true).forEach((e) -> e.remove(RemovalReason.KILLED));
			}
			
			return true;
		} else {
			return super.handleAttack(attacker);
		}
	}
}
