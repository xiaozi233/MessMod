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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
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
import org.joml.Matrix4fStack;

/**
 * A modified version of carpet.script.util.ShapesRenderer
 * Original Author : gnembon
 */
public class ShapeRenderer {
    //private final Map<RegistryKey<World>, Map<ShapeSpace, Set<Shape>>> shapes;
	private final ShapeCache shapes;
	private MinecraftClient client;
    private static RenderPipeline SHAPE_LINES_PIPELINE;
    private static RenderPipeline SHAPE_FACES_PIPELINE;
    private static RenderPipeline SHAPE_FACES_OVERLAY_PIPELINE; // 可选
	
    public ShapeRenderer(MinecraftClient mc) {
        this.shapes = ShapeCache.create(mc);
		this.client = mc;
        initializePipelines();
    }
    
    public void close() {
    	this.shapes.close();
    }
    
    public ShapeCache getShapeCache() {
		return this.shapes;
	}

    private static synchronized void initializePipelines() {
        Identifier posColorNormalVert = Identifier.of("minecraft", "core/position_color_normal");
        Identifier posColorVert = Identifier.of("minecraft", "core/position_color");
        Identifier posColorFrag = Identifier.of("minecraft", "core/position_color");

        if (SHAPE_LINES_PIPELINE == null) {
            SHAPE_LINES_PIPELINE = RenderPipeline.builder()
                    .withLocation(Identifier.of("messmod", "shape_lines_pipeline"))
                    .withVertexShader(posColorNormalVert)
                    .withFragmentShader(posColorFrag)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withColorWrite(true, true)
                    .build();
        }

        if (SHAPE_FACES_PIPELINE == null) {
            SHAPE_FACES_PIPELINE = RenderPipeline.builder()
                    .withLocation(Identifier.of("messmod", "shape_faces_pipeline"))
                    .withVertexShader(posColorVert)
                    .withFragmentShader(posColorFrag)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withColorWrite(true, true)
                    .build();
        }

        if (SHAPE_FACES_OVERLAY_PIPELINE == null) {
            SHAPE_FACES_OVERLAY_PIPELINE = RenderPipeline.builder()
                    .withLocation(Identifier.of("messmod", "shape_faces_overlay_pipeline"))
                    .withVertexShader(posColorVert)
                    .withFragmentShader(posColorFrag)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .withBlend(BlendFunction.OVERLAY)
                    .withDepthWrite(false)
                    .withColorWrite(true, true)
                    .build();
        }
    }


    public void render(MatrixStack matrices, Camera camera, float partialTick) {
        ClientWorld currentWorld = this.client.world;
        if (currentWorld == null) { return; }

        RegistryKey<World> dimensionType = currentWorld.getRegistryKey();
        Map<ShapeSpace, Set<Shape>> shapesInDim = this.shapes.getShapesInDimension(dimensionType);
        if (shapesInDim == null || shapesInDim.isEmpty()) { return; }

        Tessellator tessellator = Tessellator.getInstance();
        double cameraX = camera.getPos().x;
        double cameraY = camera.getPos().y;
        double cameraZ = camera.getPos().z;
        Matrix4f poseMatrix = matrices.peek().getPositionMatrix();

        Framebuffer mainFramebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (mainFramebuffer == null) return;

        synchronized (shapes) {
            this.shapes.getAllShapes().values().forEach((map) -> {
                map.forEach((space, set) -> {
                    set.removeIf((entry) -> entry.isExpired(this.shapes.getTime()));
                });
            });

            // --- Render Faces ---
            BufferBuilder facesBufferBuilder = tessellator.begin(SHAPE_FACES_PIPELINE.getVertexFormatMode(), SHAPE_FACES_PIPELINE.getVertexFormat());
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && !(s instanceof RenderedText)) {
                        s.renderFacesToBuffer(poseMatrix, facesBufferBuilder, cameraX, cameraY, cameraZ, partialTick);
                    }
                }
            }
            BuiltBuffer facesBuiltBuffer = facesBufferBuilder.endNullable();
            if (facesBuiltBuffer != null && facesBuiltBuffer.getDrawParameters().indexCount() > 0) {
                try (GpuBuffer faceVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "messmod_shape_faces_vb", BufferType.VERTICES, BufferUsage.STREAM_WRITE, facesBuiltBuffer.getBuffer())) {
                    RenderSystem.ShapeIndexBuffer sequentialQuads = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
                    GpuBuffer faceIndexBuffer = sequentialQuads.getIndexBuffer(facesBuiltBuffer.getDrawParameters().indexCount());
                    try (com.mojang.blaze3d.systems.RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                            .createRenderPass(mainFramebuffer.getColorAttachment(), OptionalInt.empty(), mainFramebuffer.getDepthAttachment(), OptionalDouble.empty())) {
                        renderPass.setPipeline(SHAPE_FACES_PIPELINE);
                        renderPass.setVertexBuffer(0, faceVertexBuffer);
                        renderPass.setIndexBuffer(faceIndexBuffer, sequentialQuads.getIndexType());
                        renderPass.drawIndexed(0, facesBuiltBuffer.getDrawParameters().indexCount());
                    }
                }
                facesBuiltBuffer.close();
            }

            // --- Render Lines ---
            BufferBuilder linesBufferBuilder = tessellator.begin(SHAPE_LINES_PIPELINE.getVertexFormatMode(), SHAPE_LINES_PIPELINE.getVertexFormat());
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && !(s instanceof RenderedText)) { // Exclude RenderedText from general line drawing
                        s.renderLinesToBuffer(poseMatrix, linesBufferBuilder, cameraX, cameraY, cameraZ, partialTick);
                    }
                }
            }
            BuiltBuffer linesBuiltBuffer = linesBufferBuilder.endNullable();
            if (linesBuiltBuffer != null && linesBuiltBuffer.getDrawParameters().vertexCount() > 0) {
                try (GpuBuffer lineVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "messmod_shape_lines_vb", BufferType.VERTICES, BufferUsage.STREAM_WRITE, linesBuiltBuffer.getBuffer())) {
                    try (com.mojang.blaze3d.systems.RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                            .createRenderPass(mainFramebuffer.getColorAttachment(), OptionalInt.empty(), mainFramebuffer.getDepthAttachment(), OptionalDouble.empty())) {
                        renderPass.setPipeline(SHAPE_LINES_PIPELINE);
                        renderPass.setVertexBuffer(0, lineVertexBuffer);
                        renderPass.draw(0, linesBuiltBuffer.getDrawParameters().vertexCount());
                    }
                }
                linesBuiltBuffer.close();
            }
        } // End synchronized(shapes)

        // --- Render Text (separately, after geometry, using its own system) ---
        // Text rendering should ideally happen after all GpuBuffer-based geometry
        // to ensure VCP flushes correctly and states don't interfere too much.
        VertexConsumerProvider.Immediate immediateText = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        // We need a fresh MatrixStack for text, inheriting the view transform
        MatrixStack textMatricesGlobal = new MatrixStack();
        textMatricesGlobal.multiplyPositionMatrix(matrices.peek().getPositionMatrix()); // Inherit view from overall matrices

        synchronized(shapes) { // Synchronize again if shapesInDim could change, or use a copy
            for (Map.Entry<ShapeSpace, Set<Shape>> dimEntry : shapesInDim.entrySet()) {
                for (Shape s : dimEntry.getValue()) {
                    if (s.shouldRender(dimensionType) && s instanceof RenderedText renderedTextInstance) {
                        // Pass the global text matrix stack, camera, and VCP
                        renderedTextInstance.renderActualText(textMatricesGlobal, immediateText, camera, partialTick);
                    }
                }
            }
        }
        immediateText.draw(); // Draw all batched text
    }
    
    // some raw shit

    public static void buildLine(Matrix4f modelViewMatrix, BufferBuilder builder,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b, float a,
                                 float normalX, float normalY, float normalZ) {
        builder.vertex(modelViewMatrix, x1, y1, z1).color(r, g, b, a).normal(normalX, normalY, normalZ); // 第一个顶点结束
        builder.vertex(modelViewMatrix, x2, y2, z2).color(r, g, b, a).normal(normalX, normalY, normalZ); // 第二个顶点结束
        // 当下一个 builder.vertex() 被调用，或者 builder.end() 被调用时，这些顶点会被最终确定
    }

    /**
     * Builds a wireframe box into the BufferBuilder.
     * Assumes BufferBuilder has been started with LINES mode and POSITION_COLOR_NORMAL format.
     */
    public static void buildBoxWireframe(Matrix4f modelViewMatrix, BufferBuilder builder,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         boolean xthick, boolean ythick, boolean zthick,
                                         float r1, float g1, float b1, float a,
                                         float r2, float g2, float b2) { // r2,g2,b2 仍被忽略
        float nx = 0f, ny = 1f, nz = 0f; // Default normal

        if (xthick) {
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x1, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x1, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }
        if (ythick) {
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x1, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x2, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x1, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }
        if (zthick) {
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x1, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x2, y1, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y1, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x1, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x1, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);

            builder.vertex(modelViewMatrix, x2, y2, z1).color(r1, g1, b1, a).normal(nx, ny, nz);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r1, g1, b1, a).normal(nx, ny, nz);
        }
    }

    /**
     * Builds a solid box (faces) into the BufferBuilder.
     * Assumes BufferBuilder has been started with QUADS mode and POSITION_COLOR format.
     */
    public static void buildBoxFaces(Matrix4f modelViewMatrix, BufferBuilder builder,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     boolean xthick, boolean ythick, boolean zthick,
                                     float r, float g, float b, float a) {
        // For POSITION_COLOR format, we don't call .normal()
        if (xthick && ythick) { // Front Face
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y2, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y1, z1).color(r, g, b, a);
        }
        if (xthick && ythick) { // Back Face
            builder.vertex(modelViewMatrix, x2, y1, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y1, z2).color(r, g, b, a);
        }
        if (xthick && zthick) { // Top Face
            builder.vertex(modelViewMatrix, x1, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y2, z1).color(r, g, b, a);
        }
        if (xthick && zthick) { // Bottom Face
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y1, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y1, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y1, z2).color(r, g, b, a);
        }
        if (ythick && zthick) { // Left Face
            builder.vertex(modelViewMatrix, x1, y1, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y2, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x1, y1, z1).color(r, g, b, a);
        }
        if (ythick && zthick) { // Right Face
            builder.vertex(modelViewMatrix, x2, y1, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z1).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y2, z2).color(r, g, b, a);
            builder.vertex(modelViewMatrix, x2, y1, z2).color(r, g, b, a);
        }
    }
}
