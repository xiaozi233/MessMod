package lovexyn0827.mess.rendering;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public abstract class Shape {
	protected static final BiMap<String, Class<? extends Shape>> IDS = HashBiMap.create();
	public final int color;
	public final int life;
	protected float r;
	protected float g;
	protected float b;
	protected float a;
	protected float fr;
	protected float fg;
	protected float fb;
	protected float fa;
	private long createdTime;
	double renderEpsilon = 0;
	private int fill;

	protected Shape(int color, int fill, int life, long gt) {
		this.color = color;
		this.fill = fill;
		this.life = life;
		this.createdTime = gt;
        this.fr = (float)(fill >> 24 & 0xFF) / 255.0F;
        this.fg = (float)(fill >> 16 & 0xFF) / 255.0F;
        this.fb = (float)(fill >>  8 & 0xFF) / 255.0F;
        this.fa = (float)(fill & 0xFF) / 255.0F;
        this.r = (float)(color >> 24 & 0xFF) / 255.0F;
        this.g = (float)(color >> 16 & 0xFF) / 255.0F;
        this.b = (float)(color >>  8 & 0xFF) / 255.0F;
        this.a = (float)(color & 0xFF) / 255.0F;
	}

	@Environment(EnvType.CLIENT)
	protected abstract void renderFacesToBuffer(Matrix4f matrix, BufferBuilder builder, double cameraX,
												double cameraY, double cameraZ, float partialTick);
	@Environment(EnvType.CLIENT)
	protected abstract void renderLinesToBuffer(Matrix4f matrix, BufferBuilder builder, double cameraX,
												double cameraY, double cameraZ, float partialTick);



//	@Environment(EnvType.CLIENT)
//	protected abstract void renderFaces(MatrixStack matrices, Tessellator tessellator, double cameraX,
//			double cameraY, double cameraZ, float partialTick);
//
//	@Environment(EnvType.CLIENT)
//	protected abstract void renderLines(MatrixStack matrices, Tessellator tessellator, double cameraX,
//			double cameraY, double cameraZ, float partialTick);

	protected abstract boolean shouldRender(RegistryKey<World> dimensionType);

	protected boolean isExpired(long gameTime) {
		return this.life + this.createdTime - gameTime < 0;
	}

	protected NbtCompound toTag(NbtCompound tag) {
		tag.putString("ID", IDS.inverse().get(this.getClass()));
		tag.putInt("Color", this.color);
		tag.putInt("Fill", this.fill);
		tag.putInt("Life", this.life);
		tag.putLong("GT", this.createdTime);
		return tag;
	}

	// Shape.java - fromTag 方法 (假设标准 NbtCompound，并且接受键不存在时返回0的行为)
	public static Shape fromTag(NbtCompound tag) {
		// getString(key, fallback) 是一个好方法，如果键不存在或不是字符串，它会返回fallback
		// 或者我们可以用 getString(key).orElse("")
		String id = tag.getString("ID", ""); // 如果 "ID" 不存在或不是字符串，返回空字符串

		// 使用 getTYPE(key, fallbackValue) 更简洁且安全
		// 或者使用 getTYPE(key).orElse(fallbackValue)

        return switch (id) {
            case "box" -> new RenderedBox(
                    tag.getDouble("X0", 0.0), // 如果X0不存在或不是double，则为0.0
                    tag.getDouble("Y0", 0.0),
                    tag.getDouble("Z0", 0.0),
                    tag.getDouble("X1", 0.0),
                    tag.getDouble("Y1", 0.0),
                    tag.getDouble("Z1", 0.0),
                    tag.getInt("Color", 0xFFFFFFFF), // 默认白色不透明
                    tag.getInt("Fill", 0x00000000),   // 默认完全透明填充
                    tag.getInt("Life", 0),          // 默认生命值为0 (可能代表永生或需要特殊处理)
                    tag.getLong("GT", 0L)
            );
            case "line" -> new RenderedLine(
                    new Vec3d(tag.getDouble("X0", 0.0), tag.getDouble("Y0", 0.0), tag.getDouble("Z0", 0.0)),
                    new Vec3d(tag.getDouble("X1", 0.0), tag.getDouble("Y1", 0.0), tag.getDouble("Z1", 0.0)),
                    tag.getInt("Color", 0xFFFFFFFF),
                    tag.getInt("Life", 0),
                    tag.getLong("GT", 0L)
            );
            case "text" -> new RenderedText(
                    tag.getString("Value", ""), // 如果Value不存在或不是字符串，则为空字符串
                    new Vec3d(tag.getDouble("X", 0.0), tag.getDouble("Y", 0.0), tag.getDouble("Z", 0.0)),
                    tag.getInt("Color", 0xFFFFFFFF),
                    tag.getInt("Life", 0),
                    tag.getLong("GT", 0L)
            );
            default -> {
                if (!id.isEmpty()) { // 只在 ID 不是我们期望的空字符串时打印错误
                    System.err.println("Unknown shape ID from NBT: " + id);
                }
                yield null;
            }
        };
	}

	static {
		IDS.put("box", RenderedBox.class);
		IDS.put("line", RenderedLine.class);
		IDS.put("text", RenderedText.class);
	}
}
