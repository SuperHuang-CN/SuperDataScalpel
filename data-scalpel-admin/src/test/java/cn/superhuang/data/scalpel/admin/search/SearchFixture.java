package cn.superhuang.data.scalpel.admin.search;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "search_fixture")
public class SearchFixture {

    @Id
    private String id;
    private String name;
    private Integer priority;
    @Enumerated(EnumType.STRING)
    private SearchFixtureState state;
    private LocalDate createdDate;
    private String note;

    protected SearchFixture() {
    }

    SearchFixture(
            String id,
            String name,
            Integer priority,
            SearchFixtureState state,
            LocalDate createdDate,
            String note
    ) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.state = state;
        this.createdDate = createdDate;
        this.note = note;
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Integer getPriority() {
        return priority;
    }
}
