package cn.superhuang.data.scalpel.admin.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class BaseEntityPersistenceTests {

    @Autowired
    private BaseEntityFixtureRepository repository;

    @Test
    void generatesUuidAndInitializesTimestamps() {
        BaseEntityFixture saved = repository.saveAndFlush(new BaseEntityFixture("fixture"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
