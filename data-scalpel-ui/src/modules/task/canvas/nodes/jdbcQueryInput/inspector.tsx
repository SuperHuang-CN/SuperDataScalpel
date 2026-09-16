import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { CheckCircleOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Form, Input, Space, Tag, Typography, message } from 'antd';
import { useImperativeHandle, useState } from 'react';
import { inspectJdbcQuery, useDataSource } from '../../../../datasource';
import { MonacoSqlEditor } from '../../../../../shared/components/MonacoSqlEditor';
import { ApiError } from '../../../../../shared/api/http';
import { CanvasJdbcDataSourceSelect } from '../../components/CanvasJdbcSelectors';
import {
  CanvasNodeType,
  type JdbcQueryInputConfiguration,
} from '../../canvasTypes';
import { CanvasFieldPreview } from '../../components/common/CanvasFieldPreview';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import {
  configurationFingerprint,
  focusFirstInvalidField,
} from '../../components/CanvasInspectorUtils';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';

type JdbcQueryInputFormValues = JdbcQueryInputConfiguration;

const toConfiguration = (
  values: Partial<JdbcQueryInputFormValues>,
): JdbcQueryInputConfiguration => ({
  dataSourceId: values.dataSourceId ?? '',
  sql: values.sql ?? '',
  outputTableName: values.outputTableName ?? '',
  analyzedSqlSha256: values.analyzedSqlSha256 ?? '',
  outputColumns: values.outputColumns ?? [],
});

const JdbcQueryInputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.JdbcQueryInput>) => {
  const [form] = Form.useForm<JdbcQueryInputFormValues>();
  const [analyzing, setAnalyzing] = useState(false);
  const [analyzedSql, setAnalyzedSql] = useState(node.configuration.analyzedSqlSha256
    ? node.configuration.sql : '');
  const [analyzedDataSourceId, setAnalyzedDataSourceId] = useState(
    node.configuration.analyzedSqlSha256 ? node.configuration.dataSourceId : '',
  );
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const sql = Form.useWatch('sql', form) ?? '';
  const analyzedSqlSha256 = Form.useWatch('analyzedSqlSha256', form) ?? '';
  const outputColumns = Form.useWatch('outputColumns', form) ?? [];
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceAvailable = dataSourceQuery.data !== undefined
    ? dataSourceQuery.data.enabled
      && dataSourceQuery.data.connectionKind === 'JDBC'
      && dataSourceQuery.data.purposes.includes('SOURCE')
      && ['POSTGRESQL', 'HIGHGO', 'MYSQL', 'OPENGAUSS', 'KINGBASE'].includes(dataSourceQuery.data.type)
    : dataSourceQuery.isError ? false : undefined;
  const analysisStale = Boolean(analyzedSqlSha256)
    && (sql !== analyzedSql || dataSourceId !== analyzedDataSourceId);

  const markDirty = (values: Partial<JdbcQueryInputFormValues>) => {
    onDirtyChange(
      configurationFingerprint(toConfiguration(values))
      !== configurationFingerprint(node.configuration),
    );
  };

  const submit = (values: JdbcQueryInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && dataSourceAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '数据源不可用，或不是具有 SOURCE 用途的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓'],
          }]);

        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const analyze = async () => {
    try {
      const values = await form.validateFields(['dataSourceId', 'sql']);
      if (dataSourceAvailable !== true) {
        throw new Error('请选择可用的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓 SOURCE 数据源');
      }
      setAnalyzing(true);
      const inspection = await inspectJdbcQuery(values.dataSourceId, values.sql);
      form.setFieldsValue({
        analyzedSqlSha256: inspection.analyzedSqlSha256,
        outputColumns: inspection.columns,
      });
      setAnalyzedSql(values.sql);
      setAnalyzedDataSourceId(values.dataSourceId);
      markDirty({
        ...form.getFieldsValue(true),
        analyzedSqlSha256: inspection.analyzedSqlSha256,
        outputColumns: inspection.columns,
      });
      void message.success(`SQL 分析完成，获得 ${inspection.columns.length} 个字段`);
    } catch (error) {
      const detail = error instanceof ApiError
        ? error.message : error instanceof Error ? error.message : 'SQL 分析失败';
      void message.error(detail);
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<JdbcQueryInputFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => markDirty(values)}
      >
        <Form.Item
          name="dataSourceId"
          label="JDBC SOURCE 数据源"
          rules={[{ required: true, message: '请选择数据源' }]}
        >
          <CanvasJdbcDataSourceSelect
            purpose="SOURCE"
            allowedTypes={['POSTGRESQL', 'HIGHGO', 'MYSQL', 'OPENGAUSS', 'KINGBASE']}
            placeholder="选择 PostgreSQL/HighGo/MySQL/openGauss/人大金仓 SOURCE 数据源"
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出逻辑表名"
          rules={[{ required: true, message: '请输入输出逻辑表名' }]}
        >
          <Input maxLength={128} placeholder="例如 order_summary" />
        </Form.Item>
        <Form.Item
          name="sql"
          label="只读 SQL"
          rules={[
            { required: true, message: '请输入 SELECT 查询' },
            { max: 100_000, message: 'SQL 不能超过 100000 个字符' },
          ]}
        >
          <MonacoSqlEditor height={220} onChange={() => undefined} />
        </Form.Item>
        <Form.Item name="analyzedSqlSha256" hidden><Input /></Form.Item>
        <Form.Item name="outputColumns" hidden><Input /></Form.Item>
        <div className="canvas-query-analysis-bar">
          <Space size={8}>
            <Button
              icon={<SyncOutlined />}
              loading={analyzing}
              onClick={() => void analyze()}
            >
              分析 SQL
            </Button>
            {analyzedSqlSha256 && !analysisStale && (
              <Tag color="success" icon={<CheckCircleOutlined />}>已分析</Tag>
            )}
            {analysisStale && <Tag color="warning">分析结果已过期</Tag>}
          </Space>
          <Typography.Text type="secondary">仅读取字段结构，不返回数据行</Typography.Text>
        </div>
        {analysisStale && (
          <Alert
            showIcon
            type="warning"
            title="SQL 或数据源已修改"
            description="上一次字段快照已保留；重新分析前 Compiler 会将节点标记为无效。"
          />
        )}
        <div className="canvas-inspector-section-title">
          查询结果字段 <Tag>{outputColumns.length}</Tag>
        </div>
        <CanvasFieldPreview columns={outputColumns} loading={analyzing} />
      </Form>
    </Space>
  );
};

export default JdbcQueryInputInspector;
