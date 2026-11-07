package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.mixin.client.RenderSystemAccessor;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class UnshadedVertexConsumer implements VertexConsumer {
   private static final float FALLBACK_NORMAL_X = 0.0F;
   private static final float FALLBACK_NORMAL_Y = 1.0F;
   private static final float FALLBACK_NORMAL_Z = 0.0F;
   private final VertexConsumer delegate;
   private final float normalX;
   private final float normalY;
   private final float normalZ;

   private UnshadedVertexConsumer(VertexConsumer delegate) {
      this.delegate = delegate;
      Vector3f[] lightDirections = RenderSystemAccessor.getShaderLightDirections();
      Vector3f primaryLight = lightDirections.length > 0 ? lightDirections[0] : null;
      if (primaryLight == null) {
         this.normalX = 0.0F;
         this.normalY = 1.0F;
         this.normalZ = 0.0F;
      } else {
         float maxComponent = Math.max(Math.abs(primaryLight.x()), Math.max(Math.abs(primaryLight.y()), Math.abs(primaryLight.z())));
         if (maxComponent < 1.0E-6F) {
            this.normalX = 0.0F;
            this.normalY = 1.0F;
            this.normalZ = 0.0F;
         } else {
            this.normalX = primaryLight.x() / maxComponent;
            this.normalY = primaryLight.y() / maxComponent;
            this.normalZ = primaryLight.z() / maxComponent;
         }
      }
   }

   private UnshadedVertexConsumer(VertexConsumer delegate, float normalX, float normalY, float normalZ) {
      this.delegate = delegate;
      this.normalX = normalX;
      this.normalY = normalY;
      this.normalZ = normalZ;
   }

   public static VertexConsumer wrap(VertexConsumer consumer) {
      return consumer instanceof UnshadedVertexConsumer ? consumer : new UnshadedVertexConsumer(consumer);
   }

   public static VertexConsumer wrapFixedNormal(VertexConsumer consumer) {
      return new UnshadedVertexConsumer(consumer, 0.0F, 1.0F, 0.0F);
   }

   public VertexConsumer getDelegate() {
      return this.delegate;
   }

   public Matrix3f createNormalMatrix() {
      return new Matrix3f().m10(this.normalX).m11(this.normalY).m12(this.normalZ);
   }

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
      this.delegate.addVertex(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      this.delegate.setColor(red, green, blue, alpha);
      return this;
   }

   @Override
   public VertexConsumer setColor(int color) {
      this.delegate.setColor(color);
      return this;
   }

   @Override
   public VertexConsumer setUv(float u, float v) {
      this.delegate.setUv(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv1(int u, int v) {
      this.delegate.setUv1(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv2(int u, int v) {
      this.delegate.setUv2(u, v);
      return this;
   }

   @Override
   public VertexConsumer setNormal(float x, float y, float z) {
      this.delegate.setNormal(this.normalX, this.normalY, this.normalZ);
      return this;
   }

   @Override
   public VertexConsumer setLineWidth(float width) {
      this.delegate.setLineWidth(width);
      return this;
   }
}
