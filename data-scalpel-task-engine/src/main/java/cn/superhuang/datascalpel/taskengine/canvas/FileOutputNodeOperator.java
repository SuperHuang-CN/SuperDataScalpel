package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FileOutputNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof FileOutputNodeDefinition node)) {
            throw new IllegalArgumentException("FILE_OUTPUT operator received " + definition.nodeType());
        }
        FileOutputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.outputOnly();

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        validateTargetPath(configuration.targetPath(), issues);
        if (configuration.conflictPolicy() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择目录冲突策略",
                    "configuration.conflictPolicy");
        }
        validateFormat(configuration.formatOptions(), issues);

        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        MetadataIndex.DataSourceEntry dataSource =
                dataSourceId == null ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.S3
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "目标数据源不存在、未启用、不是 S3 或不具有 DISTRIBUTION 用途",
                    "configuration.dataSourceId"
            );
        }
        if (source == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        return CanvasNodeOperationResult.fileOutput(
                context.dataAccess().prepareFileOutput(node, source.dataset()));
    }

    private static void validateTargetPath(String path, CanvasNodeIssueSink issues) {
        CanvasNodeSupport.required(path, "请输入目标目录", "configuration.targetPath", issues);
        if (CanvasNodeSupport.blank(path)) {
            return;
        }
        if (path.length() > 1024
                || path.startsWith("/")
                || path.contains("\\")
                || path.contains("://")
                || path.contains("?")
                || path.contains("#")) {
            issues.error("INVALID_FILE_OUTPUT_PATH", "目标目录必须是合法的 S3 相对路径",
                    "configuration.targetPath");
            return;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || "_temporary".equalsIgnoreCase(segment)) {
                issues.error("INVALID_FILE_OUTPUT_PATH",
                        "目标目录不能包含空段、.、.. 或 _temporary",
                        "configuration.targetPath");
                return;
            }
        }
    }

    private static void validateFormat(
            FileOutputFormatOptions options,
            CanvasNodeIssueSink issues
    ) {
        if (options == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择文件格式",
                    "configuration.formatOptions");
            return;
        }
        if (options instanceof FileOutputFormatOptions.Csv csv) {
            singleCharacter(csv.delimiter(), "分隔符", "delimiter", issues);
            singleCharacter(csv.quote(), "引用符", "quote", issues);
            singleCharacter(csv.escape(), "转义符", "escape", issues);
            if (csv.nullValue() == null) {
                issues.error("REQUIRED_CONFIGURATION", "CSV 空值文本不能为空",
                        "configuration.formatOptions.nullValue");
            }
        }
    }

    private static void singleCharacter(
            String value,
            String label,
            String field,
            CanvasNodeIssueSink issues
    ) {
        if (value == null || value.codePointCount(0, value.length()) != 1
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            issues.error("INVALID_FILE_OUTPUT_FORMAT_OPTION",
                    label + "必须是一个非换行字符",
                    "configuration.formatOptions." + field);
        }
    }
}
