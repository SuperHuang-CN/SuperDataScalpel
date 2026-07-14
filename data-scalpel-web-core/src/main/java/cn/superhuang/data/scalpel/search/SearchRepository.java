package cn.superhuang.data.scalpel.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/** Base Repository for entities that opt into the common SearchEngine. */
@NoRepositoryBean
public interface SearchRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
}
