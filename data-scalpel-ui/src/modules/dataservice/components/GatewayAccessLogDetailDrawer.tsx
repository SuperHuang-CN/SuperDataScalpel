import { Descriptions, Drawer, Space, Tag, Typography } from 'antd';
import type { DescriptionsProps } from 'antd';
import type { GatewayAccessLog } from '../model/gatewayAccess';
import {
  gatewayAccessIdentityStatusLabels,
} from '../model/gatewayAccess';
import { gatewayProviderLabels } from '../model/apiConsumer';

interface GatewayAccessLogDetailDrawerProps {
  log: GatewayAccessLog | null;
  open: boolean;
  onClose: () => void;
}

const emptyValue = (value: string | number | null | undefined) => value ?? '—';

const formatDateTime = (value: string | null) => value
  ? new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZoneName: 'short',
  }).format(new Date(value))
  : '—';

const latency = (value: number | null) => value === null ? '—' : `${value} ms`;
const bytes = (value: number | null) => value === null
  ? '—'
  : `${value.toLocaleString('zh-CN')} B`;

export const GatewayAccessLogDetailDrawer = ({
  log,
  open,
  onClose,
}: GatewayAccessLogDetailDrawerProps) => {
  const items: DescriptionsProps['items'] = log ? [
    {
      key: 'occurredAt',
      label: '发生时间',
      children: formatDateTime(log.occurredAt),
    },
    {
      key: 'receivedAt',
      label: '接收时间',
      children: formatDateTime(log.receivedAt),
    },
    {
      key: 'requestId',
      label: 'Request ID',
      span: 2,
      children: <Typography.Text copyable>{log.gatewayRequestId}</Typography.Text>,
    },
    {
      key: 'request',
      label: '请求',
      span: 2,
      children: (
        <Space size={6} wrap>
          <Tag>{log.requestMethod}</Tag>
          <Typography.Text code>{log.requestPath}</Typography.Text>
        </Space>
      ),
    },
    {
      key: 'status',
      label: '响应状态',
      children: <Tag color={log.responseStatus >= 500 ? 'red' : 'orange'}>{log.responseStatus}</Tag>,
    },
    {
      key: 'upstreamStatus',
      label: '上游状态',
      children: emptyValue(log.upstreamStatus),
    },
    {
      key: 'provider',
      label: '网关 Provider',
      children: gatewayProviderLabels[log.gatewayProvider],
    },
    {
      key: 'identity',
      label: '身份解析',
      children: gatewayAccessIdentityStatusLabels[log.identityResolutionStatus],
    },
    {
      key: 'service',
      label: '数据服务',
      span: 2,
      children: log.dataServiceName
        ? `${log.dataServiceName}（${log.dataServiceCode ?? log.dataServiceId}）`
        : emptyValue(log.dataServiceId ?? log.gatewayServiceName),
    },
    {
      key: 'consumer',
      label: 'API 消费者',
      span: 2,
      children: log.consumerName
        ? `${log.consumerName}（${log.consumerCode ?? log.consumerId}）`
        : emptyValue(log.consumerCode ?? log.consumerId),
    },
    {
      key: 'clientIp',
      label: '客户端 IP',
      children: emptyValue(log.clientIp),
    },
    {
      key: 'credential',
      label: 'Credential 外部 ID',
      children: emptyValue(log.gatewayCredentialExternalId),
    },
    {
      key: 'requestLatency',
      label: '请求总延迟',
      children: latency(log.requestLatencyMs),
    },
    {
      key: 'proxyLatency',
      label: 'Proxy 延迟',
      children: latency(log.proxyLatencyMs),
    },
    {
      key: 'kongLatency',
      label: 'Kong 延迟',
      children: latency(log.kongLatencyMs),
    },
    {
      key: 'receiveLatency',
      label: '日志接收延迟',
      children: latency(log.receiveLatencyMs),
    },
    {
      key: 'requestBytes',
      label: '请求流量',
      children: bytes(log.requestSizeBytes),
    },
    {
      key: 'responseBytes',
      label: '响应流量',
      children: bytes(log.responseSizeBytes),
    },
    {
      key: 'gatewayService',
      label: '网关 Service ID',
      children: emptyValue(log.gatewayServiceId),
    },
    {
      key: 'gatewayRoute',
      label: '网关 Route ID',
      children: emptyValue(log.gatewayRouteId),
    },
    {
      key: 'gatewayConsumer',
      label: '网关 Consumer ID',
      children: emptyValue(log.gatewayConsumerId),
    },
    {
      key: 'kafka',
      label: 'Kafka 坐标',
      children: `${log.kafkaTopic} / ${log.kafkaPartition} / ${log.kafkaOffset}`,
    },
  ] : [];

  return (
    <Drawer
      title="网关调用详情"
      width={720}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      {log && <Descriptions bordered size="small" column={2} items={items} />}
    </Drawer>
  );
};
