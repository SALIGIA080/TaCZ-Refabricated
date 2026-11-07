package com.tacz.guns.client.resource.pojo.display.attachment;

import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Map;

public class AttachmentDisplay implements IDisplay {
    @SerializedName("laser")
    private LaserConfig laserConfig;

    @SerializedName("slot")
    private Identifier slotTextureLocation;

    @SerializedName("model")
    private Identifier model;

    @SerializedName("texture")
    private Identifier texture;

    @SerializedName("lod")
    @Nullable
    private AttachmentLod attachmentLod;

    @SerializedName("adapter")
    @Nullable
    private String adapterNodeName;

    @SerializedName("show_muzzle")
    private boolean showMuzzle = false;

    @SerializedName("show_mount")
    private boolean showMount = true;

    @SerializedName("muzzle_flash_offset")
    private float muzzleFlashOffset = 0.0F;

    @SerializedName("text_show")
    private Map<String, TextShow> textShows = Maps.newHashMap();

    @SerializedName("zoom")
    @Nullable
    private float[] zoom;

    @SerializedName("views")
    @Nullable
    private int[] views;

    @SerializedName("scope")
    private boolean isScope = false;

    @SerializedName("scope_lens")
    private ScopeLensConfig scopeLens = new ScopeLensConfig();

    @SerializedName("sight")
    private boolean isSight = false;

    @SerializedName("fov")
    private float fov = 70;

    @SerializedName("views_fov")
    @Nullable
    private float[] viewsFov;

    @SerializedName("idle_scope_height_adjust")
    private float idleScopeHeightAdjust = 0.0F;

    @SerializedName("sounds")
    private Map<String, SoundData> sounds = Maps.newHashMap();

    @SerializedName("mode")
    private String variantMode = "replace";

    @SerializedName("mount_offset")
    @Nullable
    private Vector3f mountOffset;

    public Identifier getSlotTextureLocation() {
        return slotTextureLocation;
    }

    public Identifier getModel() {
        return model;
    }

    public Identifier getTexture() {
        return texture;
    }

    @Nullable
    public AttachmentLod getAttachmentLod() {
        return attachmentLod;
    }

    @Nullable
    public String getAdapterNodeName() {
        return adapterNodeName;
    }

    public boolean isShowMuzzle() {
        return showMuzzle;
    }

    public boolean isShowMount() {
        return showMount;
    }

    public float getMuzzleFlashOffset() {
        return muzzleFlashOffset;
    }

    public Map<String, TextShow> getTextShows() {
        return textShows;
    }

    @Nullable
    public float[] getZoom() {
        return zoom;
    }

    @Nullable
    public int[] getViews() {
        return views;
    }

    public boolean isScope() {
        return isScope;
    }

    public ScopeLensConfig getScopeLens() {
        return scopeLens;
    }

    public boolean isSight() {
        return isSight;
    }

    public float getFov() {
        return fov;
    }

    @Nullable
    public float[] getViewsFov() {
        return viewsFov;
    }

    public float getIdleScopeHeightAdjust() {
        return idleScopeHeightAdjust;
    }

    public Map<String, SoundData> getSounds() {
        return sounds;
    }

    public String getVariantMode() {
        return variantMode == null ? "replace" : variantMode;
    }

    @Nullable
    public Vector3f getMountOffset() {
        return mountOffset;
    }

    @Nullable
    public LaserConfig getLaserConfig() {
        return laserConfig;
    }

    @Override
    public void init() {
        if (this.scopeLens == null) {
            this.scopeLens = new ScopeLensConfig();
        }
        this.scopeLens.init();
        if (!Float.isFinite(muzzleFlashOffset) || muzzleFlashOffset < 0.0F) {
            muzzleFlashOffset = 0.0F;
        }
        if (slotTextureLocation != null) {
            slotTextureLocation = converter.idToFile(slotTextureLocation);
        }
        if (texture != null) {
            texture = converter.idToFile(texture);
        }
        if (attachmentLod != null && attachmentLod.modelTexture != null) {
            attachmentLod.modelTexture = converter.idToFile(attachmentLod.modelTexture);
        }
    }
}
