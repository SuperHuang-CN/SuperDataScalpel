package cn.superhuang.data.scalpel.business.panorama.service;

import cn.superhuang.data.scalpel.business.panorama.domain.*;
import cn.superhuang.data.scalpel.business.panorama.repository.*;
import cn.superhuang.data.scalpel.business.panorama.web.request.*;
import cn.superhuang.data.scalpel.business.panorama.web.response.*;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PanoramaService {
    private static final Logger log = LoggerFactory.getLogger(PanoramaService.class);
    private final PanoramaRepository panoramas;
    private final PanoramaContentRepository contents;
    private final DirectoryService directories;
    private final SearchEngine search;
    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final PanoramaImageProcessor images;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Set<UUID> uploading = ConcurrentHashMap.newKeySet();

    public PanoramaService(PanoramaRepository panoramas, PanoramaContentRepository contents, DirectoryService directories,
            SearchEngine search, ObjectProvider<FileObjectStorage> storageProvider, PanoramaImageProcessor images,
            ObjectMapper json, PlatformTransactionManager transactions) {
        this.panoramas = panoramas; this.contents = contents; this.directories = directories; this.search = search;
        this.storageProvider = storageProvider; this.images = images; this.json = json; this.tx = new TransactionTemplate(transactions);
    }
    public PageResponse<PanoramaResponse> search(SearchRequest request) { return search(request, null, false); }
    public PageResponse<PanoramaResponse> search(SearchRequest request, List<UUID> directoryIds, boolean uncategorized) {
        return tx.execute(status -> {
            var page = search.search(request, Panorama.class, panoramas, directoryScope(directoryIds, uncategorized));
            return new PageResponse<>(page.getContent().stream().map(this::response).toList(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
        });
    }
    public PanoramaResponse get(UUID id) { return tx.execute(status -> response(require(id, false))); }

    public PanoramaMapPointsResponse mapPoints(SearchRequest request, List<UUID> directoryIds, boolean uncategorized,
                                               Double west, Double south, Double east, Double north) {
        boolean bounds = west != null && south != null && east != null && north != null;
        if (!bounds && (west != null || south != null || east != null || north != null)) throw badRequest("地图视口需要完整边界");
        if (bounds && (!PanoramaImageProcessor.validLocation(south, west) || !PanoramaImageProcessor.validLocation(north, east) || south > north)) throw badRequest("地图视口无效");
        Specification<Panorama> scope = directoryScope(directoryIds, uncategorized).and((root, query, cb) -> {
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(cb.isNotNull(root.get("currentContentId")));
            conditions.add(cb.between(root.get("latitude"), -90.0, 90.0));
            conditions.add(cb.between(root.get("longitude"), -180.0, 180.0));
            if (bounds) {
                conditions.add(cb.between(root.get("latitude"), south, north));
                conditions.add(west <= east ? cb.between(root.get("longitude"), west, east)
                        : cb.or(cb.ge(root.get("longitude"), west), cb.le(root.get("longitude"), east)));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        });
        return tx.execute(status -> {
            var page = search.search(new SearchRequest(request.search(), 0, 500, "name,id"), Panorama.class, panoramas, scope);
            return new PanoramaMapPointsResponse(page.getContent().stream().map(p -> new PanoramaMapPointResponse(p.getId(), p.getName(), p.getLatitude(), p.getLongitude(), p.getCaptureTime(), p.getContentVersion())).toList(), page.getTotalElements(), page.getTotalElements() > 500);
        });
    }
    private Specification<Panorama> directoryScope(List<UUID> ids, boolean uncategorized) {
        if (uncategorized && ids != null && !ids.isEmpty()) throw badRequest("目录条件不能同时指定未分类");
        return (root, query, cb) -> uncategorized ? cb.isNull(root.get("directoryId"))
                : ids == null || ids.isEmpty() ? cb.conjunction() : root.get("directoryId").in(ids);
    }

    public PanoramaResponse upload(MultipartFile file, UUID requestId, UUID directoryId, UUID targetId, Long expectedVersion) {
        FileObjectStorage storage = storage();
        PanoramaResponse duplicate = duplicate(requestId, targetId);
        if (duplicate != null) return duplicate;
        if (!uploading.add(requestId)) throw conflict("同一次上传正在接收，请稍后重试");
        Path work = null;
        PanoramaContent staged = null;
        try {
            if (file.isEmpty()) throw PanoramaImageProcessor.invalid("图片不能为空");
            if (file.getSize() > PanoramaImageProcessor.MAX_BYTES) throw tooLarge();
            String filename = safeFilename(file.getOriginalFilename());
            if (!filename.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g")) throw PanoramaImageProcessor.invalid("文件扩展名必须为 JPG 或 JPEG");
            if (targetId == null) directories.validateAssignment(DirectoryScope.PANORAMA, directoryId);
            else tx.executeWithoutResult(status -> validateReplacement(require(targetId, true), expectedVersion));
            work = Files.createTempDirectory("datascalpel-panorama-");
            Path original = work.resolve("original.jpg");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (var in = new DigestInputStream(file.getInputStream(), digest); var out = Files.newOutputStream(original)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) != -1) { bytes += count; if (bytes > PanoramaImageProcessor.MAX_BYTES) throw tooLarge(); out.write(buffer, 0, count); }
            }
            PanoramaImageProcessor.Dimensions dimensions;
            try { dimensions = images.inspect(original); }
            catch (IOException e) { throw PanoramaImageProcessor.invalid("JPEG 结构损坏或无法读取尺寸"); }
            // Reject explicit projection/crop mismatches before admission. Optional metadata failures are tolerated.
            images.metadata(original, dimensions);
            PanoramaContent candidate = new PanoramaContent();
            candidate.setClientRequestId(requestId); candidate.setReplacementTargetId(targetId);
            candidate.setOriginalFilename(filename); candidate.setByteSize(bytes);
            candidate.setWidth(dimensions.width()); candidate.setHeight(dimensions.height());
            candidate.setSha256(HexFormat.of().formatHex(digest.digest()));
            candidate.setObjectPrefix("panoramas/" + UUID.randomUUID() + "/");
            candidate.setCleanupAfter(Instant.now().plusSeconds(3600));
            // Persist the cleanup address before external I/O, including crash/partial upload paths.
            staged = tx.execute(status -> contents.saveAndFlush(candidate));
            try (var in = Files.newInputStream(original)) { storage.store(candidate.getObjectPrefix() + "original.jpg", in, bytes, "image/jpeg"); }
            return tx.execute(status -> {
                Panorama panorama;
                if (targetId == null) {
                    directories.validateAssignment(DirectoryScope.PANORAMA, directoryId);
                    panorama = new Panorama(); panorama.setCreationRequestId(requestId); panorama.setName(filename.substring(0, filename.lastIndexOf('.')));
                    if (panorama.getName().isBlank()) panorama.setName("全景影像");
                    panorama.setDirectoryId(directoryId); panoramas.saveAndFlush(panorama);
                } else {
                    panorama = require(targetId, true); validateReplacement(panorama, expectedVersion);
                    retire(panorama.getCandidateContentId());
                }
                PanoramaContent content = contents.findById(candidate.getId()).orElseThrow();
                content.setPanoramaId(panorama.getId()); content.setCleanupPending(false); content.setCleanupAfter(null);
                panorama.setCandidateContentId(content.getId()); panorama.setProcessingStatus(PanoramaProcessingStatus.QUEUED); panorama.setProcessingError(null);
                panoramas.flush(); return response(panorama);
            });
        } catch (DataIntegrityViolationException e) {
            PanoramaResponse previous = duplicate(requestId, targetId);
            if (previous != null) return previous;
            throw conflict("同一次上传尚未完成，请稍后重试");
        } catch (CodedProblemException | ResponseStatusException e) { throw e; }
        catch (IOException e) { log.warn("Panorama upload I/O failed", e); throw unavailable(); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        catch (RuntimeException e) { log.warn("Panorama upload failed", e); throw unavailable(); }
        finally {
            uploading.remove(requestId);
            if (staged != null) {
                UUID contentId = staged.getId();
                tx.executeWithoutResult(status -> contents.findById(contentId).filter(PanoramaContent::getCleanupPending).ifPresent(c -> c.setCleanupAfter(Instant.now())));
            }
            cleanWork(work);
        }
    }
    private PanoramaResponse duplicate(UUID requestId, UUID targetId) {
        return tx.execute(status -> {
            var created = panoramas.findByCreationRequestId(requestId).orElse(null);
            if (created != null) {
                if (targetId != null) throw conflict("上传请求标识已用于创建资源");
                return response(created);
            }
            var previous = contents.findByClientRequestId(requestId).orElse(null);
            if (previous == null) return null;
            if (!Objects.equals(previous.getReplacementTargetId(), targetId)) throw conflict("上传请求标识已用于其他操作");
            if (previous.getPanoramaId() != null && !previous.getCleanupPending()) return response(require(previous.getPanoramaId(), false));
            throw conflict("该上传正在接收或清理，请稍后重试");
        });
    }
    private void validateReplacement(Panorama p, Long version) { checkVersion(p, version); if (busy(p)) throw conflict("已有排队或处理中的候选成品"); }

    public PanoramaResponse update(UUID id, UpdatePanoramaRequest request) {
        return tx.execute(status -> {
            Panorama p = require(id, true); checkVersion(p, request.expectedContentVersion());
            directories.validateAssignment(DirectoryScope.PANORAMA, request.directoryId());
            if ((request.latitude() == null) != (request.longitude() == null)) throw badRequest("经纬度需要同时填写或清空");
            if (request.latitude() != null && !PanoramaImageProcessor.validLocation(request.latitude(), request.longitude())) throw badRequest("经纬度无效");
            if (request.captureOffset() != null && !request.captureOffset().isBlank() && PanoramaImageProcessor.validOffset(request.captureOffset()) == null) throw badRequest("时区偏移无效");
            p.setName(request.name().trim()); p.setDescription(request.description()); p.setDirectoryId(request.directoryId());
            p.setTimeMode(request.timeMode()); p.setLocationMode(request.locationMode());
            p.setCaptureTime(request.captureTime()); p.setCaptureOffset(request.captureTime() == null ? null : PanoramaImageProcessor.validOffset(request.captureOffset()));
            p.setLatitude(request.latitude()); p.setLongitude(request.longitude());
            applyAutomatic(p, metadata(p.getCurrentContentId())); panoramas.flush(); return response(p);
        });
    }
    public PanoramaResponse retry(UUID id, UUID candidateId) {
        return tx.execute(status -> {
            Panorama p = failedCandidate(id, candidateId);
            p.setProcessingStatus(PanoramaProcessingStatus.QUEUED); p.setProcessingError(null);
            panoramas.flush(); return response(p);
        });
    }
    public PanoramaResponse discard(UUID id, UUID candidateId) {
        return tx.execute(status -> {
            Panorama p = failedCandidate(id, candidateId);
            if (p.getCurrentContentId() == null) throw conflict("首次处理失败请重试、替换或删除资源");
            retire(candidateId); p.setCandidateContentId(null); p.setProcessingStatus(PanoramaProcessingStatus.READY); p.setProcessingError(null);
            panoramas.flush(); return response(p);
        });
    }
    private Panorama failedCandidate(UUID id, UUID candidateId) {
        Panorama p = require(id, true);
        if (!Objects.equals(p.getCandidateContentId(), candidateId) || p.getProcessingStatus() != PanoramaProcessingStatus.FAILED) throw conflict("候选成品已变化或当前不能执行此操作");
        return p;
    }
    public void delete(UUID id) {
        tx.executeWithoutResult(status -> {
            Panorama p = require(id, true);
            if (p.getProcessingStatus() == PanoramaProcessingStatus.PROCESSING) throw conflict("正在处理的全景不能删除");
            retire(p.getCurrentContentId()); retire(p.getCandidateContentId()); panoramas.delete(p);
        });
    }

    public PanoramaBinary open(UUID id, long version, String variant) {
        // Open the object while holding the short single-instance media lease; retirement also uses this monitor.
        synchronized (this) {
            var descriptor = tx.execute(status -> {
                Panorama p = require(id, false); checkVersion(p, version);
                if (p.getCurrentContentId() == null) throw new CodedProblemException(HttpStatus.CONFLICT, "PANORAMA_PREVIEW_UNAVAILABLE", "全景尚未处理完成");
                PanoramaContent c = contents.findById(p.getCurrentContentId()).orElseThrow();
                return new BinaryDescriptor(c.getObjectPrefix() + variant + ".jpg", c.getOriginalFilename());
            });
            try { return new PanoramaBinary(storage().open(descriptor.key()), descriptor.filename()); }
            catch (CodedProblemException e) { throw e; }
            catch (RuntimeException e) { log.warn("Panorama content read failed", e); throw unavailable(); }
        }
    }

    public void recover() {
        tx.executeWithoutResult(status -> panoramas.findAllByProcessingStatus(PanoramaProcessingStatus.PROCESSING).forEach(p -> p.setProcessingStatus(PanoramaProcessingStatus.QUEUED)));
    }
    /** Called serially by the dedicated worker. All image/storage operations run outside management transactions. */
    public void processNext() {
        if (storageProvider.getIfAvailable() == null) return;
        PanoramaContent candidate = tx.execute(status -> {
            var first = panoramas.findFirstByProcessingStatusOrderByCreatedAtAsc(PanoramaProcessingStatus.QUEUED).orElse(null);
            if (first == null) return null;
            Panorama p = panoramas.findLockedById(first.getId()).orElse(null);
            if (p == null || p.getProcessingStatus() != PanoramaProcessingStatus.QUEUED) return null;
            p.setProcessingStatus(PanoramaProcessingStatus.PROCESSING);
            return contents.findById(p.getCandidateContentId()).orElseThrow();
        });
        if (candidate == null) return;
        Path work = null;
        try {
            work = Files.createTempDirectory("datascalpel-panorama-");
            Path original = work.resolve("original.jpg"), preview = work.resolve("preview.jpg"), thumbnail = work.resolve("thumbnail.jpg");
            try (var remote = storage().open(candidate.getObjectPrefix() + "original.jpg")) { Files.copy(remote.inputStream(), original); }
            var dimensions = images.inspect(original);
            var metadata = images.metadata(original, dimensions);
            images.derivatives(original, preview, thumbnail, dimensions);
            for (Path image : List.of(preview, thumbnail)) {
                try (var in = Files.newInputStream(image)) { storage().store(candidate.getObjectPrefix() + image.getFileName(), in, Files.size(image), "image/jpeg"); }
            }
            tx.executeWithoutResult(status -> {
                Panorama p = panoramas.findLockedById(candidate.getPanoramaId()).orElse(null);
                if (p == null || !candidate.getId().equals(p.getCandidateContentId()) || p.getProcessingStatus() != PanoramaProcessingStatus.PROCESSING) { retire(candidate.getId()); return; }
                PanoramaContent c = contents.findById(candidate.getId()).orElseThrow(); c.setMetadataJson(json.writeValueAsString(metadata));
                retire(p.getCurrentContentId()); p.setCurrentContentId(c.getId()); p.setCandidateContentId(null);
                p.setContentVersion(p.getContentVersion() + 1); p.setProcessingStatus(PanoramaProcessingStatus.READY); p.setProcessingError(null);
                applyAutomatic(p, metadata);
            });
        } catch (Exception e) {
            log.warn("Panorama processing failed for {}", candidate.getId(), e);
            tx.executeWithoutResult(status -> panoramas.findLockedById(candidate.getPanoramaId()).ifPresent(p -> {
                if (candidate.getId().equals(p.getCandidateContentId())) {
                    p.setProcessingStatus(PanoramaProcessingStatus.FAILED);
                    p.setProcessingError(e instanceof CodedProblemException coded ? coded.getMessage() : p.getCurrentContentId() == null ? "全景处理失败，请重试或重新上传成品" : "全景处理失败，请重试或替换成品；原有成品仍可使用");
                }
            }));
        } finally { cleanWork(work); }
    }
    public synchronized void cleanup() {
        if (storageProvider.getIfAvailable() == null) return;
        for (var candidate : contents.findTop20ByCleanupPendingTrueAndCleanupAfterBeforeOrderByCleanupAfterAsc(Instant.now())) {
            if (uploading.contains(candidate.getClientRequestId())) continue;
            if (panoramas.existsByCurrentContentIdOrCandidateContentId(candidate.getId(), candidate.getId())) continue;
            try {
                storage().deletePrefix(candidate.getObjectPrefix());
                tx.executeWithoutResult(status -> contents.deleteById(candidate.getId()));
            } catch (RuntimeException e) {
                log.warn("Panorama cleanup failed for {}", candidate.getId(), e);
                tx.executeWithoutResult(status -> contents.findById(candidate.getId()).ifPresent(c -> c.setCleanupAfter(Instant.now().plusSeconds(60))));
            }
        }
    }
    private void retire(UUID id) {
        if (id != null) contents.findById(id).ifPresent(c -> { c.setCleanupPending(true); c.setCleanupAfter(Instant.now().plusSeconds(60)); });
    }
    private void applyAutomatic(Panorama p, PanoramaMetadataResponse metadata) {
        if (p.getTimeMode() == PanoramaValueMode.AUTO) { p.setCaptureTime(metadata == null ? null : metadata.captureTime()); p.setCaptureOffset(metadata == null ? null : metadata.captureOffset()); }
        if (p.getLocationMode() == PanoramaValueMode.AUTO) { p.setLatitude(metadata == null ? null : metadata.latitude()); p.setLongitude(metadata == null ? null : metadata.longitude()); }
    }
    private PanoramaMetadataResponse metadata(UUID id) {
        return id == null ? null : contents.findById(id).map(c -> c.getMetadataJson() == null ? null : json.readValue(c.getMetadataJson(), PanoramaMetadataResponse.class)).orElse(null);
    }
    private PanoramaResponse response(Panorama p) {
        return new PanoramaResponse(p.getId(), p.getName(), p.getDescription(), p.getDirectoryId(), p.getCaptureTime(), p.getCaptureOffset(), p.getLatitude(), p.getLongitude(),
                p.getTimeMode(), p.getLocationMode(), p.getContentVersion(), p.getProcessingStatus(), p.getProcessingError(), content(p.getCurrentContentId()), content(p.getCandidateContentId()), p.getCreatedAt(), p.getUpdatedAt());
    }
    private PanoramaContentResponse content(UUID id) {
        if (id == null) return null;
        return contents.findById(id).map(c -> new PanoramaContentResponse(c.getId(), c.getOriginalFilename(), c.getByteSize(), c.getWidth(), c.getHeight(), c.getSha256(), metadata(c.getId()))).orElse(null);
    }
    private Panorama require(UUID id, boolean lock) { return (lock ? panoramas.findLockedById(id) : panoramas.findById(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "全景影像不存在")); }
    private static boolean busy(Panorama p) { return p.getProcessingStatus() == PanoramaProcessingStatus.QUEUED || p.getProcessingStatus() == PanoramaProcessingStatus.PROCESSING; }
    private static void checkVersion(Panorama p, Long expected) { if (expected == null || expected != p.getContentVersion()) throw new CodedProblemException(HttpStatus.CONFLICT, "PANORAMA_CONTENT_CHANGED", "全景内容已变化，请刷新后重试"); }
    private FileObjectStorage storage() { var storage = storageProvider.getIfAvailable(); if (storage == null) throw unavailable(); return storage; }
    private static CodedProblemException unavailable() { return new CodedProblemException(HttpStatus.SERVICE_UNAVAILABLE, "PANORAMA_STORAGE_UNAVAILABLE", "全景文件存储暂不可用，请检查存储配置或稍后重试"); }
    private static CodedProblemException conflict(String detail) { return new CodedProblemException(HttpStatus.CONFLICT, "PANORAMA_PROCESSING_CONFLICT", detail); }
    private static CodedProblemException tooLarge() { return new CodedProblemException(HttpStatus.PAYLOAD_TOO_LARGE, "PANORAMA_FILE_TOO_LARGE", "全景原图不能超过 100 MiB"); }
    private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private static String safeFilename(String raw) {
        String value = raw == null ? "panorama.jpg" : raw.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (value.isBlank() || value.length() > 255) throw PanoramaImageProcessor.invalid("文件名不能为空且不能超过 255 字符");
        return value;
    }
    private static void cleanWork(Path path) {
        if (path == null) return;
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException e) { log.warn("Panorama temporary files could not be removed", e); }
    }
    private record BinaryDescriptor(String key, String filename) {}
    public record PanoramaBinary(FileObjectStorage.FileObjectContent content, String filename) {}
}
