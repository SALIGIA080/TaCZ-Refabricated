package com.tacz.guns.resource;

import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public final class GunPackOverlayResolver {
    private GunPackOverlayResolver() { }

    public static List<String> resolve(PackMeta meta, String minecraftVersion) {
        if (meta.getOverlays() == null) return List.of();
        if (meta.getOverlays().entries() == null) {
            throw new IllegalArgumentException("overlays.entries must be an array");
        }
        Set<String> seen = new HashSet<>();
        List<String> selected = new ArrayList<>();
        for (PackMeta.OverlayEntry entry : meta.getOverlays().entries()) {
            if (entry == null || entry.directory() == null || !entry.directory().matches("[a-z0-9_-]+")
                    || Set.of("assets", "data").contains(entry.directory())) {
                throw new IllegalArgumentException("Invalid overlay directory: " + entry);
            }
            if (!seen.add(entry.directory())) {
                throw new IllegalArgumentException("Duplicate overlay directory: " + entry.directory());
            }
            if (matches(entry.minecraft(), minecraftVersion)) selected.add(entry.directory());
        }
        return List.copyOf(selected);
    }

    public static boolean matches(String specification, String version) {
        if (specification == null || specification.isBlank()) {
            throw new IllegalArgumentException("Missing Minecraft version range");
        }
        try {
            VersionRange range = VersionRange.createFromVersionSpec(specification);
            // Maven's bare version is a recommendation, not a constraint.
            if (!range.hasRestrictions()) {
                throw new IllegalArgumentException("Use an explicit Minecraft range, e.g. [1.21.1]: " + specification);
            }
            if (!version.matches("\\d+\\.\\d+(?:\\.\\d+)?")) {
                return specification.equals("[" + version + "]");
            }
            return range.containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("Invalid Minecraft version range: " + specification, e);
        }
    }

    public static void validate(Path path, List<String> selected) throws IOException {
        if (selected.isEmpty()) return;
        if (Files.isDirectory(path)) {
            Path root = path.toRealPath();
            for (String directory : selected) {
                Path overlay = path.resolve(directory);
                if (!Files.isDirectory(overlay) || !overlay.toRealPath().startsWith(root)) {
                    throw new IOException("Missing or escaping overlay directory: " + directory);
                }
                try (var paths = Files.walk(overlay)) {
                    for (Path child : paths.toList()) {
                        if (Files.isSymbolicLink(child) && Files.isDirectory(child)) {
                            throw new IOException("Linked overlay directories are not supported: " + child);
                        }
                        if (!child.toRealPath().startsWith(root)) {
                            throw new IOException("Overlay path escapes gun pack: " + child);
                        }
                    }
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                Set<String> found = new HashSet<>();
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    for (String directory : selected) {
                        if (name.startsWith(directory + "/")) {
                            validateEntryPath(name);
                            found.add(directory);
                        }
                    }
                }
                if (!found.containsAll(selected)) {
                    throw new IOException("Missing overlay directories: " + selected.stream().filter(s -> !found.contains(s)).toList());
                }
            }
        }
    }

    public static void validateEntryPath(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) {
            throw new IOException("Unsafe pack path: " + name);
        }
        String relative = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Unsafe pack path: " + name);
            }
        }
    }

    public static PackResources open(Path path, PackLocationInfo info, List<String> selected) throws IOException {
        validate(path, selected);
        List<PackResources> overlays = new ArrayList<>();
        if (Files.isDirectory(path)) {
            for (String directory : selected) overlays.add(new PathPackResources(info, path.resolve(directory)));
            return new OverlayPackResources(new PathPackResources(info, path), overlays, null);
        }
        // 26.2 关键修复：zip 枪包<b>没有 overlay</b>时，直接用 vanilla 的
        // FilePackResources.FileResourcesSupplier 加载（旧版已验证的路径）。
        //
        // 不能像官方 1.2.0 那样对 zip 无条件 FileSystems.newFileSystem 挂载：
        // 26.2 的 vanilla PathPackResources.getNamespaces() 用 Files.newDirectoryStream
        // 直接遍历 zip 根目录，会触发 jdk.zipfs.ZipFileSystem.getInode()，而在 26.2
        // (Java 25) 下该 zipfs 的 inodes 字段可能为 null → NPE「Cannot invoke
        // LinkedHashMap.get(Object) because this.inodes is null」，导致启动即崩。
        // 官方 1.21.1 的 PathPackResources 用 Files.list（不触发 getInode），故无此问题。
        if (selected.isEmpty()) {
            // 套 NoClosePackResources 阻止 zip 被 reload 收尾提前 close（第三方枪包无声音的根因，
            // 详见 NoClosePackResources 类注释）。保持 zip 常驻与 1025 Forge SecureJar 语义一致。
            return new cn.sh1rocu.tacz.util.forge.NoClosePackResources(
                    new FilePackResources.FileResourcesSupplier(path).openPrimary(info));
        }
        // 有 overlay 的 zip 枪包：仍需 zipfs 挂载以支持目录覆盖。为避免上述 NPE，
        // 用本项目自建的 cn.sh1rocu.tacz.util.forge.PathPackResources（其 getNamespaces
        // 走 Files.walk + IOException 兜底，且 close 为空实现），并保持 zipfs 强引用到 close。
        var zip = FileSystems.newFileSystem(path, Map.of());
        try {
            Path root = zip.getPath("/");
            for (String directory : selected) overlays.add(new cn.sh1rocu.tacz.util.forge.PathPackResources(info.id(), false, root.resolve(directory)));
            return new OverlayPackResources(new cn.sh1rocu.tacz.util.forge.PathPackResources(info.id(), false, root), overlays, zip);
        } catch (RuntimeException | Error e) {
            try {
                zip.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }
}
