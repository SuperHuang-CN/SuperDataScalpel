package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** One typed filter used by the model physical-table data query. */
@Schema(description = "模型物理表只读查询的一个类型化筛选条件")
public record DataModelDataQueryFilterInput(
        @Schema(description = "模型字段编码；服务端按模型白名单解析，不能传任意 SQL 或物理表达式") @NotBlank String field,
        @Schema(description = "运算符，不区分大小写：EQ、NE、GT、GE、LT、LE、IN、NOT_IN、BETWEEN、NOT_BETWEEN、LIKE、NOT_LIKE、IS_NULL、IS_NOT_NULL、IS_EMPTY 或 IS_NOT_EMPTY。LIKE 类只支持 STRING，IS_EMPTY 类也只支持 STRING。") @NotBlank String operator,
        @Schema(description = "单值运算符的唯一值，或 BETWEEN/NOT_BETWEEN 的起始值；服务端按字段类型严格转换。DATE 使用 yyyy-MM-dd，TIMESTAMP/TIMESTAMP_NTZ 使用 ISO 本地日期时间且不带时区。IS_NULL、IS_NOT_NULL、IS_EMPTY、IS_NOT_EMPTY 不接受值。") Object value,
        @Schema(description = "BETWEEN/NOT_BETWEEN 的结束值；其他运算符省略。与 value 同时提供时会组合为恰好两个边界值。") Object secondValue,
        @Schema(description = "IN/NOT_IN 的 1 至 1,000 个值，或 BETWEEN 类的两个值；不能与 value 或 secondValue 同时使用。省略规范化为空数组。") List<Object> values
) {

    public DataModelDataQueryFilterInput {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
