package lovexyn0827.mess.mixins;

import java.util.function.Predicate;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import lovexyn0827.mess.fakes.ServerPlayerEntityInterface;
import lovexyn0827.mess.options.OptionManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.FillCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockBox;

@Mixin(FillCommand.class)
public class FillCommandMixin {
	@Inject(method = "execute", at = @At("HEAD"))
	private static void onFillBegin(ServerCommandSource source, BlockBox range, BlockStateArgument block, FillCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> filter, boolean strict, CallbackInfoReturnable<Integer> cir) {
		if (OptionManager.fillHistory) {
			Entity entity = source.getEntity();
			if (entity instanceof ServerPlayerEntity) {
				((ServerPlayerEntityInterface) entity).getBlockPlacementHistory().beginOperation();
			} 
		}
	}
	
	@Inject(method = "execute", at = @At("RETURN"))
	private static void onFillFinish(ServerCommandSource source, BlockBox range, BlockStateArgument block, FillCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> filter, boolean strict, CallbackInfoReturnable<Integer> cir) {
		if(OptionManager.fillHistory) {
			Entity entity = source.getEntity();
			if (entity instanceof ServerPlayerEntity) {
				((ServerPlayerEntityInterface) entity).getBlockPlacementHistory().endOperation(false);
			} 
		}
	}
	
	@Inject(method = "execute", at = {
			@At(value = "INVOKE", target = "com/mojang/brigadier/exceptions/SimpleCommandExceptionType.create"
					+ "()Lcom/mojang/brigadier/exceptions/CommandSyntaxException;"), 
			@At(value = "INVOKE", target = "com/mojang/brigadier/exceptions/Dynamic2CommandExceptionType.create"
					+ "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/mojang/brigadier/exceptions/CommandSyntaxException;")}, 
			remap = false)
	private static void onFillFail(ServerCommandSource source, BlockBox range, BlockStateArgument block, FillCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> filter, boolean strict, CallbackInfoReturnable<Integer> cir) {
		if(OptionManager.fillHistory) {
			Entity entity = source.getEntity();
			if (entity instanceof ServerPlayerEntity) {
				((ServerPlayerEntityInterface) entity).getBlockPlacementHistory().endOperation(true);
			} 
		}
	}
	
	@Inject(
			method = "execute",
			at = @At(value = "FIELD", target = "Lnet/minecraft/server/command/FillCommand$Mode;filter:Lnet/minecraft/server/command/FillCommand$Filter;")
	)
	private static void onInventoryClean(ServerCommandSource source, BlockBox range, BlockStateArgument block, FillCommand.Mode mode, @Nullable Predicate<CachedBlockPosition> filter, boolean strict, CallbackInfoReturnable<Integer> cir,
										 @Local ServerWorld serverWorld, @Local BlockPos blockPos) {
		if(OptionManager.fillHistory) {
			Entity entity = source.getEntity();
			if (entity instanceof ServerPlayerEntity) {
				((ServerPlayerEntityInterface) entity).getBlockPlacementHistory()
						.preparePrevBlockEntityForTheNext(serverWorld.getBlockEntity(blockPos));
			} 
		}
	}
}
