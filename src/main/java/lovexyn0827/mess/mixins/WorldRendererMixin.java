package lovexyn0827.mess.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import lovexyn0827.mess.MessMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.WorldRenderer;

import net.minecraft.client.util.math.MatrixStack;
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
	
	@Inject(method = "renderParticles", 
			at = @At(
					value = "RETURN"
			)
	)
	private void renderShapes(FrameGraphBuilder frameGraphBuilder, Camera camera, float tickProgress, Fog fog, CallbackInfo ci) {
		if (MessMod.INSTANCE.shapeRenderer != null) { // 确保 world 不为 null
			FramePass pass = frameGraphBuilder.createPass("messmod_shapes");
			if (this.framebufferSet.mainFramebuffer == null || this.framebufferSet.mainFramebuffer.get() == null) {
				return;
			}
			this.framebufferSet.mainFramebuffer = pass.transfer(this.framebufferSet.mainFramebuffer);

			pass.setRenderer(() -> {
				MatrixStack matricesForShapeRenderer = new MatrixStack();
				// RenderSystem.getModelViewMatrix() 返回的是当前全局模型视图矩阵 (Matrix4f)
				// 它应该是由 WorldRenderer.render 设置的，包含了相机视图变换
				matricesForShapeRenderer.multiplyPositionMatrix(RenderSystem.getModelViewMatrix());

				MessMod.INSTANCE.shapeRenderer.render(matricesForShapeRenderer, camera, tickProgress);
			});
		}
	}
}
