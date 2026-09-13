package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TablePreview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据库表的受限样例数据，最多返回接口允许的预览行数")
public record TablePreviewResponse(
        @Schema(description = "被预览表的限定标识") TableIdentifierResponse table,
        @Schema(description = "预览列定义；每行值的元素位置与该列表顺序对齐。") List<PreviewColumnResponse> columns,
        @Schema(description = "样例数据行；每行元素位置与 columns 中的列位置一一对应。时间值转为字符串，二进制值以 Base64 返回且最多读取 512 字节，其他文本最多保留 2000 个字符；截断值追加省略号") List<List<Object>> rows,
        @Schema(description = "本次请求实际采用的行数上限，最大 100") int limit,
        @Schema(description = "是否还存在未返回的数据；true 表示 rows 只是样本，不能据此推断完整数据量。") boolean truncated
) {
    public static TablePreviewResponse from(TablePreview preview) {
        return new TablePreviewResponse(
                TableIdentifierResponse.from(preview.table()),
                preview.columns().stream().map(PreviewColumnResponse::from).toList(),
                preview.rows(),
                preview.limit(),
                preview.truncated()
        );
    }
}
