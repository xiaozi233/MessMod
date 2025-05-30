/* MIT License
 *
 * Copyright (c) 2020 gnembon
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package lovexyn0827.mess.rendering;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import org.joml.Matrix4f;

/**
 * A modified version of carpet.script.util.ShapesRenderer
 * Original Author : gnembon
 */
public class ShapeRenderer {
    //private final Map<RegistryKey<World>, Map<ShapeSpace, Set<Shape>>> shapes;
	private final ShapeCache shapes;
	private final MinecraftClient client;
    private static RenderPipeline SHAPE_LINES_PIPELINE;
    private static RenderPipeline SHAPE_FACES_PIPELINE;
    private static boolean pipelinesInitialized = false;
    private static final float currentLineWidth = 1.0f; // 可以添加一个字段来控制当前线宽

    public ShapeRenderer(MinecraftClient mc) {
        this.shapes = ShapeCache.create(mc);
        this.client = mc;
        InitializePipelines();
    }

    public void close() {
    	this.shapes.close();
    }
    
    public ShapeCache getShapeCache() {
		return this.shapes;
	}

    private static synchronized void InitializePipelines() {
        if (pipelinesInitialized) {
            return;
        }
        // 确保在渲染线程执行
        RenderSystem.assertOnRenderThread();

        Identifier linesVert = Identifier.of("minecraft", "core/rendertype_lines");
        Identifier linesFrag = Identifier.of("minecraft", "core/rendertype_lines");
        Identifier positionColorVert = Identifier.of("minecraft", "core/position_color");
        Identifier positionColorFrag = Identifier.of("minecraft", "core/position_color");

        // --- Lines Pipeline ---
        SHAPE_LINES_PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.of("messmod", "shape_lines_pipeline"))
                .withVertexShader(linesVert)
                .withFragmentShader(linesFrag)
                .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.DEBUG_LINES)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false) // 线条通常不写入深度
                .withColorWrite(true, true)
                // 标准 Uniforms (会被 RenderSystem/RenderPass 自动填充)
                .withUniform("ModelViewMat", UniformType.MATRIX4X4)
                .withUniform("ProjMat", UniformType.MATRIX4X4)
                .withUniform("ColorModulator", UniformType.VEC4)
                .withUniform("FogStart", UniformType.FLOAT)
                .withUniform("FogEnd", UniformType.FLOAT)
                .withUniform("FogColor", UniformType.VEC4)
                .withUniform("FogShape", UniformType.INT)
                // 特定于 lines 的 Uniforms
                .withUniform("LineWidth", UniformType.FLOAT)
                .withUniform("ScreenSize", UniformType.VEC2)
                .build();

        // --- Faces Pipeline (Standard Blend) ---
        SHAPE_FACES_PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.of("messmod", "shape_faces_pipeline"))
                .withVertexShader(positionColorVert)
                .withFragmentShader(positionColorFrag)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withCull(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false) // 透明面通常不写入深度
                .withColorWrite(true, true)
                .withUniform("ModelViewMat", UniformType.MATRIX4X4)
                .withUniform("ProjMat", UniformType.MATRIX4X4)
                .withUniform("ColorModulator", UniformType.VEC4)
                // position_color shader 通常不处理 Fog
                .build();

        pipelinesInitialized = true;
    }

    public void render(MatrixStack matrices, Camera camera, float partialTick) { // matrices 是从 Mixin 传来的
        ClientWorld currentWorld = this.client.world;
        if (currentWorld == null) { return; }

        RegistryKey<World> dimensionType = currentWorld.getRegistryKey();
        Map<ShapeSpace, Set<Shape>> shapesInDim = this.shapes.getShapesInDimension(dimensionType);
        if (shapesInDim == null || shapesInDim.isEmpty()) { return; }

        Tessellator tessellator = Tessellator.getInstance();
        double cameraX = camera.getPos().x;
        double cameraY = camera.getPos().y;
        double cameraZ = camera.getPos().z;

        // 投影矩阵从 RenderSystem 全局获取，由 GameRenderer 设置
        Matrix4f projectionMatrixForShader = RenderSystem.getProjectionMatrix();

        Framebuffer mainFramebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (mainFramebuffer == null) return;

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        synchronized (shapes) {
            this.shapes.getAllShapes().values().forEach((map) -> {
                map.forEach((space, set) -> {
                    set.removeIf((entry) -> entry.isExpired(this.shapes.getTime()));
                });
            });

            // --- 渲染面 ---
            BufferBuilder facesBufferBuilder = tessellator.begin(SHAPE_FACES_PIPELINE.getVertexFormatMode(), SHAPE_FACES_PIPELINE.getVertexFormat());
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && !(s instanceof RenderedText)) {
                        // Shape.renderFacesToBuffer 现在不接收 matrix 参数
                        s.renderFacesToBuffer(facesBufferBuilder, cameraX, cameraY, cameraZ, partialTick);
                    }
                }
            }
            BuiltBuffer facesBuiltBuffer = facesBufferBuilder.endNullable();
            if (facesBuiltBuffer != null && facesBuiltBuffer.getDrawParameters().indexCount() > 0) {
                RenderPipeline currentFacePipeline = SHAPE_FACES_PIPELINE;
                try (GpuBuffer faceVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "messmod_shape_faces_vb", BufferType.VERTICES, BufferUsage.STREAM_WRITE, facesBuiltBuffer.getBuffer())) {
                    RenderSystem.ShapeIndexBuffer sequentialQuads = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
                    GpuBuffer faceIndexBuffer = sequentialQuads.getIndexBuffer(facesBuiltBuffer.getDrawParameters().indexCount());
                    try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                            .createRenderPass(mainFramebuffer.getColorAttachment(), OptionalInt.empty(), mainFramebuffer.getDepthAttachment(), OptionalDouble.empty())) {
                        renderPass.setPipeline(currentFacePipeline);
//                        renderPass.setUniform("ProjMat", projectionMatrixForShader);   // <--- 手动设置
                        renderPass.setVertexBuffer(0, faceVertexBuffer);
                        renderPass.setIndexBuffer(faceIndexBuffer, sequentialQuads.getIndexType());
                        renderPass.drawIndexed(0, facesBuiltBuffer.getDrawParameters().indexCount());
                    }
                }
                facesBuiltBuffer.close();
            }

            // --- 渲染线 ---
            BufferBuilder linesBufferBuilder = tessellator.begin(SHAPE_LINES_PIPELINE.getVertexFormatMode(), SHAPE_LINES_PIPELINE.getVertexFormat());
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && !(s instanceof RenderedText)) {
                        // Shape.renderLinesToBuffer 现在不接收 matrix 参数
                        s.renderLinesToBuffer(linesBufferBuilder, cameraX, cameraY, cameraZ, partialTick);
                    }
                }
            }
            BuiltBuffer linesBuiltBuffer = linesBufferBuilder.endNullable();
            if (linesBuiltBuffer != null && linesBuiltBuffer.getDrawParameters().vertexCount() > 0) {
                try (GpuBuffer lineVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "messmod_shape_lines_vb", BufferType.VERTICES, BufferUsage.STREAM_WRITE, linesBuiltBuffer.getBuffer())) {
                    try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                            .createRenderPass(mainFramebuffer.getColorAttachment(), OptionalInt.empty(), mainFramebuffer.getDepthAttachment(), OptionalDouble.empty())) {
                        renderPass.setPipeline(SHAPE_LINES_PIPELINE);
//                        renderPass.setUniform("ProjMat", projectionMatrixForShader);   // <--- 手动设置
                        renderPass.setUniform("LineWidth", currentLineWidth);
                        Window window = client.getWindow();
                        renderPass.setUniform("ScreenSize", (float) window.getFramebufferWidth(), (float) window.getFramebufferHeight());
                        renderPass.setVertexBuffer(0, lineVertexBuffer);
                        renderPass.draw(0, linesBuiltBuffer.getDrawParameters().vertexCount());
                    }
                }
                linesBuiltBuffer.close();
            }
        } // End synchronized(shapes)

        // --- 文本渲染 ---
        VertexConsumerProvider.Immediate immediateText = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        MatrixStack textMatricesGlobal = new MatrixStack();
        // 文本的 MatrixStack 从我们正确的视图矩阵开始

        synchronized(shapes) {
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && s instanceof RenderedText renderedTextInstance) {
                        MatrixStack individualTextMatrices = new MatrixStack();
                        individualTextMatrices.multiplyPositionMatrix(textMatricesGlobal.peek().getPositionMatrix());
                        renderedTextInstance.renderActualText(individualTextMatrices, immediateText, camera, partialTick);
                    }
                }
            }
        }
        immediateText.draw();
    }
    
    // some raw shit

    public static void buildLine(BufferBuilder builder,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b, float a,
                                 float normalX, float normalY, float normalZ) {
        builder.vertex(x1, y1, z1).color(r, g, b, a).normal(normalX, normalY, normalZ); // 第一个顶点结束
        builder.vertex(x2, y2, z2).color(r, g, b, a).normal(normalX, normalY, normalZ); // 第二个顶点结束
        // 当下一个 builder.vertex() 被调用，或者 builder.end() 被调用时，这些顶点会被最终确定
    }

    /**
     * Builds a wireframe box into the BufferBuilder.
     * Assumes BufferBuilder has been started with LINES mode and POSITION_COLOR_NORMAL format.
     */
    public static void buildBoxWireframe(BufferBuilder builder,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         boolean xthick, boolean ythick, boolean zthick,
                                         float r1, float g1, float b1, float a,
                                         float r2, float g2, float b2) { // r2,g2,b2 仍被忽略
        float nx = 0f, ny = 1f, nz = 0f; // Default normal

        if (xthick) {
            builder.vertex(x1, y1, z1).color(r1, g2, b2, a).normal(nx, ny, nz);
            builder.vertex(x2, y1, z1).color(r1, g2, b2, a).normal(nx, ny, nz);

            builder.vertex(x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x1, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x1, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }

        if (ythick) {
            builder.vertex(x1, y1, z1).color(r2, g1, b2, a).normal(nx, ny, nz);
            builder.vertex(x1, y2, z1).color(r2, g1, b2, a).normal(nx, ny, nz);

            builder.vertex(x2, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x1, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }

        if (zthick) {
            builder.vertex(x1, y1, z1).color(r2, g2, b1, a).normal(nx, ny, nz);
            builder.vertex(x1, y1, z2).color(r2, g2, b1, a).normal(nx, ny, nz);

            builder.vertex(x1, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }
    }

    /**
     * Builds a solid box (faces) into the BufferBuilder.
     * Assumes BufferBuilder has been started with QUADS mode and POSITION_COLOR format.
     */
    public static void buildBoxFaces(BufferBuilder builder,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     boolean xthick, boolean ythick, boolean zthick,
                                     float r, float g, float b, float a) {
        // For POSITION_COLOR format, we don't call .normal()
        if (xthick && ythick) { // Front Face
            builder.vertex(x1, y1, z1).color(r, g, b, a);
            builder.vertex(x1, y2, z1).color(r, g, b, a);
            builder.vertex(x2, y2, z1).color(r, g, b, a);
            builder.vertex(x2, y1, z1).color(r, g, b, a);
        }
        if (xthick && ythick) { // Back Face
            builder.vertex(x2, y1, z2).color(r, g, b, a);
            builder.vertex(x2, y2, z2).color(r, g, b, a);
            builder.vertex(x1, y2, z2).color(r, g, b, a);
            builder.vertex(x1, y1, z2).color(r, g, b, a);
        }
        if (xthick && zthick) { // Top Face
            builder.vertex(x1, y2, z2).color(r, g, b, a);
            builder.vertex(x2, y2, z2).color(r, g, b, a);
            builder.vertex(x2, y2, z1).color(r, g, b, a);
            builder.vertex(x1, y2, z1).color(r, g, b, a);
        }
        if (xthick && zthick) { // Bottom Face
            builder.vertex(x1, y1, z1).color(r, g, b, a);
            builder.vertex(x2, y1, z1).color(r, g, b, a);
            builder.vertex(x2, y1, z2).color(r, g, b, a);
            builder.vertex(x1, y1, z2).color(r, g, b, a);
        }
        if (ythick && zthick) { // Left Face
            builder.vertex(x1, y1, z2).color(r, g, b, a);
            builder.vertex(x1, y2, z2).color(r, g, b, a);
            builder.vertex(x1, y2, z1).color(r, g, b, a);
            builder.vertex(x1, y1, z1).color(r, g, b, a);
        }
        if (ythick && zthick) { // Right Face
            builder.vertex(x2, y1, z1).color(r, g, b, a);
            builder.vertex(x2, y2, z1).color(r, g, b, a);
            builder.vertex(x2, y2, z2).color(r, g, b, a);
            builder.vertex(x2, y1, z2).color(r, g, b, a);
        }
    }
}
