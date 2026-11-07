package com.tacz.guns.client.model;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import net.minecraft.world.item.ItemStack;

public class DisplayGunModelInstance extends BakedModelInstance {
   private static final AttachmentType[] TYPES = AttachmentType.values();
   private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_\\d+)?$");
   private final BoneState bulletInBarrel;
   private final BoneState bulletInMag;
   private final BoneState bulletChain;
   private final BoneState mount;
   private final BoneState carry;
   private final BoneState sight;
   private final BoneState sightFolded;
   private final BoneState magStandard;
   private final BoneState magExtended1;
   private final BoneState magExtended2;
   private final BoneState magExtended3;
   private final BoneState handguardDefault;
   private final BoneState handguardTactical;
   private final BoneState[] attachmentDefaults = new BoneState[TYPES.length];
   private final int[] attachmentPositions = new int[TYPES.length];
   private final BoneState[] adapterChildren;
   private final int[] laserBeamBones;
   private final int muzzleFlashPosition;
   private final int[] shellOriginBones;

   public DisplayGunModelInstance(BakedBedrockModel model, Set<String> hiddenTextBones, DisplayGunModelInstance.Kind kind) {
      super(model);
      Builder lasers = IntStream.builder();

      for (BakedBoneDefinition definition : model.bones()) {
         String name = definition.name();
         if (name != null) {
            if (kind == DisplayGunModelInstance.Kind.GUN ? name.equals("laser_beam") : LASER_BEAM_PATTERN.matcher(name).matches()) {
               lasers.add(definition.index());
            }

            BoneState bone = this.getBone(definition.index());
            bone.illuminated = name.endsWith("_illuminated");
            if (hiddenTextBones.contains(name)
               || name.startsWith("laser_beam")
               || kind == DisplayGunModelInstance.Kind.GUN && isHiddenGunBone(name)
               || kind == DisplayGunModelInstance.Kind.ATTACHMENT && name.startsWith("division")) {
               bone.visible = false;
            }
         }
      }

      this.laserBeamBones = lasers.build().toArray();
      this.muzzleFlashPosition = this.getIndex("muzzle_flash");
      Builder shells = IntStream.builder();
      if (kind == DisplayGunModelInstance.Kind.GUN) {
         int shell = this.getIndex("shell");

         for (int index = 1; shell >= 0; shell = this.getIndex("shell_" + index++)) {
            shells.add(shell);
         }
      }

      this.shellOriginBones = shells.build().toArray();
      this.bulletInBarrel = this.getBone("bullet_in_barrel");
      this.bulletInMag = this.getBone("bullet_in_mag");
      this.bulletChain = this.getBone("bullet_chain");
      this.mount = this.getBone("mount");
      this.carry = this.getBone("carry");
      this.sight = this.getBone("sight");
      this.sightFolded = this.getBone("sight_folded");
      this.magStandard = this.getBone("mag_standard");
      this.magExtended1 = this.getBone("mag_extended_1");
      this.magExtended2 = this.getBone("mag_extended_2");
      this.magExtended3 = this.getBone("mag_extended_3");
      this.handguardDefault = this.getBone("handguard_default");
      this.handguardTactical = this.getBone("handguard_tactical");

      for (AttachmentType type : TYPES) {
         if (type != AttachmentType.NONE) {
            String prefix = type.name().toLowerCase(Locale.ROOT);
            int position = this.getIndex(prefix + "_pos");
            this.attachmentPositions[type.ordinal()] = position;
            this.attachmentDefaults[type.ordinal()] = this.getBone(prefix + "_default");
            if (kind == DisplayGunModelInstance.Kind.GUN) {
               setVisible(this.getBone(position), false);
            }
         }
      }

      BoneState adapter = this.getBone("attachment_adapter");
      if (adapter == null) {
         this.adapterChildren = new BoneState[0];
      } else {
         int[] children = adapter.children();
         this.adapterChildren = new BoneState[children.length];

         for (int i = 0; i < children.length; i++) {
            this.adapterChildren[i] = this.getBone(children[i]);
         }
      }
   }

   public int attachmentPosition(AttachmentType type) {
      return this.attachmentPositions[type.ordinal()];
   }

   public int[] laserBeamBones() {
      return this.laserBeamBones;
   }

   public int muzzleFlashPosition() {
      return this.muzzleFlashPosition;
   }

   public int[] shellOriginBones() {
      return this.shellOriginBones;
   }

   public void configureGun(IGun gun, ItemStack stack, Map<AttachmentType, ClientAttachmentIndex> attachments) {
      ClientAttachmentIndex scope = attachments.get(AttachmentType.SCOPE);
      ClientAttachmentIndex magazine = attachments.get(AttachmentType.EXTENDED_MAG);
      boolean hasScope = scope != null;
      boolean tacticalHandguard = attachments.containsKey(AttachmentType.LASER) || attachments.containsKey(AttachmentType.GRIP);
      int magLevel = magazine == null ? 0 : magazine.getData().getExtendedMagLevel();
      boolean hasAmmo = gun.getCurrentAmmoCount(stack) > 0;
      setVisible(this.bulletInBarrel, gun.hasBulletInBarrel(stack));
      setVisible(this.bulletInMag, hasAmmo);
      setVisible(this.bulletChain, hasAmmo);
      setVisible(this.mount, scope != null && scope.isShowMount());
      setVisible(this.carry, !hasScope);
      setVisible(this.sight, !hasScope);
      setVisible(this.sightFolded, hasScope);
      setVisible(this.magStandard, magLevel == 0);
      setVisible(this.magExtended1, magLevel == 1);
      setVisible(this.magExtended2, magLevel == 2);
      setVisible(this.magExtended3, magLevel == 3);
      setVisible(this.handguardDefault, !tacticalHandguard);
      setVisible(this.handguardTactical, tacticalHandguard);

      for (AttachmentType type : TYPES) {
         if (type != AttachmentType.NONE) {
            ClientAttachmentIndex index = attachments.get(type);
            setVisible(this.attachmentDefaults[type.ordinal()], index == null || type == AttachmentType.MUZZLE && index.isShowMuzzle());
         }
      }

      for (BoneState child : this.adapterChildren) {
         if (child != null) {
            boolean visible = false;

            for (ClientAttachmentIndex index : attachments.values()) {
               if (child.name() != null && child.name().equals(index.getAdapterNodeName())) {
                  visible = true;
                  break;
               }
            }

            child.visible = visible;
         }
      }
   }

   public static void setVisible(BoneState bone, boolean visible) {
      if (bone != null) {
         bone.visible = visible;
      }
   }

   public static boolean isHiddenGunBone(String name) {
      return name.equals("additional_magazine")
         || name.equals("muzzle_flash")
         || name.equals("lefthand_pos")
         || name.equals("righthand_pos")
         || name.equals("shell")
         || name.matches("shell_\\d+");
   }

   public static enum Kind {
      GUN,
      ATTACHMENT;
   }
}