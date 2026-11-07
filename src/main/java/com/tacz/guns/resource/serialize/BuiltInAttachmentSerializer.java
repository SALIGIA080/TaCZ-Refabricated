package com.tacz.guns.resource.serialize;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.resource.pojo.data.gun.BuiltInAttachment;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.resources.Identifier;

public class BuiltInAttachmentSerializer implements JsonDeserializer<BuiltInAttachment> {
   public BuiltInAttachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      List<Identifier> attachments = Lists.newArrayList();
      Identifier defaultAttachment = null;
      Identifier displayAttachment = null;
      boolean showIcon = true;
      if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
         displayAttachment = (Identifier)context.deserialize(json, Identifier.class);
         attachments.add(displayAttachment);
         showIcon = false;
      } else if (json.isJsonArray()) {
         attachments.addAll(parseAttachmentList(json.getAsJsonArray(), context));
      } else {
         if (!json.isJsonObject()) {
            throw new JsonParseException("builtin attachment must be a string, array, or object");
         }

         JsonObject object = json.getAsJsonObject();
         JsonElement attachmentsElement = object.get("attachments");
         if (attachmentsElement != null) {
            if (attachmentsElement.isJsonArray()) {
               attachments.addAll(parseAttachmentList(attachmentsElement.getAsJsonArray(), context));
            } else {
               if (!attachmentsElement.isJsonPrimitive() || !attachmentsElement.getAsJsonPrimitive().isString()) {
                  throw new JsonParseException("builtin attachments must be a string or string array");
               }

               attachments.add((Identifier)context.deserialize(attachmentsElement, Identifier.class));
            }
         }

         JsonElement defaultElement = object.has("default_attached") ? object.get("default_attached") : object.get("default");
         if (defaultElement != null) {
            defaultAttachment = (Identifier)context.deserialize(defaultElement, Identifier.class);
            if (!attachments.contains(defaultAttachment)) {
               attachments.add(defaultAttachment);
            }
         }

         JsonElement showIconElement = object.get("show_icon");
         if (showIconElement != null) {
            showIcon = showIconElement.getAsBoolean();
         }
      }

      return new BuiltInAttachment(attachments, defaultAttachment, displayAttachment, showIcon);
   }

   private static List<Identifier> parseAttachmentList(JsonArray array, JsonDeserializationContext context) {
      List<Identifier> result = Lists.newArrayList();
      array.forEach(element -> result.add((Identifier)context.deserialize(element, Identifier.class)));
      return result;
   }
}
