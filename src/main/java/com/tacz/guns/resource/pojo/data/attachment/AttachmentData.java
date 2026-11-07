package com.tacz.guns.resource.pojo.data.attachment;

import com.google.common.collect.Maps;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.modifier.JsonProperty;

import javax.annotation.Nullable;
import java.util.Map;

public class AttachmentData {
    @Expose(serialize = false, deserialize = false)
    private Map<String, JsonProperty<?>> modifier = Maps.newHashMap();

    @SerializedName("weight")
    private float weight = 0;

    @SerializedName("extended_mag_level")
    private int extendedMagLevel = 0;

    @SerializedName("flash_hider")
    @Nullable
    private Float flashHider = null;

    @SerializedName("smoke_hider")
    @Nullable
    private Float smokeHider = null;

    @SerializedName("smoke_speed_scale")
    @Nullable
    private Float smokeSpeedScale = null;

    @SerializedName("smoke_count_scale")
    @Nullable
    private Float smokeCountScale = null;

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

    @SerializedName("melee")
    @Nullable
    private MeleeData meleeData = null;

    /** 1025 delta：消音器对枪口烟雾的速度/数量/方向缩放。 */
    public boolean hasSmokeSpeedScale() {
        return this.smokeSpeedScale != null && Float.isFinite(this.smokeSpeedScale) && this.smokeSpeedScale >= 0.0F;
    }

    public float getSmokeSpeedScale() {
        return this.hasSmokeSpeedScale() ? this.smokeSpeedScale : 1.0F;
    }

    public boolean hasSmokeCountScale() {
        return this.smokeCountScale != null && Float.isFinite(this.smokeCountScale) && this.smokeCountScale >= 0.0F;
    }

    public float getSmokeCountScale() {
        return this.hasSmokeCountScale() ? this.smokeCountScale : 1.0F;
    }

    @Nullable
    public com.tacz.guns.resource.pojo.data.SmokeDirectionData.Config getSmokeDirectionConfig() {
        return com.tacz.guns.resource.pojo.data.SmokeDirectionData.parse(this.smokeDirections, this.smokeSpreadAngle, this.smokeDirection, this.smokeSpread);
    }

    public float getWeight() {
        return weight;
    }

    public int getExtendedMagLevel() {
        return extendedMagLevel;
    }

    public boolean hasFlashHider() {
        return flashHider != null && Float.isFinite(flashHider) && flashHider >= 0;
    }

    public float getFlashHider() {
        return hasFlashHider() ? flashHider : 1;
    }

    public boolean hasSmokeHider() {
        return smokeHider != null && Float.isFinite(smokeHider) && smokeHider >= 0;
    }

    public float getSmokeHider() {
        return hasSmokeHider() ? smokeHider : 1;
    }

    @Nullable
    public MeleeData getMeleeData() {
        return meleeData;
    }

    public void addModifier(String id, JsonProperty<?> jsonProperty) {
        modifier.put(id, jsonProperty);
    }

    public Map<String, JsonProperty<?>> getModifier() {
        return modifier;
    }
}
