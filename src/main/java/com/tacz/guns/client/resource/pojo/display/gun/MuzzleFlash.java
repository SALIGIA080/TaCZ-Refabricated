package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;

public class MuzzleFlash {
    public static final Identifier DEFAULT_FIRST_PERSON_FLASH = Identifier.fromNamespaceAndPath("tacz", "muzzle_flash");
    public static final Identifier DEFAULT_FIRST_PERSON_SMOKE = Identifier.fromNamespaceAndPath("tacz", "muzzle_flash_smoke");
    public static final Identifier DEFAULT_THIRD_PERSON_FLASH = Identifier.fromNamespaceAndPath("tacz", "muzzle_flash");
    public static final Identifier DEFAULT_THIRD_PERSON_SMOKE = Identifier.fromNamespaceAndPath("tacz", "muzzle_flash_smoke");

    @SerializedName("type")
    private Type type = Type.DEFAULT;

    @SerializedName("texture")
    protected Identifier texture = null;

    @SerializedName("scale")
    private float scale = 1;

    @SerializedName("first_person_flash")
    private Identifier firstPersonFlash = DEFAULT_FIRST_PERSON_FLASH;

    @SerializedName("first_person_smoke")
    private Identifier firstPersonSmoke = DEFAULT_FIRST_PERSON_SMOKE;

    @SerializedName("third_person_flash")
    private Identifier thirdPersonFlash = DEFAULT_THIRD_PERSON_FLASH;

    @SerializedName("third_person_smoke")
    private Identifier thirdPersonSmoke = DEFAULT_THIRD_PERSON_SMOKE;

    @SerializedName("initial_speed_scale")
    private float initialSpeedScale = 1.0F;

    @SerializedName("smoke_particle_count")
    private float smokeParticleCount = 0.0F;

    @SerializedName("smoke_scale")
    private float smokeScale = 0.0F;

    @SerializedName("flash_light_level")
    private int flashLightLevel = 15;

    @SerializedName("smoke_direction")
    @Nullable
    private com.google.gson.JsonElement smokeDirection = null;

    @SerializedName("smoke_directions")
    @Nullable
    private com.google.gson.JsonElement smokeDirections = null;

    @SerializedName("smoke_spread_angle")
    @Nullable
    private Float smokeSpreadAngle = null;

    @SerializedName("smoke_spread")
    @Nullable
    private Float smokeSpread = null;

    /** 1025 delta：枪口火光的环境亮度（0~15）。 */
    public int getFlashLightLevel() {
        return Math.max(0, Math.min(15, this.flashLightLevel));
    }

    /** 1025 delta：烟雾方向表（JSON 元素，交给 SmokeDirectionData 解析）。 */
    @Nullable
    public float[][] getSmokeDirections() {
        return com.tacz.guns.resource.pojo.data.SmokeDirectionData.parseDirections(this.smokeDirection);
    }

    public float getSmokeSpread() {
        return com.tacz.guns.resource.pojo.data.SmokeDirectionData.parseSpread(this.smokeSpread);
    }

    @Nullable
    public com.tacz.guns.resource.pojo.data.SmokeDirectionData.Config getSmokeDirectionConfig() {
        return com.tacz.guns.resource.pojo.data.SmokeDirectionData.parse(this.smokeDirections, this.smokeSpreadAngle, this.smokeDirection, this.smokeSpread);
    }

    public Type getType() {
        return this.type == null ? Type.DEFAULT : this.type;
    }

    public boolean isRenew() {
        return this.getType() == Type.RENEW;
    }

    public Identifier getTexture() {
        return texture;
    }

    public float getScale() {
        return scale;
    }

    @Nullable
    public Identifier getFirstPersonFlashId() {
        return this.firstPersonFlash;
    }

    @Nullable
    public Identifier getFirstPersonSmokeId() {
        return this.firstPersonSmoke;
    }

    @Nullable
    public Identifier getThirdPersonFlashId() {
        return this.thirdPersonFlash;
    }

    @Nullable
    public Identifier getThirdPersonSmokeId() {
        return this.thirdPersonSmoke;
    }

    public float getInitialSpeedScale() {
        return Float.isFinite(this.initialSpeedScale) ? this.initialSpeedScale : 1.0F;
    }

    public float getSmokeParticleCount() {
        return Float.isFinite(this.smokeParticleCount) ? this.smokeParticleCount : 0.0F;
    }

    public float getSmokeScale() {
        return Float.isFinite(this.smokeScale) && this.smokeScale > 0.0F ? this.smokeScale : 0.0F;
    }

    public enum Type {
        @SerializedName("default")
        DEFAULT,
        @SerializedName("renew")
        RENEW;
    }
}
