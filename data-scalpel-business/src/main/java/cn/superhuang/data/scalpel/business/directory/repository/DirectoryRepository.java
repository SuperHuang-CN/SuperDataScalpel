package cn.superhuang.data.scalpel.business.directory.repository;

import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DirectoryRepository extends JpaRepository<Directory, UUID> {

    List<Directory> findAllByScopeOrderBySortOrderAscNameAsc(DirectoryScope scope);

    boolean existsByParentId(UUID parentId);

}
