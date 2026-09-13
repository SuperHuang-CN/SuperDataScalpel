package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "一次文件上传创建的文件、立即可识别逻辑表和异步作业。")

public record FileDatasetUploadResponse(
        @Schema(description = "本次上传创建的物理文件记录及其准备状态。")
        List<FileDatasetFileResponse> files,
        @Schema(description = "本次上传已立即识别或创建的数据表；需异步准备的文件可能暂时没有表。")
        List<FileDatasetTableResponse> tables,
        @Schema(description = "本次上传提交的异步准备或解析作业 UUID；调用方可逐项查询执行状态。")
        List<UUID> jobIds
) {
    public FileDatasetUploadResponse {
        files = List.copyOf(files);
        tables = List.copyOf(tables);
        jobIds = List.copyOf(jobIds);
    }
}
