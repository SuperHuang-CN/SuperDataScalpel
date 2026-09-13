package cn.superhuang.data.scalpel.business.standard.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "需要内容版本并发校验的码表命令")
public record StandardDictionaryVersionRequest(
        @Schema(description = "客户端最近读取到的码表内容版本；与服务端当前版本不一致时返回 409，避免启停或删除命令作用于过期树结构") @Min(1) int expectedVersion
) {
}
