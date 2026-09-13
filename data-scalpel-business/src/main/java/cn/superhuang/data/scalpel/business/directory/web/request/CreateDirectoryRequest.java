package cn.superhuang.data.scalpel.business.directory.web.request;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "在一个独立业务目录树中创建顶级或子目录；目录只用于分类，不授予资源权限")
public record CreateDirectoryRequest(
        @Schema(description = "目录所属业务范围；创建后固定，不能跨范围移动") @NotNull DirectoryScope scope,
        @Schema(description = "上级目录 UUID；为空表示创建顶级目录，非空时必须指向同一 scope 的现有目录") UUID parentId,
        @Schema(description = "目录显示名称，最长 100 字符；保存时去除首尾空白，同一 scope 和 parentId 下忽略大小写唯一。当前接口允许 /，但 MODEL 和 METRIC 目录名称包含 / 时无法形成可用于 Excel 导入导出的唯一路径") @NotBlank @Size(max = 100) String name,
        @Schema(description = "同级显示排序值；数值越小越靠前，数值相同时再按名称排序。JSON 省略该原始数值字段时为 0") int sortOrder,
        @Schema(description = "目录用途或内容说明，最长 500 字符；为空或仅含空白时保存为 null") @Size(max = 500) String description
) {
}
