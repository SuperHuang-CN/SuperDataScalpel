package cn.superhuang.data.scalpel.business.dsh.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description = "会话历史分页。cursor 模式返回 nextBeforeSeq 和 historyThroughSeq；旧 offset 模式返回 offset 和 limit")
public record DshMessagePageResponse(
        @Schema(description = "本页消息，按原生事件序号升序") List<DshMessageResponse> items,
        @Schema(description = "是否还有消息；cursor 模式指更早消息，offset 模式指后续消息") boolean hasMore,
        @Schema(description = "游标模式下读取更早消息的 beforeSeq，无消息时为空；offset 模式缺省") Long nextBeforeSeq,
        @Schema(description = "游标模式快照涵盖的最后事件序号，空历史为 -1；offset 模式缺省") Long historyThroughSeq,
        @Schema(description = "offset 模式的起始消息下标；游标模式缺省") Integer offset,
        @Schema(description = "offset 模式的单页数量；游标模式缺省") Integer limit) {}
