package com.tacz.guns.client.resource.pojo.animation.bedrock;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

public class ParticleEffectKeyframes {
   private final Double2ObjectRBTreeMap<ParticleEffectData> keyframes;

   public ParticleEffectKeyframes(Double2ObjectRBTreeMap<ParticleEffectData> keyframes) {
      this.keyframes = keyframes;
   }

   public Double2ObjectRBTreeMap<ParticleEffectData> getKeyframes() {
      return this.keyframes;
   }
}