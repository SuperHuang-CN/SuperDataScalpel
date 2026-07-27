package cn.superhuang.data.scalpel.business.service.consumer.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.regex.Pattern;

/** DataScalpel-owned identity for one application that calls published APIs. */
@Entity
@Table(name = "ds_api_consumer", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_api_consumer_code", columnNames = "code"
))
public class ApiConsumer extends BaseEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{1,63}");

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private long revision;

    protected ApiConsumer() {
    }

    private ApiConsumer(String code, String name, String description) {
        this.code = normalizeCode(code);
        this.name = required(name, "消费者名称");
        this.description = optional(description);
        this.revision = 1;
    }

    public static ApiConsumer create(String code, String name, String description) {
        return new ApiConsumer(code, name, description);
    }

    public void update(String name, String description) {
        this.name = required(name, "消费者名称");
        this.description = optional(description);
        this.revision++;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getRevision() {
        return revision;
    }

    public static String normalizeCode(String value) {
        String normalized = required(value, "消费者编码").toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("消费者编码必须以小写字母开头，只能包含小写字母、数字、点、下划线和连字符，长度为 2 到 64 位");
        }
        return normalized;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
