// RenderedLine.java (更新后)
package lovexyn0827.mess.rendering;

// import com.mojang.blaze3d.systems.RenderSystem; // 不再需要直接调用RenderSystem
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
// import net.minecraft.client.render.Tessellator; // 不再需要
// import net.minecraft.client.util.math.MatrixStack; // 不再需要，用 Matrix4f
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f; // 新增
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class RenderedLine extends Shape {

	private final Vec3d from;
	private final Vec3d to;

	public RenderedLine(Vec3d from, Vec3d to, int color, int life, long gt) { // 参数顺序调整为 from, to
		super(color, 0, life, gt); // fill color is 0 (or irrelevant for lines)
		this.from = from;
		this.to = to;
	}

	@Override
	@Environment(EnvType.CLIENT)
	protected void renderFacesToBuffer(Matrix4f matrix, BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		// Lines do not have faces, so this method is empty.
	}

	@Override
	@Environment(EnvType.CLIENT)
	protected void renderLinesToBuffer(Matrix4f matrix, BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		if (this.a <= 0.001f) return; // 如果线条颜色完全透明，则不绘制

		// RenderSystem.lineWidth(2); // lineWidth 设置移至 RenderPipeline 或 RenderSystem 全局 (如果还支持)
		// 注意：RenderSystem.lineWidth() 在1.21.5中仍然存在，但它设置的是一个全局的 shaderLineWidth 变量。
		// 实际的线宽是否生效取决于 SHAPE_LINES_PIPELINE 使用的着色器是否读取和应用这个变量。
		// 核心OpenGL通常不支持轻易改变非抗锯齿线的宽度大于1。
		// 如果 SHAPE_LINES_PIPELINE 配置了 GL_LINE_SMOOTH 并且 GPU 支持，lineWidth 才可能有效。
		// 默认情况下，假设线宽为1。如果确实需要粗线，这可能需要自定义着色器或更复杂的几何体（例如用两个三角形组成一条粗线）。

		// 使用 ShapeRendererUtils.buildLine
		// 注意：renderEpsilon 的应用方式可能需要调整。
		// 如果 renderEpsilon 是为了避免Z-fighting，将其加到所有坐标上可能不总是正确。
		// 这里暂时保持原样，但其效果可能与旧版不同，因为矩阵变换的方式变了。
		// 一个更稳妥的做法可能是在构建RenderPipeline时使用depthBias。
		ShapeRenderer.buildLine(matrix, builder,
				(float) (from.x - renderEpsilon), (float) (from.y - renderEpsilon), (float) (from.z - renderEpsilon),
				(float) (to.x + renderEpsilon),   (float) (to.y + renderEpsilon),   (float) (to.z + renderEpsilon), // 通常epsilon应该一致地应用
				this.r, this.g, this.b, this.a,
				0, 1, 0 // 示例法线，对于无光照线不重要
		);
	}

	// 移除旧的 renderFaces 和 renderLines 方法
	// @Override
	// @Environment(EnvType.CLIENT)
	// protected void renderFaces(MatrixStack matrices, Tessellator tessellator, double cx, double cy,
	// double cz, float partialTick) { }
	// @Override
	// @Environment(EnvType.CLIENT)
	// protected void renderLines(MatrixStack matrices, Tessellator tessellator, double cx, double cy,
	// double cz, float partialTick) { ... }

	@Override
	protected boolean shouldRender(RegistryKey<World> dimensionType) {
		return true;
	}

	@Override
	protected NbtCompound toTag(NbtCompound tag) {
		super.toTag(tag);
		tag.putDouble("X0", this.from.x);
		tag.putDouble("Y0", this.from.y);
		tag.putDouble("Z0", this.from.z);
		tag.putDouble("X1", this.to.x);
		tag.putDouble("Y1", this.to.y);
		tag.putDouble("Z1", this.to.z);
		return tag;
	}
}