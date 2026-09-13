package cn.superhuang.data.scalpel.business.dsh.repository;
import cn.superhuang.data.scalpel.business.dsh.domain.DshUserBinding;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.Optional;
import java.util.UUID;
public interface DshUserBindingRepository extends SearchRepository<DshUserBinding,UUID> { Optional<DshUserBinding> findByUserId(UUID userId); }
