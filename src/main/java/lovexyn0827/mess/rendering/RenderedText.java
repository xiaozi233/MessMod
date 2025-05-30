// RenderedText.java (更新后)
package lovexyn0827.mess.rendering;

import net.minecraft.client.render.BufferBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
// import net.minecraft.client.render.RenderLayer; // 不再需要自己创建 BufferAllocator
// import net.minecraft.client.render.Tessellator; // 不再需要
import net.minecraft.client.render.VertexConsumerProvider;
// import net.minecraft.client.util.BufferAllocator; // 不再需要自己创建
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString; // 确保这个 import
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class RenderedText extends Shape {

	private final String value;
	private final Vec3d pos;

	public RenderedText(String value, Vec3d pos, int color, int life, long gt) {
		// 背景色 (fill) 0x0000002f 意味着非常暗淡的近乎透明的黑色背景
		super(color, 0x0000002F, life, gt); // 将背景色传给父类
		this.value = value;
		this.pos = pos;
	}

	@Override
	protected void renderFacesToBuffer(BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		// 文本没有“面”通过这种方式渲染
	}

	@Override
	protected void renderLinesToBuffer(BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		// 文本也不是通过画“线”来渲染的
		// 如果需要文本的边界框，可以在这里画，但实际文本渲染不在这里
	}

	/**
	 * 专门用于渲染实际文本的方法。
	 * 它将在 ShapeRenderer 中被特别调用。
	 * @param matrixStack 当前的 MatrixStack，已经应用了相机视图变换。
	 * @param vertexConsumers VertexConsumerProvider 用于文本渲染。
	 * @param camera 当前相机，用于获取旋转。
	 */
	@Environment(EnvType.CLIENT)
	public void renderActualText(MatrixStack matrixStack, VertexConsumerProvider.Immediate vertexConsumers, Camera camera, float partialTick) {
		if (this.a <= 0.001f && this.fa <= 0.001f) return; // Skip if both text and background are fully transparent

		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer textRenderer = client.textRenderer;

		int backgroundColor = 0; // Default to fully transparent background
		if (this.fa > 0.001f) { // Only calculate background if alpha is significant
			backgroundColor = ((int)(this.fa * 255.0F) << 24) |
					((int)(this.fr * 255.0F) << 16) |
					((int)(this.fg * 255.0F) << 8)  |
					((int)(this.fb * 255.0F));
		}

		// Text color (this.color already includes alpha from this.a)
		int textColor = this.color;
		if (this.a <= 0.001f && this.fa > 0.001f) { // If text is transparent but background is not, make text opaque black or white
			textColor = 0xFF000000; // Or 0xFFFFFFFF depending on desired contrast with background
		}


		matrixStack.push();
		matrixStack.translate(pos.x - camera.getPos().x, pos.y - camera.getPos().y, pos.z - camera.getPos().z);
		matrixStack.multiply(camera.getRotation());
		float scale = 0.020f; // Adjusted scale, was 0.025f
		matrixStack.scale(scale, -scale, scale);

		float textWidth = textRenderer.getWidth(this.value);
		textRenderer.draw(this.value,
				-textWidth / 2.0f,
				0,
				textColor,
				false, // shadow
				matrixStack.peek().getPositionMatrix(),
				vertexConsumers,
				TextRenderer.TextLayerType.SEE_THROUGH, // Allows transparency for text and background
				backgroundColor,
				15728880 // Light (full bright)
		);
		matrixStack.pop();
	}


	@Override
	protected boolean shouldRender(RegistryKey<World> dimensionType) {
		return true;
	}

	@Override
	protected NbtCompound toTag(NbtCompound tag) {
		super.toTag(tag);
		tag.putDouble("X", this.pos.x);
		tag.putDouble("Y", this.pos.y);
		tag.putDouble("Z", this.pos.z);
		tag.put("Value", NbtString.of(this.value)); // 使用 NbtString.of()
		return tag;
	}
}