package com.tacz.guns.api.client.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import java.util.Arrays;
import java.util.List;

public class ObjectAnimationParticleChannel {
   public double[] keyframeTimeS;
   public ParticleEffectData[] keyframeData;

   public ObjectAnimationParticleChannel() {
   }

   public ObjectAnimationParticleChannel(ObjectAnimationParticleChannel source) {
      this.keyframeTimeS = source.keyframeTimeS;
      this.keyframeData = source.keyframeData;
   }

   public void collectParticles(double fromTimeS, double toTimeS, List<ParticleEffectData> sink) {
      if (this.keyframeTimeS != null && this.keyframeTimeS.length != 0 && fromTimeS != toTimeS) {
         if (fromTimeS > toTimeS && fromTimeS <= this.getEndTimeS()) {
            this.collect(0.0, toTimeS, sink);
            toTimeS = this.getEndTimeS();
         }

         int from = this.computeIndex(fromTimeS, true);
         int to = this.computeIndex(toTimeS, false);

         for (int i = from + 1; i <= to; i++) {
            sink.add(this.keyframeData[i]);
         }
      }
   }

   private void collect(double fromTimeS, double toTimeS, List<ParticleEffectData> sink) {
      int from = this.computeIndex(fromTimeS, true);
      int to = this.computeIndex(toTimeS, false);

      for (int i = from + 1; i <= to; i++) {
         sink.add(this.keyframeData[i]);
      }
   }

   public double getEndTimeS() {
      return this.keyframeTimeS[this.keyframeTimeS.length - 1];
   }

   private int computeIndex(double timeS, boolean open) {
      int index = Arrays.binarySearch(this.keyframeTimeS, timeS);
      if (index >= 0) {
         return open ? index - 1 : index;
      } else {
         return -index - 2;
      }
   }
}