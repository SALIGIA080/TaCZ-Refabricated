package com.tacz.guns.compat.jei.entry;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class GunAttachmentQueryEntry {
   public static final int ATTACHMENTS_PER_PAGE = 54;
   private final ItemStack gunStack;
   private final AttachmentType attachmentType;
   private final List<ItemStack> attachmentStacks;
   private final int totalAttachmentCount;
   private final int pageNumber;
   private final int pageCount;

   private GunAttachmentQueryEntry(
      ItemStack gunStack, AttachmentType attachmentType, List<ItemStack> attachmentStacks, int totalAttachmentCount, int pageNumber, int pageCount
   ) {
      this.gunStack = gunStack;
      this.attachmentType = attachmentType;
      this.attachmentStacks = List.copyOf(attachmentStacks);
      this.totalAttachmentCount = totalAttachmentCount;
      this.pageNumber = pageNumber;
      this.pageCount = pageCount;
   }

   public static List<GunAttachmentQueryEntry> getAllGunAttachmentQueryEntries() {
      Map<AttachmentType, List<ItemStack>> attachmentsByType = new EnumMap<>(AttachmentType.class);
      TimelessAPI.getAllCommonAttachmentIndex()
         .stream()
         .filter(entry -> !entry.getValue().getPojo().isHidden())
         .filter(entry -> entry.getValue().getType() != AttachmentType.NONE)
         .sorted(
            Comparator.<Entry<Identifier, CommonAttachmentIndex>>comparingInt(entry -> entry.getValue().getSort())
               .thenComparing(entry -> entry.getKey().toString())
         )
         .forEach(
            entry -> attachmentsByType.computeIfAbsent(entry.getValue().getType(), type -> new ArrayList<>())
               .add(AttachmentItemBuilder.create().setId(entry.getKey()).build())
         );
      List<GunAttachmentQueryEntry> entries = new ArrayList<>();
      TimelessAPI.getAllCommonGunIndex()
         .stream()
         .sorted(
            Comparator.<Entry<Identifier, CommonGunIndex>>comparingInt(entry -> entry.getValue().getSort())
               .thenComparing(entry -> entry.getKey().toString())
         )
         .forEach(entry -> {
            ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).build();
            if (gun.getItem() instanceof IGun iGun) {
               for (AttachmentType type : AttachmentType.values()) {
                  List<ItemStack> candidates = attachmentsByType.get(type);
                  if (candidates != null && iGun.allowAttachmentType(gun, type)) {
                     List<ItemStack> compatible = candidates.stream().filter(attachment -> iGun.allowAttachment(gun, attachment)).toList();
                     int total = compatible.size();
                     int pageCount = (total + 54 - 1) / 54;

                     for (int page = 0; page < pageCount; page++) {
                        int start = page * 54;
                        int end = Math.min(start + 54, total);
                        entries.add(new GunAttachmentQueryEntry(gun, type, compatible.subList(start, end), total, page + 1, pageCount));
                     }
                  }
               }
            }
         });
      return entries;
   }

   public ItemStack getGunStack() {
      return this.gunStack;
   }

   public AttachmentType getAttachmentType() {
      return this.attachmentType;
   }

   public List<ItemStack> getAttachmentStacks() {
      return this.attachmentStacks;
   }

   public int getTotalAttachmentCount() {
      return this.totalAttachmentCount;
   }

   public int getPageNumber() {
      return this.pageNumber;
   }

   public int getPageCount() {
      return this.pageCount;
   }
}
