import {
  CopyOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import type {
  ScriptRequestExample,
  ScriptRequestParameter,
} from '@superhuang/super-api-studio-script-workbench';
import {
  Button,
  Empty,
  Input,
  Select,
  Space,
  Tabs,
  Tooltip,
  Typography,
} from 'antd';

interface ScriptRequestExamplesPanelProps {
  examples: ScriptRequestExample[];
  activeExampleId?: string;
  readOnly: boolean;
  onActiveExampleChange: (exampleId: string) => void;
  onChange: (examples: ScriptRequestExample[]) => void;
}

interface RequestParameterEditorProps {
  parameters: ScriptRequestParameter[];
  readOnly: boolean;
  onChange: (parameters: ScriptRequestParameter[]) => void;
}

const createId = (prefix: string) => {
  const suffix = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}-${suffix}`;
};

const uniqueExampleName = (examples: ScriptRequestExample[], preferredName: string) => {
  const names = new Set(examples.map((example) => example.name.trim()));
  if (!names.has(preferredName)) return preferredName;
  let suffix = 2;
  while (names.has(`${preferredName} ${suffix}`)) suffix += 1;
  return `${preferredName} ${suffix}`;
};

const newExample = (examples: ScriptRequestExample[], preferredName = 'Example'): ScriptRequestExample => ({
  id: createId('example'),
  name: uniqueExampleName(examples, preferredName),
  bodyText: '{\n  \n}',
  query: [],
  headers: [{ id: createId('header'), key: 'Content-Type', value: 'application/json' }],
});

const RequestParameterEditor = ({ parameters, readOnly, onChange }: RequestParameterEditorProps) => {
  const update = (id: string, patch: Partial<Pick<ScriptRequestParameter, 'key' | 'value'>>) => {
    onChange(parameters.map((parameter) => parameter.id === id ? { ...parameter, ...patch } : parameter));
  };

  return (
    <div className="data-service-script-parameter-editor">
      <div className="data-service-script-parameter-toolbar">
        <Typography.Text type="secondary">空 Key 不会提交</Typography.Text>
        <Button
          size="small"
          disabled={readOnly}
          onClick={() => onChange([...parameters, { id: createId('parameter'), key: '', value: '' }])}
        >
          添加参数
        </Button>
      </div>
      <div className="data-service-script-parameter-list">
        {parameters.length ? parameters.map((parameter) => (
          <div className="data-service-script-parameter-row" key={parameter.id}>
            <Input
              size="small"
              value={parameter.key}
              disabled={readOnly}
              placeholder="Key"
              autoComplete="off"
              onChange={(event) => update(parameter.id, { key: event.target.value })}
            />
            <Input
              size="small"
              value={parameter.value}
              disabled={readOnly}
              placeholder="Value"
              autoComplete="off"
              onChange={(event) => update(parameter.id, { value: event.target.value })}
            />
            <Button
              size="small"
              danger
              disabled={readOnly}
              aria-label={`删除参数 ${parameter.key || '未命名'}`}
              icon={<DeleteOutlined />}
              onClick={() => onChange(parameters.filter((item) => item.id !== parameter.id))}
            />
          </div>
        )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无参数" />}
      </div>
    </div>
  );
};

export const ScriptRequestExamplesPanel = ({
  examples,
  activeExampleId,
  readOnly,
  onActiveExampleChange,
  onChange,
}: ScriptRequestExamplesPanelProps) => {
  const activeExample = examples.find((example) => example.id === activeExampleId) ?? examples[0];

  const updateActiveExample = (patch: Partial<Omit<ScriptRequestExample, 'id'>>) => {
    if (!activeExample || readOnly) return;
    onChange(examples.map((example) => example.id === activeExample.id ? { ...example, ...patch } : example));
  };

  const create = () => {
    if (readOnly) return;
    const created = newExample(examples);
    onChange([...examples, created]);
    onActiveExampleChange(created.id);
  };

  const copy = () => {
    if (!activeExample || readOnly) return;
    const copied: ScriptRequestExample = {
      ...activeExample,
      id: createId('example'),
      name: uniqueExampleName(examples, `${activeExample.name.trim() || 'Example'} 副本`),
      query: activeExample.query.map((parameter) => ({ ...parameter, id: createId('query') })),
      headers: activeExample.headers.map((parameter) => ({ ...parameter, id: createId('header') })),
    };
    onChange([...examples, copied]);
    onActiveExampleChange(copied.id);
  };

  const remove = () => {
    if (!activeExample || readOnly) return;
    const remaining = examples.filter((example) => example.id !== activeExample.id);
    const nextExamples = remaining.length ? remaining : [newExample([], '默认示例')];
    onChange(nextExamples);
    onActiveExampleChange(nextExamples[0].id);
  };

  return (
    <section className="data-service-script-request-panel">
      <div className="data-service-script-request-heading">
        <div>
          <Typography.Text strong>请求示例</Typography.Text>
          <Typography.Text type="secondary">用于脚本调试</Typography.Text>
        </div>
        <Space.Compact>
          <Tooltip title="新建 Example">
            <Button size="small" disabled={readOnly} aria-label="新建请求示例" icon={<PlusOutlined />} onClick={create} />
          </Tooltip>
          <Tooltip title="复制当前 Example">
            <Button size="small" disabled={readOnly || !activeExample} aria-label="复制请求示例" icon={<CopyOutlined />} onClick={copy} />
          </Tooltip>
          <Tooltip title="删除当前 Example">
            <Button size="small" danger disabled={readOnly || !activeExample} aria-label="删除请求示例" icon={<DeleteOutlined />} onClick={remove} />
          </Tooltip>
        </Space.Compact>
      </div>
      <Select
        size="small"
        value={activeExample?.id}
        placeholder="选择 Example"
        options={examples.map((example) => ({ value: example.id, label: example.name || '未命名 Example' }))}
        onChange={onActiveExampleChange}
      />
      {activeExample ? (
        <>
          <Input
            size="small"
            value={activeExample.name}
            disabled={readOnly}
            placeholder="Example 名称"
            autoComplete="off"
            onChange={(event) => updateActiveExample({ name: event.target.value })}
          />
          <Tabs
            size="small"
            items={[
              {
                key: 'body',
                label: 'Body',
                children: (
                  <Input.TextArea
                    className="data-service-script-body-editor"
                    value={activeExample.bodyText}
                    disabled={readOnly}
                    spellCheck={false}
                    autoComplete="off"
                    onChange={(event) => updateActiveExample({ bodyText: event.target.value })}
                  />
                ),
              },
              {
                key: 'query',
                label: `Query (${activeExample.query.filter((parameter) => parameter.key.trim()).length})`,
                children: (
                  <RequestParameterEditor
                    parameters={activeExample.query}
                    readOnly={readOnly}
                    onChange={(query) => updateActiveExample({ query })}
                  />
                ),
              },
              {
                key: 'headers',
                label: `Header (${activeExample.headers.filter((parameter) => parameter.key.trim()).length})`,
                children: (
                  <RequestParameterEditor
                    parameters={activeExample.headers}
                    readOnly={readOnly}
                    onChange={(headers) => updateActiveExample({ headers })}
                  />
                ),
              },
            ]}
          />
        </>
      ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Example" />}
    </section>
  );
};
