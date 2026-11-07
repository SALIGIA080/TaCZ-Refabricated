package com.tacz.guns.item;

import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * 弹药库方块物品。26.2 移植说明：
 * <ul>
 *   <li>构造器改收 {@link Properties}（注册时必须带 setId）；</li>
 *   <li>appendHoverText 换用 26.2 的 Consumer 回调签名；</li>
 *   <li>NBT 读取适配 26.2 的 Optional 风格 API；</li>
 *   <li>ItemStack 解析改用 OPTIONAL_CODEC + NbtOps。</li>
 * </ul>
 */
public class AmmoStorageBlockItem extends BlockItem {
   private static final int MAX_TOOLTIP_ITEMS = 5;
   private static final String ENTRIES_TAG = "Entries";
   private static final String STACK_TAG = "Stack";
   private static final String COUNT_TAG = "Count";

   public AmmoStorageBlockItem(Block block, Properties properties) {
      super(block, properties.stacksTo(1));
   }

   @Override
   public boolean canFitInsideContainerItems() {
      return false;
   }

   @Override
   public Component getName(ItemStack stack) {
      Component name = super.getName(stack);
      if (this.getBlock() == ModBlocks.AMMO_STORAGE_GOLD) {
         return name.copy().withStyle(ChatFormatting.YELLOW);
      } else {
         return this.getBlock() == ModBlocks.AMMO_STORAGE_DIAMOND ? name.copy().withStyle(ChatFormatting.AQUA) : name;
      }
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> components, TooltipFlag flag) {
      super.appendHoverText(stack, context, display, components, flag);
      components.accept(Component.translatable("tooltip.tacz.ammo_storage.description").withStyle(ChatFormatting.GRAY));
      components.accept(Component.translatable("tooltip.tacz.ammo_storage.description.extra").withStyle(ChatFormatting.GRAY));
   }

   @Override
   public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
      // 26.2：方块实体数据存放在 DataComponents.BLOCK_ENTITY_DATA（TypedEntityData）
      net.minecraft.world.item.component.TypedEntityData<?> data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
      if (data == null || !data.contains(ENTRIES_TAG)) {
         return Optional.empty();
      }
      CompoundTag blockEntityTag = data.copyTagWithoutId();
      ListTag entries = blockEntityTag.getListOrEmpty(ENTRIES_TAG);
      List<AmmoStorageTooltip.Entry> mergedEntries = new ArrayList<>();

      for (int i = 0; i < entries.size(); i++) {
         CompoundTag entryTag = entries.getCompoundOrEmpty(i);
         if (entryTag.contains(STACK_TAG)) {
            ItemStack entryStack = ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, entryTag.getCompoundOrEmpty(STACK_TAG))
               .result().orElse(ItemStack.EMPTY);
            long count = entryTag.getLongOr(COUNT_TAG, 0);
            if (!entryStack.isEmpty() && count > 0) {
               mergeTooltipEntry(mergedEntries, entryStack, count);
            }
         }
      }

      if (mergedEntries.isEmpty()) {
         return Optional.empty();
      } else {
         int displayed = Math.min(MAX_TOOLTIP_ITEMS, mergedEntries.size());
         List<AmmoStorageTooltip.Entry> tooltipEntries = new ArrayList<>(mergedEntries.subList(0, displayed));
         return Optional.of(new AmmoStorageTooltip(tooltipEntries, mergedEntries.size() - displayed));
      }
   }

   private static void mergeTooltipEntry(List<AmmoStorageTooltip.Entry> entries, ItemStack stack, long count) {
      for (int i = 0; i < entries.size(); i++) {
         AmmoStorageTooltip.Entry entry = entries.get(i);
         if (ItemStack.isSameItemSameComponents(entry.ammo(), stack)) {
            long mergedCount = saturatedAdd(entry.count(), count);
            entries.set(i, new AmmoStorageTooltip.Entry(entry.ammo(), mergedCount));
            return;
         }
      }

      ItemStack sample = stack.copy();
      sample.setCount(1);
      entries.add(new AmmoStorageTooltip.Entry(sample, count));
   }

   private static long saturatedAdd(long left, long right) {
      return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
   }
}
