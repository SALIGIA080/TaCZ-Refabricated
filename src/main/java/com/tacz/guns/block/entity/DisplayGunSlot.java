package com.tacz.guns.block.entity;

public final class DisplayGunSlot {
   private final GunDisplayBlockEntity owner;
   private final int index;
   private long revision;
   private long appearanceRevision;
   private long notifiedRevision = Long.MIN_VALUE;

   public DisplayGunSlot(GunDisplayBlockEntity owner, int index) {
      this.owner = owner;
      this.index = index;
   }

   public GunDisplayBlockEntity owner() {
      return this.owner;
   }

   public int index() {
      return this.index;
   }

   public long appearanceRevision() {
      return this.appearanceRevision;
   }

   public void changed() {
      this.revision++;
      this.appearanceRevision++;
   }

   public void transformed() {
      this.revision++;
   }

   public boolean consumeChange() {
      if (this.notifiedRevision == this.revision) {
         return false;
      } else {
         this.notifiedRevision = this.revision;
         return true;
      }
   }
}
