package cn.superhuang.data.scalpel.business.directory.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryExcelFile;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryExcelService;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.directory.web.request.CreateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.request.UpdateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryImportResultResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryTreeNodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories")
@Tag(name = "目录管理")
public class DirectoryResource {

    private final DirectoryService service;
    private final DirectoryExcelService excelService;

    public DirectoryResource(DirectoryService service, DirectoryExcelService excelService) {
        this.service = service;
        this.excelService = excelService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询目录树")
    @GetMapping
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "查询目录树", description = "返回指定业务范围从顶级目录开始的递归目录树，以及每个节点的直属和子树资源数量；同级按 sortOrder、name 排序。资源计数包含该范围内所有生命周期状态，只读取目录和业务实体计数，不返回资源详情，也不构成权限过滤。")
    public List<DirectoryTreeNodeResponse> tree(
            @Parameter(description = "要查询的业务目录范围") @RequestParam DirectoryScope scope
    ) {
        return service.tree(scope);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载目录导入模板")
    @GetMapping("/actions/download-import-template")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "下载目录导入模板", description = "下载格式版本 1 的空白 .xlsx 模板，包含说明和目录工作表；业务 scope 在正式导入时通过查询参数指定，不写入模板。该二进制响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        return excelFile(excelService.template());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "导出指定范围的完整目录树")
    @GetMapping("/actions/export")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "导出指定范围的完整目录树", description = "将指定业务范围的完整目录树按父目录在前的顺序导出为格式版本 1 的 .xlsx；行标识仅用于本文件内表达父子关系，每次导出会重新生成，不是目录 UUID。不改变目录；该二进制响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "要导出的业务目录范围") @RequestParam DirectoryScope scope
    ) {
        return excelFile(excelService.export(scope));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "原子校验并导入指定范围的目录树")
    @PostMapping(path = "/actions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "原子校验并导入指定范围的目录树", description = "读取格式版本 1 的系统模板，完整校验父行、循环、同级重名、长度和数量后，在一个管理库事务中合并最多 5000 行；任一行失败时整批不保存。行标识只在本文件内定位父行，不匹配目录 UUID；系统按文件父级解析出的目录和忽略大小写的名称匹配现有目录，因此不同名称会新建，不能用此接口重命名或移动现有目录。未出现在文件中的现有目录和资源保持不变。文件上传不属于系统 MCP 第一版支持范围。")
    public DirectoryImportResultResponse importDirectories(
            @Parameter(description = "要导入的业务目录范围") @RequestParam DirectoryScope scope,
            @Parameter(description = "按目录模板填写的 .xlsx 文件，最大 10 MiB、5000 条目录") @RequestPart("file") MultipartFile file
    ) {
        return excelService.importDirectories(scope, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询目录详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "查询目录详情", description = "读取单个目录的范围、父节点、名称、排序、说明和审计时间；不返回子目录或目录下业务资源。")
    public DirectoryResponse get(@Parameter(description = "目录 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增目录")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "新增目录", description = "在指定业务范围和父目录下新增分类节点；父目录必须存在且 scope 相同，同一父级下名称忽略大小写唯一。目录只保存 UUID 标量供业务资源分类，不创建权限或数据隔离规则。")
    public DirectoryResponse create(@Valid @RequestBody CreateDirectoryRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改目录")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "修改目录", description = "整体修改目录名称、父节点、排序和说明；scope 与目录 UUID 保持不变。改变父节点会把以该目录为根的整棵子树一起移动，目录内资源仍引用原 UUID；服务会阻止跨 scope、自引用、移动到自身后代和新父级同名。")
    public DirectoryResponse update(
            @Parameter(description = "目录 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateDirectoryRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除目录")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "删除目录", description = "只允许删除没有子目录且未被当前范围业务资源引用的目录，不执行级联删除。")
    public void delete(@Parameter(description = "目录 UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    private ResponseEntity<byte[]> excelFile(DirectoryExcelFile file) {
        byte[] content = file.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DirectoryExcelService.CONTENT_TYPE))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content);
    }
}
