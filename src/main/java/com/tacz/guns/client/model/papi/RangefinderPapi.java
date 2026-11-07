package com.tacz.guns.client.model.papi;

import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

public class RangefinderPapi implements Function<ItemStack, String> {
   public static final String NAME = "rangefinder";

   public String apply(ItemStack stack) {
      return RangefinderManager.getText(stack);
   }
}
