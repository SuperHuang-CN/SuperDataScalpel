package cn.superhuang.data.scalpel.business.ontology.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "按对象业务唯一标识读取一个具体对象的只读请求。")
public record BusinessObjectPreviewRequest(
        @Schema(description = "对象主来源唯一标识值。整数标识也以字符串传递，服务端按字段类型解析。") @NotBlank @Size(max = 500) String objectKey
) {
}
