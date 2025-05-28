package lovexyn0827.mess.mixins;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.state.ContextAwareNetworkStateFactory;
import net.minecraft.network.state.NetworkStateFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lovexyn0827.mess.command.LogPacketCommand;
import net.minecraft.network.state.NetworkState;
import net.minecraft.network.state.NetworkStateBuilder;

@Mixin(NetworkStateBuilder.class)
public class NetworkStateBuilderMixin<T extends PacketListener, B extends ByteBuf, C> {
	@Shadow
	@Final
	private List<NetworkStateBuilder.PacketType<T, ?, B, C>> packetTypes;

	// 根据新版本的方法签名调整注入点
	@Inject(method = "buildContextAwareFactory", at = @At("HEAD"))
	private void capturePacketTypes(CallbackInfoReturnable<ContextAwareNetworkStateFactory<T, B, C>> cir) {
		this.packetTypes.stream()
				.map(t -> t.type().id()) // 使用type()代替旧版的id()
				.forEach(LogPacketCommand.PACKET_TYPES::add);
	}
//	@Shadow
//	@Final
//	private List<NetworkStateBuilder.PacketType<?, ?, ?>> packetTypes;
//
//	@Inject(method = {"buildFactory"}, at = @At("HEAD"))
//	private void capturePacketTypes(CallbackInfoReturnable<NetworkState.Factory<?, ?>> cir) {
//		this.packetTypes.stream().map((t) -> t.id().id()).forEach(LogPacketCommand.PACKET_TYPES::add);
//	}
}