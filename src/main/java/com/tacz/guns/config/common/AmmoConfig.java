package com.tacz.guns.config.common;

import com.google.common.collect.Lists;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class AmmoConfig {
    public static final List<String> DEFAULT_DESTROY_BLOCKS = List.of("#forge:glass", "#forge:glass_panes");
    public static final List<String> DEFAULT_BULLET_DAMAGE_IGNORE_LIST = List.of("minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame");
    public static ForgeConfigSpec.BooleanValue EXPLOSIVE_AMMO_DESTROYS_BLOCK;
    public static ForgeConfigSpec.BooleanValue EXPLOSIVE_AMMO_FIRE;
    public static ForgeConfigSpec.BooleanValue EXPLOSIVE_AMMO_KNOCK_BACK;
    public static ForgeConfigSpec.IntValue EXPLOSIVE_AMMO_VISIBLE_DISTANCE;
    public static ForgeConfigSpec.ConfigValue<List<String>> PASS_THROUGH_BLOCKS;
    public static ForgeConfigSpec.BooleanValue DESTROY_GLASS;
    public static ForgeConfigSpec.ConfigValue<List<String>> DESTROY_BLOCKS;
    public static ForgeConfigSpec.DoubleValue DESTROY_BLOCK_HARDNESS_THRESHOLD;
    public static ForgeConfigSpec.BooleanValue IGNITE_BLOCK;
    public static ForgeConfigSpec.BooleanValue IGNITE_ENTITY;
    public static ForgeConfigSpec.BooleanValue IGNORE_TEAM_FRIENDLY_FIRE;
    public static ForgeConfigSpec.BooleanValue ENABLE_BULLET_DAMAGE_IGNORE_LIST;
    public static ForgeConfigSpec.ConfigValue<List<String>> BULLET_DAMAGE_IGNORE_LIST;
    public static ForgeConfigSpec.DoubleValue GLOBAL_BULLET_SPEED_MODIFIER;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("ammo");

        builder.comment("Warning: Ammo with explosive properties can break blocks");
        EXPLOSIVE_AMMO_DESTROYS_BLOCK = builder.define("ExplosiveAmmoDestroysBlock", true);

        builder.comment("Warning: Ammo with explosive properties can set the surroundings on fire");
        EXPLOSIVE_AMMO_FIRE = builder.define("ExplosiveAmmoFire", false);

        builder.comment("Ammo with explosive properties can add knockback effect");
        EXPLOSIVE_AMMO_KNOCK_BACK = builder.define("ExplosiveAmmoKnockBack", true);

        builder.comment("The distance at which the explosion effect can be seen");
        EXPLOSIVE_AMMO_VISIBLE_DISTANCE = builder.defineInRange("ExplosiveAmmoVisibleDistance", 192, 0, Integer.MAX_VALUE);

        builder.comment("Blocks that bullets can pass through. Accepts block IDs and tags prefixed with #, e.g. minecraft:glass or #forge:glass.");
        PASS_THROUGH_BLOCKS = defineStringListAllowEmpty(builder, "PassThroughBlocks", Lists.newArrayList());

        builder.comment(
                "Allow bullets to destroy blocks listed in DestroyBlocks without drops. The name is retained for compatibility; the list is not limited to glass."
        );
        DESTROY_GLASS = builder.define("DestroyGlass", true);

        builder.comment(
                "Blocks that bullets can destroy when DestroyGlass is enabled, subject to DestroyBlockHardnessThreshold. Accepts block IDs and tags prefixed with #, e.g. minecraft:glass or #forge:glass."
        );
        DESTROY_BLOCKS = defineStringListAllowEmpty(builder, "DestroyBlocks", Lists.newArrayList(DEFAULT_DESTROY_BLOCKS));

        builder.comment(
                "Maximum block hardness for direct bullet destruction. Blocks above this threshold or with negative hardness (unbreakable) are never destroyed, even if listed in DestroyBlocks. Default: 1.0; vanilla glass has hardness 0.3."
        );
        DESTROY_BLOCK_HARDNESS_THRESHOLD = builder.defineInRange("DestroyBlockHardnessThreshold", 1.0, 0.0, Double.MAX_VALUE);

        builder.comment("Whether a ammo can ignite the block");
        IGNITE_BLOCK = builder.define("IgniteBlock", true);

        builder.comment("Whether a ammo can ignite the entity");
        IGNITE_ENTITY = builder.define("IgniteEntity", true);

        builder.comment("Bullets pass through members of the shooter's scoreboard team when friendly fire is disabled, without consuming pierce count");
        IGNORE_TEAM_FRIENDLY_FIRE = builder.define("IgnoreTeamFriendlyFire", false);

        builder.comment(
                "Enable the entity bullet damage ignore list. Listed entities are ignored by bullet hit detection and take no damage or knockback from ammo explosions."
        );
        ENABLE_BULLET_DAMAGE_IGNORE_LIST = builder.define("EnableBulletDamageIgnoreList", false);

        builder.comment("Entity type IDs protected from bullets and ammo explosions when EnableBulletDamageIgnoreList is enabled.");
        BULLET_DAMAGE_IGNORE_LIST = defineStringListAllowEmpty(builder, "BulletDamageIgnoreList", Lists.newArrayList(DEFAULT_BULLET_DAMAGE_IGNORE_LIST),
                value -> value instanceof String id && Identifier.tryParse(id) != null);

        builder.comment("Global bullet speed modifier, the init speed of the bullet will be multiplied by this value, default is 2.0");
        builder.comment("This is to compensate the side effects introduced while fixing the shooter variable input issue");
        GLOBAL_BULLET_SPEED_MODIFIER = builder.defineInRange("GlobalBulletSpeedModifier", 2.0, 0.01, 20);

        builder.pop();
    }

    /**
     * 1025: defineListAllowEmpty 返回 ConfigValue&lt;List&lt;? extends String&gt;&gt;，
     * 这里收窄回 List&lt;String&gt; 以保持既有消费方（BlockRayTrace/AmmoClothConfig）编译不变。
     */
    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<List<String>> defineStringListAllowEmpty(ForgeConfigSpec.Builder builder, String name, List<String> defaultValue) {
        return (ForgeConfigSpec.ConfigValue<List<String>>) (ForgeConfigSpec.ConfigValue<?>) builder.defineListAllowEmpty(name, defaultValue, value -> value instanceof String);
    }

    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<List<String>> defineStringListAllowEmpty(ForgeConfigSpec.Builder builder, String name, List<String> defaultValue, java.util.function.Predicate<Object> validator) {
        return (ForgeConfigSpec.ConfigValue<List<String>>) (ForgeConfigSpec.ConfigValue<?>) builder.defineListAllowEmpty(name, defaultValue, validator);
    }
}
