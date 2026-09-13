package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "执行受控 DDL 前必须通过的一项结构、数据或运行环境检查")
public record PhysicalTableChangeCheckResponse(
        @Schema(description = "检查类型，例如结构指纹、表为空、无 NULL、长度/精度范围、无重建依赖或数据库运行参数") TableChangeCheckType type,
        @Schema(description = "检查涉及的物理列名") List<String> columnNames,
        @Schema(description = "字符串长度上限；非长度检查时为空") Integer lengthLimit,
        @Schema(description = "十进制总精度上限；非精度检查时为空") Integer precisionLimit,
        @Schema(description = "十进制小数位上限；非精度检查时为空") Integer scaleLimit,
        @Schema(description = "期望的变更前物理结构指纹；非指纹检查时为空") String expectedFingerprint,
        @Schema(description = "检查目的和失败后处理说明") String description
) {
    static PhysicalTableChangeCheckResponse from(TableChangeCheck check) {
        return new PhysicalTableChangeCheckResponse(
                check.type(), check.columnNames(), check.lengthLimit(), check.precisionLimit(), check.scaleLimit(),
                check.expectedFingerprint() == null ? null : check.expectedFingerprint().value(), check.description()
        );
    }
}
