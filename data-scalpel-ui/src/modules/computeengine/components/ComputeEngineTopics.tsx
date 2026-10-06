import { Button, Popover, Typography } from 'antd';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import type { ComputeEngine } from '../model/computeEngine';

type Props = { engine: Pick<ComputeEngine, 'commandTopic' | 'runnerEventTopic' | 'adminEventTopic'>; compact?: boolean };

/** Shared read-only view: never silently truncate the only way to inspect a channel. */
export const ComputeEngineTopics = ({ engine, compact = false }: Props) => {
  const content = <div style={{ maxWidth: 'min(600px, 80vw)' }}>
    {[
      ['任务命令 Topic', engine.commandTopic],
      ['Runner 事件 Topic', engine.runnerEventTopic],
      ['Admin 事件 Topic', engine.adminEventTopic],
    ].map(([label, value]) => <div key={label} style={{ marginBottom: 8 }}>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <div><Typography.Text copyable={{ text: value }} style={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>{value || '—'}</Typography.Text></div>
    </div>)}
    <Typography.Text type="secondary">实例共享模式下，命令与 Runner 通道由此 Dispatcher 的所有引擎共用，按引擎 ID 路由；Admin 通道由平台共用。通道在部署配置中维护。</Typography.Text>
  </div>;
  return compact ? <div>
    <Typography.Text ellipsis={{ tooltip: engine.commandTopic }} style={{ display: 'block', maxWidth: '100%', fontFamily: 'monospace' }}>{engine.commandTopic}</Typography.Text>
    <Popover content={content} title={<OverlayTitle variant="popover" title="Kafka 消息通道（只读）" />} trigger={['click']}>
      <Button type="link" size="small" style={{ padding: 0 }}>查看全部 Topic</Button>
    </Popover>
  </div> : content;
};
