package cn.superhuang.data.scalpel.admin.persistence;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "base_entity_fixture")
public class BaseEntityFixture extends BaseEntity {

    @Column(nullable = false)
    private String name;

    protected BaseEntityFixture() {
    }

    BaseEntityFixture(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
