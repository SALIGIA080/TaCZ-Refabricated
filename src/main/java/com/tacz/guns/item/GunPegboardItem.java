package com.tacz.guns.item;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class GunPegboardItem extends BlockItem {
   public GunPegboardItem(Block block, Properties properties) {
      super(block, properties);
   }

   @Override
   public InteractionResult place(BlockPlaceContext context) {
      ItemStack stack = context.getItemInHand();
      int color = stack.has(DataComponents.DYED_COLOR) ? DyedItemColor.getOrDefault(stack, -1) : -1;
      boolean inheritedColor = false;
      BlockPos placedPos = context.getClickedPos();
      BlockPos targetPos = placedPos.relative(context.getClickedFace().getOpposite());
      boolean linking = !context.replacingClickedOnBlock()
         && context.getPlayer() != null
         && !context.getPlayer().isShiftKeyDown()
         && context.getLevel().getBlockState(targetPos).getBlock() instanceof GunPegboardBlock;
      if (color < 0 && linking && context.getLevel().getBlockEntity(targetPos) instanceof GunPegboardBlockEntity target) {
         color = target.getColor();
         inheritedColor = color >= 0;
      }

      InteractionResult result = super.place(context);
      if (result.consumesAction()
         && context.getLevel().getBlockEntity(placedPos) instanceof GunPegboardBlockEntity pegboard
         && pegboard.getBlockState().is(this.getBlock())) {
         pegboard.setColor(color, inheritedColor);
      }

      return result;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag flag) {
      super.appendHoverText(stack, context, display, adder, flag);
      adder.accept(Component.translatable("tooltip.tacz.gun_pegboard.dye").withStyle(ChatFormatting.GRAY));
      adder.accept(Component.translatable("tooltip.tacz.gun_pegboard.link_color").withStyle(ChatFormatting.GRAY));
   }
}
