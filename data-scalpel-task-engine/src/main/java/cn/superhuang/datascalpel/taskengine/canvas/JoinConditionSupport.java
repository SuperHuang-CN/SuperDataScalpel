package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JoinConditionSupport {

    private JoinConditionSupport() {
    }

    static void validate(
            List<JoinCondition> conditions,
            Map<String, CanvasColumnSchema> leftColumns,
            Map<String, CanvasColumnSchema> rightColumns,
            String path,
            String displayName,
            String geometryErrorMessage,
            boolean preciseColumnPath,
            CanvasNodeIssueSink issues
    ) {
        if (conditions == null) {
            return;
        }
        Set<String> conditionKeys = new HashSet<>();
        for (int index = 0; index < conditions.size(); index++) {
            JoinCondition condition = conditions.get(index);
            String conditionPath = path + "[" + index + "]";
            if (condition == null
                    || CanvasNodeSupport.blank(condition.leftColumnName())
                    || CanvasNodeSupport.blank(condition.rightColumnName())
                    || condition.operator() != JoinOperator.EQUALS) {
                issues.error(
                        "REQUIRED_CONFIGURATION",
                        displayName + "不完整或操作符不受支持",
                        conditionPath
                );
                continue;
            }
            String key = condition.leftColumnName() + "\u0000" + condition.rightColumnName();
            if (!conditionKeys.add(key)) {
                issues.error("DUPLICATE_JOIN_CONDITION", displayName + "重复", conditionPath);
            }
            CanvasColumnSchema left = leftColumns.get(condition.leftColumnName());
            CanvasColumnSchema right = rightColumns.get(condition.rightColumnName());
            if (left == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "左表字段不存在：" + condition.leftColumnName(),
                        preciseColumnPath ? conditionPath + ".leftColumnName" : conditionPath
                );
            }
            if (right == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "右表字段不存在：" + condition.rightColumnName(),
                        preciseColumnPath ? conditionPath + ".rightColumnName" : conditionPath
                );
            }
            if ((left != null && left.fieldType() == PlatformDataType.GEOMETRY)
                    || (right != null && right.fieldType() == PlatformDataType.GEOMETRY)) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        geometryErrorMessage,
                        conditionPath
                );
            }
        }
    }
}
