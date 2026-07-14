package cn.superhuang.data.scalpel.admin.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BaseEntityFixtureRepository extends JpaRepository<BaseEntityFixture, UUID> {
}
