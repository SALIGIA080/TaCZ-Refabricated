package cn.sh1rocu.tacz.util.forge;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * 包装一个 {@link PackResources}，把所有读取方法原样转发给底层实现，但把
 * {@link #close()} 变成空操作，从而<b>阻止底层资源（尤其是 zip 枪包）被提前关闭</b>。
 *
 * <h2>背景（2026-11-06 定位的第三方枪包无声音真根因）</h2>
 * <p>第三方枪包是 zip（{@code [NPGP3.8]Release.zip} 等），通过 vanilla 的
 * {@code FilePackResources} 加载；官方枪包是文件夹，走 TACZ 自定义的
 * {@link PathPackResources}（其 {@code close()} 本就是空实现）。</p>
 *
 * <p>vanilla 的 zip 用 {@code SharedZipFileAccess} 维护一个共享 {@code ZipFile}，
 * {@code FilePackResources.close()} 会真正 {@code zipFile.close()}。而声音缓冲是
 * <b>异步</b>加载的（{@code SoundBufferLibrary.getCompleteBuffer} 走
 * {@code nonCriticalIoPool} 线程）。资源 reload 收尾时，旧的
 * {@code MultiPackResourceManager} 被 close，级联调用所有 delegate 的 {@code close()}，
 * 于是 zip 被关掉。之后异步线程去读 zip 里的 {@code tacz_sounds/*.ogg} 就抛
 * {@code IllegalStateException: zip file closed}，buffer 加载失败（future 异常完成，
 * {@code SoundEngine.play} 里的 {@code thenAccept} 永不执行）→ 无声。</p>
 *
 * <p>官方枪因为是文件夹（{@code PathPackResources.open()} 每次 {@code Files.newInputStream()}，
 * 无状态），不受 zip 关闭影响，所以「官方枪有声、第三方枪无声」。</p>
 *
 * <p>修复：给 zip 枪包套上本包装类，{@code close()} 空操作，让 zip 在游戏运行期间保持打开
 * （与 1025 原版 Forge 用 {@code SecureJar} 保持打开、且 {@code PathPackResources.close()}
 * 空实现的语义一致）。代价是 zip 句柄常驻，与 1025 原版行为相同，可接受。</p>
 */
public final class NoClosePackResources implements PackResources {
    private final PackResources delegate;

    public NoClosePackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return delegate.getRootResource(paths);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        delegate.listResources(type, namespace, path, resourceOutput);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> deserializer) throws IOException {
        return delegate.getMetadataSection(deserializer);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public String packId() {
        return delegate.packId();
    }

    @Override
    public Optional<net.minecraft.server.packs.repository.KnownPack> knownPackInfo() {
        return delegate.knownPackInfo();
    }

    /**
     * 关键：空操作，阻止底层 zip 被提前关闭。
     */
    @Override
    public void close() {
        // 故意不调用 delegate.close()，保持 zip 打开，供异步声音缓冲加载使用。
    }
}
