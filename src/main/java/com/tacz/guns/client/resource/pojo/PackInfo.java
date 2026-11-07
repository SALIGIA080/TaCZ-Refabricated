package com.tacz.guns.client.resource.pojo;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PackInfo {
    @SerializedName("version")
    private String version = "1.0.0";

    @SerializedName("name")
    private String name = "custom.tacz.error.no_name";

    @SerializedName("desc")
    private String description = "";

    @SerializedName("license")
    private String license = "All Rights Reserved";

    @SerializedName("authors")
    private List<String> authors = Lists.newArrayList();

    @SerializedName("date")
    private String date = "1919-08-10";

    @SerializedName("url")
    private String url;

    @SerializedName("logo")
    private String logo;

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLicense() {
        return license;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getDate() {
        return date;
    }

    public String getUrl() {
        return url;
    }

    @Nullable
    public Identifier getLogo(String namespace) {
        return logo != null && !logo.isBlank() ? Identifier.tryParse(logo.contains(":") ? logo : namespace + ":" + logo) : null;
    }
}
