// RenderedBox.java (更新后)
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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class RenderedBox extends Shape {

	private Box box;

	public RenderedBox(Box box, int lineColor, int fillColor, int life, long gt) {
		super(lineColor, fillColor, life, gt);
		this.box = box;
	}

	public RenderedBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
					   int lineColor, int fillColor, int life, long gt) {
		super(lineColor, fillColor, life, gt);
		// 确保 min <= max
		this.box = new Box(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
				Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
	}

	@Override
	@Environment(EnvType.CLIENT)
	protected void renderFacesToBuffer(Matrix4f matrix, net.minecraft.client.render.BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		if (this.fa <= 0.001f) return; // 如果填充色完全透明，则不绘制面

		// 使用 ShapeRendererUtils (或内联逻辑) 构建面
		// 注意：ShapeRendererUtils.buildBoxFaces 的 xthick,ythick,zthick 参数现在用于条件性绘制面
		// 如果总是想画所有面，都传true
		ShapeRenderer.buildBoxFaces(matrix, builder,
				(float) (box.minX - renderEpsilon), (float) (box.minY - renderEpsilon), (float) (box.minZ - renderEpsilon),
				(float) (box.maxX + renderEpsilon), (float) (box.maxY + renderEpsilon), (float) (box.maxZ + renderEpsilon),
				true, true, true, // xthick, ythick, zthick -> true to draw all faces
				this.fr, this.fg, this.fb, this.fa // 填充色
		);
	}

	@Override
	@Environment(EnvType.CLIENT)
	protected void renderLinesToBuffer(Matrix4f matrix, BufferBuilder builder, double cameraX,
									   double cameraY, double cameraZ, float partialTick) {
		if (this.a <= 0.001f) return; // 如果线条颜色完全透明，则不绘制

		// 使用 ShapeRendererUtils (或内联逻辑) 构建线框
		// ShapeRendererUtils.buildBoxWireframe 的 xthick,ythick,zthick 参数现在用于条件性绘制边
		// 第二组颜色参数 (r2,g2,b2) 在简化版中被忽略
		ShapeRenderer.buildBoxWireframe(matrix, builder,
				(float) (box.minX - renderEpsilon), (float) (box.minY - renderEpsilon), (float) (box.minZ - renderEpsilon),
				(float) (box.maxX + renderEpsilon), (float) (box.maxY + renderEpsilon), (float) (box.maxZ + renderEpsilon),
				true, true, true, // xthick, ythick, zthick -> true to draw all edges
				this.r, this.g, this.b, this.a,  // 线条颜色
				0, 0, 0  // r2, g2, b2 (在简化版中未使用)
		);
	}

	// 移除旧的 renderFaces 和 renderLines 方法
	// @Override
	// @Environment(EnvType.CLIENT)
	// protected void renderFaces(MatrixStack matrices, Tessellator tessellator, double cx, double cy,
	// double cz, float partialTick) { ... }
	// @Override
	// @Environment(EnvType.CLIENT)
	// protected void renderLines(MatrixStack matrices, Tessellator tessellator, double cx, double cy,
	// double cz, float partialTick) { ... }


	@Override
	protected boolean shouldRender(RegistryKey<World> dimensionType) {
		// 这里可以添加维度检查逻辑，例如：
		// return dimensionType.equals(this.dimension); // 如果Shape保存了它属于的维度
		return true; // 目前总是渲染
	}

	@Override
	protected NbtCompound toTag(NbtCompound tag) {
		super.toTag(tag); // 调用父类方法填充通用字段
		tag.putDouble("X0", this.box.minX);
		tag.putDouble("Y0", this.box.minY);
		tag.putDouble("Z0", this.box.minZ);
		tag.putDouble("X1", this.box.maxX);
		tag.putDouble("Y1", this.box.maxY);
		tag.putDouble("Z1", this.box.maxZ);
		return tag;
	}
}