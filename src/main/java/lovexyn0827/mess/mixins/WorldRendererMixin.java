package lovexyn0827.mess.mixins;

import lovexyn0827.mess.MessMod;
import net.minecraft.client.render.*;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//A slight modified version of WorldRenderer_scarpetRenderMixin
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Shadow @Final
	private DefaultFramebufferSet framebufferSet;

//	@Inject(
//			method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
//			at = @At(
//					value = "INVOKE",
//					target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V",
//					shift = At.Shift.BEFORE
//			)
//	)
//	private void messMod$onRenderWorldLast(
//			// === 方法 execute 的原始参数 ===
//			// 这些参数我们可能仍然需要，例如 camera, tickCounter
//			ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
//			Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix,
//			// === CallbackInfo ===
//			CallbackInfo ci,
//			// === 使用 @Local 捕获局部变量 ===
//			// 捕获 WorldRenderer.render 方法内部的 matrix4fStack (类型是 org.joml.Matrix4fStack)
//			// 和 frameGraphBuilder (类型是 FrameGraphBuilder)
//			// 我们需要确保在注入点，这些类型的局部变量是明确的。
//			// 通常，方法内的局部变量名在编译后会丢失，MixinExtras 通过类型和顺序/索引来定位。
//			// 如果有多个同类型的局部变量，需要使用 ordinal 或 index。
//			// 假设在注入点，只有一个 Matrix4fStack 和一个 FrameGraphBuilder 类型的局部变量在作用域内，
//			// 或者它们是该类型的第一个 (ordinal = 0)。
//			@Local FrameGraphBuilder frameGraphBuilder, // 捕获 FrameGraphBuilder
//			@Local Matrix4fStack capturedRenderSystemMatrixStack // 捕获 org.joml.Matrix4fStack
//			// 这个是 RenderSystem.getModelViewStack() 的结果，
//			// 并且在 WorldRenderer.render 中被 mul(positionMatrix)
//	) {
//		if (MessMod.INSTANCE.shapeRenderer != null) {
//			// 确保 frameGraphBuilder 不是 null (虽然 @Local 通常能保证捕获到非 null 值，除非它本身就是 null)
//			if (frameGraphBuilder == null || capturedRenderSystemMatrixStack == null) {
//				System.err.println("MessMod Error: Failed to capture local variables in WorldRendererMixin.");
//				return;
//			}
//
//			FramePass pass = frameGraphBuilder.createPass("messmod_shapes");
//			if (this.framebufferSet.mainFramebuffer == null || this.framebufferSet.mainFramebuffer.get() == null) {
//				return;
//			}
//			this.framebufferSet.mainFramebuffer = pass.transfer(this.framebufferSet.mainFramebuffer);
//
//			pass.setRenderer(() -> {
//				// 创建一个新的 Minecraft MatrixStack
//				MatrixStack mcMatrixStack = new MatrixStack();
//				// capturedRenderSystemMatrixStack 本身就是栈顶的 Matrix4f (因为它 extends Matrix4f)
//				// 将其状态应用到新的 mcMatrixStack
//				mcMatrixStack.multiplyPositionMatrix(capturedRenderSystemMatrixStack);
//
//				MessMod.INSTANCE.shapeRenderer.render(mcMatrixStack, camera, tickCounter.getTickProgress(false));
//			});
//		}
//	}
	@Inject(method = "renderParticles",
			at = @At(
					value = "RETURN"
			)
	)
	private void renderShapes(FrameGraphBuilder frameGraphBuilder, Camera camera, float tickProgress, Fog fog, CallbackInfo ci) {
		if (MessMod.INSTANCE.shapeRenderer != null) {
			FramePass pass = frameGraphBuilder.createPass("messmod_shapes");
			this.framebufferSet.mainFramebuffer = pass.transfer(this.framebufferSet.mainFramebuffer);
			pass.setRenderer(() -> MessMod.INSTANCE.shapeRenderer.render(null, camera, 1.0F));
		}
	}
}
