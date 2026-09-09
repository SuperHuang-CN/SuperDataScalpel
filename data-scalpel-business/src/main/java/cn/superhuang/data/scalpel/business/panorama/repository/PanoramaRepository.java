package cn.superhuang.data.scalpel.business.panorama.repository;

import cn.superhuang.data.scalpel.business.panorama.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PanoramaRepository extends SearchRepository<Panorama, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Panorama p where p.id = :id")
    Optional<Panorama> findLockedById(@Param("id") UUID id);
    List<Panorama> findAllByProcessingStatus(PanoramaProcessingStatus status);
    Optional<Panorama> findFirstByProcessingStatusOrderByCreatedAtAsc(PanoramaProcessingStatus status);
    Optional<Panorama> findByCreationRequestId(UUID requestId);
    boolean existsByDirectoryId(UUID id);
    boolean existsByCurrentContentIdOrCandidateContentId(UUID currentId, UUID candidateId);
    @Query("select new cn.superhuang.data.scalpel.business.panorama.repository.PanoramaRepository$DirectoryResourceCount(p.directoryId, count(p)) from Panorama p where p.directoryId in :ids group by p.directoryId")
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("ids") Collection<UUID> ids);
    record DirectoryResourceCount(UUID directoryId, long resourceCount) {}
}
