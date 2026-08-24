package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.JdbcInputReadOption;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Authoritative validation for per-table Spark JDBC read options. */
final class JdbcInputReadOptionPolicy {
    static final int MAX_OPTIONS = 32;
    static final int MAX_VALUE_LENGTH = 4_096;

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(?i).*(password|passwd|pwd|secret|token|credential|api[-_.]?key|access[-_.]?key|"
                    + "secret[-_.]?key|private[-_.]?key).*"
    );
    private static final Set<String> RESERVED_OPTIONS = Set.of(
            "url", "driver", "user", "password", "dbtable", "query", "preparequery", "customschema",
            "keytab", "principal", "refreshkrb5config", "connectionprovider",
            "catalog", "schema", "currentschema", "database", "databasename",
            "truncate", "cascadetruncate", "createtableoptions", "createtablecolumntypes", "batchsize",
            "isolationlevel", "tablecomment",
            "partitioncolumn", "lowerbound", "upperbound", "numpartitions"
    );
    private static final Set<String> BOOLEAN_OPTIONS = Set.of(
            "pushdownpredicate", "pushdownaggregate", "pushdownlimit", "pushdownoffset",
            "pushdowntablesample", "pushdownjoin", "prefertimestampntz"
    );
    private static final Set<String> NON_NEGATIVE_INTEGER_OPTIONS = Set.of("fetchsize", "querytimeout");

    private JdbcInputReadOptionPolicy() {
    }

    static void validate(
            List<JdbcInputReadOption> options,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (options == null) return;
        if (options.size() > MAX_OPTIONS) {
            issues.error(
                    "JDBC_READ_OPTION_COUNT_EXCEEDED",
                    "每张物理表最多配置 " + MAX_OPTIONS + " 个读取参数",
                    path
            );
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            JdbcInputReadOption option = options.get(index);
            String optionPath = path + "[" + index + "]";
            if (option == null) {
                issues.error("JDBC_READ_OPTION_NAME_INVALID", "读取参数不能为空", optionPath);
                continue;
            }
            String name = option.name();
            if (name == null || !NAME_PATTERN.matcher(name).matches()) {
                issues.error(
                        "JDBC_READ_OPTION_NAME_INVALID",
                        "读取参数名必须匹配 " + NAME_PATTERN.pattern(),
                        optionPath + ".name"
                );
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (!names.add(normalizedName)) {
                issues.error(
                        "JDBC_READ_OPTION_DUPLICATE",
                        "读取参数名不能重复（忽略大小写）：" + name,
                        optionPath + ".name"
                );
            }
            if (RESERVED_OPTIONS.contains(normalizedName) || SENSITIVE_NAME.matcher(name).matches()) {
                issues.error(
                        "JDBC_READ_OPTION_RESERVED",
                        "读取参数由平台控制或属于敏感连接信息：" + name,
                        optionPath + ".name"
                );
            }
            String value = option.value();
            if (value == null) {
                issues.error("JDBC_READ_OPTION_VALUE_INVALID", "读取参数值不能为空", optionPath + ".value");
                continue;
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                issues.error(
                        "JDBC_READ_OPTION_VALUE_TOO_LONG",
                        "读取参数值不能超过 " + MAX_VALUE_LENGTH + " 个字符",
                        optionPath + ".value"
                );
            }
            if ("sessioninitstatement".equals(normalizedName) && value.isBlank()) {
                issues.error(
                        "JDBC_READ_OPTION_VALUE_INVALID",
                        "sessionInitStatement 不能为空",
                        optionPath + ".value"
                );
            }
            if (BOOLEAN_OPTIONS.contains(normalizedName)
                    && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                issues.error(
                        "JDBC_READ_OPTION_VALUE_INVALID",
                        name + " 只能是 true 或 false",
                        optionPath + ".value"
                );
            }
            if (NON_NEGATIVE_INTEGER_OPTIONS.contains(normalizedName) && !nonNegativeInteger(value)) {
                issues.error(
                        "JDBC_READ_OPTION_VALUE_INVALID",
                        name + " 必须是非负整数",
                        optionPath + ".value"
                );
            }
        }
    }

    private static boolean nonNegativeInteger(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
