import { ArrowLeftOutlined, ArrowRightOutlined, BookOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Collapse, Drawer, Empty, Input, Select, Skeleton, Space, Tag, Typography } from 'antd';
import { useState } from 'react';
import { getSdkApiDocumentation } from '../api/sdkApi';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { matchesSdkSearch, sdkMemberExample, sdkMemberGroups, sdkTypesForMode, type SdkApiType } from '../model/sdkApi';
import './sdkApi.css';

type Selection = { typeName: string; memberName?: string };
type SelectApi = (type: SdkApiType, memberName?: string) => void;

const Example = ({ value, related }: { value: string; related?: boolean }) => <div className="sdk-api-example">
  <div><Typography.Text strong>{related ? '完整用法示例 · 包含相关操作' : '实际调用示例'}</Typography.Text><Typography.Text copyable={{ text: value }}>复制示例</Typography.Text></div>
  <pre>{value}</pre>
  <Typography.Text type="secondary">示例中的变量、资源引用名和参数，请与当前任务保持一致。</Typography.Text>
</div>;

function Operations({ type, onSelect, limit }: { type: SdkApiType; onSelect: SelectApi; limit?: number }) {
  return <div className="sdk-api-operations">{sdkMemberGroups(type).slice(0, limit).map(({ name, overloads }) =>
    <button type="button" key={name} title={overloads[0].summary} onClick={() => onSelect(type, name)}>
      <span>{overloads[0].summary.split(/[，；。]/)[0] || overloads[0].summary}</span><code>{name}{overloads[0].signature.includes('(') ? '()' : ''}</code><ArrowRightOutlined aria-hidden="true" />
    </button>)}</div>;
}

function TypeOverview({ type, onSelect }: { type: SdkApiType; onSelect: SelectApi }) {
  return <section className="sdk-api-type-section">
    <div className="sdk-api-type-heading"><Typography.Text strong>{type.summary}</Typography.Text><button type="button" onClick={() => onSelect(type)}>{type.simpleName}</button></div>
    <Operations type={type} onSelect={onSelect} />
  </section>;
}

/** All capabilities, groups, operations and examples come from the deployed SDK. */
export function SdkApiDrawer({ mode, onClose }: { mode: 'BATCH' | 'STREAMING'; onClose: () => void }) {
  const [search, setSearch] = useState('');
  const [group, setGroup] = useState<string>();
  const [selected, setSelected] = useState<Selection>();
  const [signature, setSignature] = useState<string>();
  const query = useQuery({ queryKey: ['spark-jar-sdk-api'], queryFn: getSdkApiDocumentation,
    staleTime: 0, refetchOnMount: 'always', refetchInterval: 30_000, retry: false });
  const types = sdkTypesForMode(query.data?.types ?? [], mode);
  const groups = [...new Set(types.map(type => type.group))];
  const currentGroup = groups.includes(group ?? '') ? group : undefined;
  const activeType = types.find(type => type.name === selected?.typeName);
  const overloads = activeType?.members.filter(member => member.name === selected?.memberName) ?? [];
  const member = overloads.find(item => item.signature === signature) ?? [...overloads].sort((a, b) => a.parameters.length - b.parameters.length)[0];
  const searching = Boolean(search.trim());
  const matches = types.filter(type => (!currentGroup || type.group === currentGroup) && matchesSdkSearch(type, search));
  const select: SelectApi = (type, memberName) => { setSearch(''); setGroup(type.group); setSelected({ typeName: type.name, memberName }); setSignature(undefined); };
  const chooseGroup = (value?: string) => { setGroup(value); setSelected(undefined); setSignature(undefined); };
  const relatedTypes = activeType && member ? types.filter(type => type.name !== activeType.name &&
    (type.simpleName === member.returnType || member.parameters.some(parameter => parameter.type === type.simpleName))) : [];
  const example = activeType && member ? sdkMemberExample(activeType, member) : activeType?.example;
  const parentTypes = activeType?.parents.map(parent => types.find(type => type.name === parent)).filter(type => type !== undefined) ?? [];
  const showDetail = !searching && activeType && (!selected?.memberName || member);

  return <Drawer open placement="right" size={960} onClose={onClose}
    rootClassName="business-overlay business-drawer-overlay resource-workspace-overlay sdk-api-drawer"
    styles={{ body: { display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: 0 } }}
    title={<Space><BookOutlined /><div>SDK 使用指南<Typography.Text type="secondary" className="sdk-api-subtitle">按用途找操作，示例与当前 SDK 同步</Typography.Text></div></Space>}
    extra={<Button icon={<ReloadOutlined />} loading={query.isFetching} onClick={() => void query.refetch()}>刷新</Button>}
    footer={<Space wrap><Typography.Text type="secondary">{query.data ? `SDK ${query.data.version} · ${query.data.fingerprint.slice(0, 8)}` : '当前 TaskEngine 配套 SDK'}</Typography.Text><Tag>{mode === 'BATCH' ? '批处理' : '实时处理'}</Tag></Space>}>
    <div className="sdk-api-filters"><Input autoComplete="off" aria-label="搜索 SDK API" placeholder="想做什么？搜索读取、写入、参数，或方法名" prefix={<SearchOutlined />} allowClear value={search} onChange={event => { setSearch(event.target.value); setSelected(undefined); setGroup(undefined); }} /></div>
    {query.isError ? <div className="sdk-api-feedback"><InlineFeedback tone="error" label="SDK 文档读取失败" detail={query.error.message} action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} /></div>
      : query.isPending ? <div className="sdk-api-feedback"><Skeleton active paragraph={{ rows: 8 }} /></div>
        : <div className="sdk-api-layout">
          <nav aria-label="SDK 用途" className="sdk-api-nav">
            <button type="button" aria-current={!currentGroup ? 'page' : undefined} onClick={() => chooseGroup()}>能做什么<ArrowRightOutlined /></button>
            <div className="sdk-api-nav-heading">按用途查看</div>
            {groups.map(value => <button type="button" key={value} aria-current={currentGroup === value ? 'page' : undefined} onClick={() => chooseGroup(value)}>{value}<ArrowRightOutlined /></button>)}
          </nav>
          <section className="sdk-api-content" key={`${currentGroup ?? 'all'}:${selected?.typeName ?? ''}:${selected?.memberName ?? ''}`}>
            {showDetail ? <>
              <Button type="text" size="small" className="sdk-api-back" icon={<ArrowLeftOutlined />} onClick={() => setSelected(undefined)}>返回{activeType.group}</Button>
              <Typography.Title level={4}>{member?.summary ?? activeType.summary}</Typography.Title>
              <div className="sdk-api-origin">所属接口：<code>{activeType.simpleName}</code>{member?.deprecated && <Tag color="warning">已废弃</Tag>}</div>
              {activeType.note && <p className="sdk-api-note">{activeType.note}</p>}
              {member?.note && <p className="sdk-api-note">{member.note}</p>}
              {example && <Example value={example} related={Boolean(member && !member.example)} />}
              {member ? <>
                {overloads.length > 1 && <div className="sdk-api-detail-heading"><Typography.Text strong>选择重载</Typography.Text><Select aria-label="调用方式" value={member.signature} onChange={setSignature} options={overloads.map(item => ({ value: item.signature, label: item.signature }))} /></div>}
                {!example && <p className="sdk-api-note">此方法暂未提供独立调用示例。<Button type="link" size="small" onClick={() => select(activeType)}>查看所属接口用法</Button></p>}
                <Collapse className="sdk-api-definition" ghost key={member.signature} defaultActiveKey={example ? [] : ['definition']} items={[{
                  key: 'definition', label: '方法定义与参数（参考）', children: <>
                    <p className="sdk-api-note">下面是接口定义，不是可直接粘贴运行的调用代码。</p>
                    <pre className="sdk-api-signature">{member.returnType} {member.signature}</pre>
                    {member.parameters.length > 0 && <dl className="sdk-api-parameters">{member.parameters.map(parameter => <div key={parameter.name}><dt><code>{parameter.name}</code><small>{parameter.type}</small></dt><dd>{parameter.description}</dd></div>)}</dl>}
                    {member.returns && <p className="sdk-api-returns">返回：{member.returns}</p>}
                  </>,
                }]} />
                {relatedTypes.length > 0 && <div className="sdk-api-related"><Typography.Text strong>相关配置与后续操作</Typography.Text>{relatedTypes.map(type => <button type="button" key={type.name} onClick={() => select(type)}><span>{type.summary}<small>{type.simpleName}</small></span><ArrowRightOutlined /></button>)}</div>}
              </> : <Operations type={activeType} onSelect={select} />}
              {parentTypes.map(type => <Button type="link" key={type.name} onClick={() => select(type)}>另含 {type.simpleName} 的通用能力</Button>)}
            </> : searching ? <>
              <Typography.Title level={4}>搜索结果</Typography.Title>
              {matches.length ? matches.map(type => {
                const relevant = type.members.filter(item => matchesSdkSearch({ ...type, summary: '', note: '', example: '', members: [item] }, search));
                return <TypeOverview key={type.name} type={{ ...type, members: relevant.length ? relevant : type.members }} onSelect={select} />;
              }) : <Empty description="没有匹配的 API，试试其他关键词" />}
            </> : currentGroup ? <>
              <Typography.Title level={4}>{currentGroup}</Typography.Title>
              <p className="sdk-api-intro">选择具体操作，查看示例和需要填写的参数。</p>
              {matches.filter(type => type.example).map(type => <TypeOverview key={type.name} type={type} onSelect={select} />)}
              {matches.some(type => !type.example) && <Collapse ghost defaultActiveKey={matches.some(type => type.example) ? [] : ['types']} items={[{ key: 'types', label: `配置与其他操作（${matches.filter(type => !type.example).length} 类）`, children: matches.filter(type => !type.example).map(type => <TypeOverview key={type.name} type={type} onSelect={select} />) }]} />}
            </> : <>
              <Typography.Title level={4}>用 SDK 能做什么？</Typography.Title>
              <p className="sdk-api-intro">选择下面的操作，直接查看用法。这里只介绍平台 SDK，不包含 Spark 的全部 API。</p>
              {groups.length ? <div className="sdk-api-capabilities">{groups.map(value => {
                const entries = types.filter(type => type.group === value);
                const entry = entries.find(type => type.example) ?? entries[0];
                return <section key={value} className="sdk-api-capability">
                  <button type="button" className="sdk-api-capability-heading" onClick={() => chooseGroup(value)}>{value}<ArrowRightOutlined /></button>
                  <Operations type={entry} onSelect={select} limit={3} />
                  <button type="button" className="sdk-api-view-all" onClick={() => chooseGroup(value)}>查看全部操作</button>
                </section>;
              })}</div> : <Empty description="当前模式暂无 SDK API" />}
            </>}
          </section>
        </div>}
  </Drawer>;
}
