package cn.superhuang.data.scalpel.business.directory.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "整体修改目录名称、上级、排序和说明；目录所属业务范围保持不变。修改上级会连同全部后代一起移动，但后代的 parentId 不变")
public record UpdateDirectoryRequest(
        @Schema(description = "新的上级目录 UUID；为空表示移动到顶级，非空时必须同范围，且不能指向自身或当前目录的任一后代") UUID parentId,
        @Schema(description = "新的目录显示名称，最长 100 字符；保存时去除首尾空白，同一 scope 和 parentId 下忽略大小写唯一。当前接口允许 /，但 MODEL 和 METRIC 目录名称包含 / 时无法形成可用于 Excel 导入导出的唯一路径") @NotBlank @Size(max = 100) String name,
        @Schema(description = "新的同级显示排序值；数值越小越靠前，数值相同时再按名称排序。请求省略该原始数值字段时会保存为 0") int sortOrder,
        @Schema(description = "新的目录用途或内容说明，最长 500 字符；为空或仅含空白时清除原说明") @Size(max = 500) String description
) {
}
