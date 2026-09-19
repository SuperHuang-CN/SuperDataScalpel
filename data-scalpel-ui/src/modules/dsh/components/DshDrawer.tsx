import { createUuid } from '../../../shared/browser/createUuid';
import { ArrowLeftOutlined, ExpandOutlined, HistoryOutlined, PaperClipOutlined, PlusOutlined, RobotOutlined, ShrinkOutlined, MoreOutlined, SendOutlined, StopOutlined } from '@ant-design/icons';
import { Button, Drawer, Dropdown, Empty, Form, Input, Modal, Segmented, Space, Spin, Tag, Tooltip, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { dshApi } from '../api/dsh';
import { historyKey, listKey, sessionKey, useConversation } from '../hooks/useConversation';
import { busy, stateLabel, type Session } from '../model/types';
import { Messages } from './Messages';
import { Questions } from './Questions';
import { AttachmentDrafts } from './Attachments';
import { useAttachmentDrafts } from '../hooks/useAttachmentDrafts';
import './dsh.css';

const COMPACT_ASSISTANT_QUERY = '(max-width: 720px)';
const layoutStorageKey = (user: string) => `data-scalpel.dsh.drawer-layout.${user}`;

export default function DshDrawer({ user, open, onClose }: { user: string; open: boolean; onClose: () => void }) {
  const client = useQueryClient(); const [notice, noticeContext] = message.useMessage(); const [modal, modalContext] = Modal.useModal();
  const [wide, setWide] = useState(() => window.localStorage.getItem(layoutStorageKey(user)) === 'wide');
  const [compact, setCompact] = useState(() => window.matchMedia(COMPACT_ASSISTANT_QUERY).matches);
  const [listing, setListing] = useState(true);
  const [selected, setSelected] = useState<string>(); const [archived, setArchived] = useState(false);
  const [queryDraft, setQueryDraft] = useState(''); const [query, setQuery] = useState(''); const [offset, setOffset] = useState(0);
  const [drafts, setDrafts] = useState<Record<string,string>>({});
  const [command, setCommand] = useState(''); const commandLock = useRef(false); const [error, setError] = useState('');
  const [rename, setRename] = useState<Session>(); const [renameForm] = Form.useForm<{title:string}>();
  const creation = useRef<string | undefined>(undefined); const receipts = useRef<Record<string, {id:string; text:string; attachmentIds:string[]}>>({});
  const fileInput = useRef<HTMLInputElement>(null); const [dragging, setDragging] = useState(false);
  const capabilities = useQuery({ queryKey:['dsh',user,'capabilities'], queryFn:dshApi.capabilities, enabled:open, retry:false, refetchOnWindowFocus:true, staleTime:0 });
  const attachmentDrafts = useAttachmentDrafts(capabilities.data?.attachments);
  const attachments = selected ? attachmentDrafts.drafts[selected] ?? [] : [];
  const attachmentsReady = attachments.every(a => a.state === 'ready');
  const imageUnsupported = capabilities.data?.attachments?.imageSupported === false && attachments.some(a => a.preview);
  const addFiles = (files: File[]) => {
    if (!selected || !!command || !files.length) return;
    const failure = attachmentDrafts.add(selected, files); if (failure) void notice.warning(failure);
  };
  const ready = capabilities.data?.ready === true && capabilities.data.enabled;
  const workspace = useQuery({ queryKey:['dsh',user,'workspace'], queryFn:dshApi.ensure, enabled:open && ready, retry:false });
  const enabled = open && ready && workspace.isSuccess;
  const sessions = useQuery({ queryKey:[...listKey(user),query,archived,offset], queryFn:() => dshApi.list(query,archived,offset),
    enabled, retry:false, refetchInterval:enabled ? 15000 : false, refetchOnWindowFocus:true, staleTime:0 });
  const conversation = useConversation(user, selected, enabled && !!selected);
  const session = conversation.detail.data;
  const mutation = async (name: string, fn: () => Promise<void>) => {
    if (commandLock.current) return;
    commandLock.current = true; setCommand(name); setError('');
    try { await fn(); }
    catch (e) {
      const uncertain = e instanceof ApiError && (!e.status || e.problem?.code?.includes('UNKNOWN') || e.problem?.code?.includes('UNCERTAIN'));
      setError(uncertain ? '结果待确认，请核对会话状态和历史；再次提交将沿用原消息标识。' : e instanceof Error ? e.message : '操作失败');
      if (selected) { void client.invalidateQueries({queryKey:sessionKey(user,selected)}); void client.invalidateQueries({queryKey:historyKey(user,selected)}); }
      throw e;
    } finally { commandLock.current = false; setCommand(''); }
  };
  const run = (name: string, fn: () => Promise<void>) => { void mutation(name,fn).catch(() => undefined); };
  const saveSession = (value: Session) => {
    client.setQueryData(sessionKey(user,value.sessionId),value); void client.invalidateQueries({queryKey:listKey(user)});
  };
  const choose = (value: Session) => { setSelected(value.sessionId); saveSession(value); setListing(false); setError(''); };
  const create = () => run('create', async () => {
    creation.current ??= createUuid(); const value = await dshApi.create(creation.current);
    creation.current = undefined; choose(value); setArchived(false); setOffset(0);
  });
  const changeArchive = (value: Session) => {
    if (value.archived) { run(value.sessionId,async () => { saveSession(await dshApi.archive(value.sessionId,false)); void notice.success('会话已恢复'); }); return; }
    if (busy(value)) {
      modal.confirm({title:`停止“${value.title}”的执行？`,content:'停止完成后，可以再次归档此会话。',okText:'停止执行',cancelText:'取消',
        onOk:() => mutation(value.sessionId,async () => { saveSession(await dshApi.cancel(value.sessionId)); })}); return;
    }
    modal.confirm({ title:`归档“${value.title}”？`,content:'会话历史和工作文件会保留，可以在“已归档”中查看并恢复。',okText:'归档',cancelText:'取消',
      onOk:() => mutation(value.sessionId,async () => { saveSession(await dshApi.archive(value.sessionId,true)); void notice.success('会话已归档'); }) });
  };
  const actions = (value: Session) => <Dropdown menu={{items:[{key:'rename',label:'重命名'},{key:'archive',label:value.archived ? '恢复会话' : '归档会话'}],onClick:({key}) => {
    if (key === 'rename') { setRename(value); renameForm.setFieldsValue({title:value.title}); } else changeArchive(value);
  }}} trigger={['click']}><Button type="text" icon={<MoreOutlined />} aria-label={`管理会话：${value.title}`} disabled={!!command} loading={command === value.sessionId} /></Dropdown>;
  // Remount only on user change in AppShell; route/visibility changes preserve drafts and selection.
  useEffect(() => () => { client.removeQueries({queryKey:['dsh',user]}); }, [client,user]);
  useEffect(() => { window.localStorage.setItem(layoutStorageKey(user), wide ? 'wide' : 'narrow'); }, [user,wide]);
  useEffect(() => {
    const media = window.matchMedia(COMPACT_ASSISTANT_QUERY);
    const change = () => setCompact(media.matches);
    change(); media.addEventListener('change',change);
    return () => media.removeEventListener('change',change);
  }, []);
  const send = () => {
    if (!selected || busy(session) || session?.archived || !attachmentsReady || imageUnsupported || (!drafts[selected]?.trim() && !attachments.length)) return;
    const id = selected; const text = drafts[id]?.trim() ?? '';
    const attachmentIds = attachments.flatMap(a => a.attachment ? [a.attachment.id] : []);
    run('send',async () => {
      const previous = receipts.current[id];
      if (previous && (previous.text !== text || JSON.stringify(previous.attachmentIds) !== JSON.stringify(attachmentIds))) throw new ApiError('上一条消息结果尚未确认，请先核对历史，再使用原文和原附件重试或确认后开始新消息。',409);
      const receipt = previous ?? {id:createUuid(),text,attachmentIds}; receipts.current[id] = receipt;
      try { await dshApi.send(id,receipt.id,receipt.text,receipt.attachmentIds); }
      catch (failure) {
        if (failure instanceof ApiError && failure.status && !/UNKNOWN|UNCERTAIN/.test(failure.problem?.code ?? '')) delete receipts.current[id];
        throw failure;
      }
      delete receipts.current[id];
      attachmentDrafts.clearSent(id,receipt.attachmentIds);
      setDrafts(old => ({...old,[id]:old[id]?.trim() === text ? '' : old[id]}));
      await Promise.all([client.invalidateQueries({queryKey:sessionKey(user,id)}),client.invalidateQueries({queryKey:historyKey(user,id)}),client.invalidateQueries({queryKey:listKey(user)})]);
    });
  };
  useEffect(() => {
    if (!selected) return;
    const receipt = receipts.current[selected];
    if (receipt && conversation.history.data?.items.some(m => m.id === receipt.id)) {
      delete receipts.current[selected];
      attachmentDrafts.clearSent(selected,receipt.attachmentIds);
      setDrafts(old => old[selected]?.trim() === receipt.text ? { ...old, [selected]: '' } : old);
    }
  }, [selected, conversation.history.data, attachmentDrafts]);
  const unavailable = capabilities.error ?? workspace.error;
  const text = selected ? drafts[selected] ?? '' : '';
  const wideLayout = wide && !compact;
  const sessionPanel = <aside className={`dsh-session-pane ${wideLayout ? 'is-persistent' : 'is-overlay'}`} aria-label="会话管理">
    <div className="dsh-session-pane-header"><strong>会话</strong>
      {!wideLayout && <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => setListing(false)}>返回对话</Button>}
    </div>
    <div className="dsh-session-filters"><Segmented block options={[{label:'进行中的会话',value:false},{label:'已归档',value:true}]} value={archived} onChange={v => {setArchived(Boolean(v));setOffset(0);}} />
      <form autoComplete="off" onSubmit={e => {e.preventDefault();setQuery(queryDraft.trim());setOffset(0);}}><Input aria-label="搜索会话标题" value={queryDraft} onChange={e=>setQueryDraft(e.target.value)} allowClear maxLength={100} placeholder="搜索会话标题" /><Button htmlType="submit">查询</Button></form></div>
    {sessions.isFetching && <Spin size="small" />}
    {sessions.error && <InlineFeedback tone="error" label="会话列表加载失败" detail={sessions.error.message} action={<Button onClick={()=>void sessions.refetch()}>重试</Button>} />}
    <div className="dsh-session-list">{sessions.data?.items.map(value=><div className={`dsh-session-row ${value.sessionId === selected ? 'is-selected' : ''}`} key={value.sessionId}>
      <button className="dsh-session-select" onClick={()=>choose(value)} aria-current={value.sessionId === selected ? 'true' : undefined}><strong>{value.title}</strong><span>{stateLabel(value)} · {formatManagementDateTime(value.lastActivityAt)}</span></button>{actions(value)}</div>)}
      {sessions.data?.items.length===0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={archived?'暂无归档会话':'暂无会话'}>{!archived && <Button type="primary" onClick={create} disabled={!!command}>新建会话</Button>}</Empty>}</div>
    <div className="dsh-list-footer"><Button disabled={!offset || sessions.isFetching} onClick={()=>setOffset(v=>Math.max(0,v-20))}>上一页</Button><span>第 {offset/20+1} 页</span><Button disabled={!sessions.data?.hasMore || sessions.isFetching} onClick={()=>setOffset(v=>v+20)}>下一页</Button></div>
  </aside>;
  return <>{noticeContext}{modalContext}<Drawer rootClassName="dsh-drawer" className="dsh-panel" open={open} onClose={onClose}
    title={<Space><RobotOutlined /><span>AI 助手</span></Space>} mask={false} push={false} autoFocus={false} zIndex={900}
    size={compact ? '100vw' : wide ? '80vw' : 'max(520px, 40vw)'}
    extra={<Space><Tooltip title="新建会话"><Button type="text" icon={<PlusOutlined />} aria-label="新建会话" onClick={create} loading={command==='create'} disabled={!enabled || !!command} /></Tooltip>
      <Tooltip title={wide?'收窄助手':'展开助手'}><Button className="dsh-layout-toggle" type="text" icon={wide?<ShrinkOutlined />:<ExpandOutlined />} aria-label={wide?'收窄助手':'展开助手'} onClick={() => setWide(value => !value)} /></Tooltip></Space>}>
    {!enabled ? <div className="dsh-unavailable">
      {(capabilities.isFetching || ready && workspace.isFetching) && <Spin tip="正在连接助手" />}
      {unavailable && <InlineFeedback tone="error" label="助手暂不可用" detail={unavailable.message} action={<Button onClick={() => {void capabilities.refetch();void workspace.refetch();}}>重试</Button>} />}
      {capabilities.data && !ready && <InlineFeedback tone="warning" label={capabilities.data.enabled?'助手依赖尚未就绪':'AI 助手尚未启用'} detail={capabilities.data.code ?? '请联系管理员检查 DSH 配置'} action={<Button onClick={() => void capabilities.refetch()}>刷新状态</Button>} />}
    </div> : <>
      {error && <InlineFeedback className="dsh-feedback" tone="error" label={error} action={<Button type="link" onClick={() => setError('')}>收起</Button>} />}
      <div className={`dsh-workspace ${wideLayout ? 'is-wide' : 'is-narrow'}`}>
        {!wideLayout && listing && <button className="dsh-session-backdrop" aria-label="关闭会话列表" onClick={() => setListing(false)} />}
        {(wideLayout || listing) && sessionPanel}
        <section className="dsh-conversation-pane" aria-label="AI 助手对话">
          <div className="dsh-toolbar"><Space>{!wideLayout && <Button type="text" icon={<HistoryOutlined />} onClick={() => setListing(true)}>会话列表</Button>}
            {session && <Tag>{stateLabel(session)}</Tag>}</Space>{session && actions(session)}</div>
          {!selected ? <div className="dsh-conversation-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择一个会话，或者开始新的对话"><Button type="primary" icon={<PlusOutlined />} onClick={create} disabled={!!command}>新建会话</Button></Empty></div> : <>
          <div className="dsh-conversation-title"><strong>{session?.title ?? '对话'}</strong><small>{conversation.transient.connection}</small></div>
        {(conversation.detail.error || conversation.transient.error) && <InlineFeedback className="dsh-feedback" tone="error" label={conversation.detail.error?.message ?? conversation.transient.error} />}
        {!conversation.history.data && !conversation.transient.error && <Spin size="small" />}
        <Messages key={selected} user={user} sessionId={selected} items={conversation.history.data?.items ?? []} text={conversation.transient.text} tools={conversation.transient.tools} running={busy(session)} older={conversation.history.data?.hasMore ?? false} loadOlder={conversation.loadOlder}>
          {session?.pendingInteractions.map(interaction=><Questions key={interaction.interactionId} interaction={interaction} disabled={!!command || session.archived} submit={answers=>mutation('answer',async()=>{
            await dshApi.answer(session.sessionId,interaction.interactionId,answers);await client.invalidateQueries({queryKey:sessionKey(user,session.sessionId)});
          })} />)}
          {session?.lastOutcome && !busy(session) && <div className="dsh-outcome">{{COMPLETED:'本轮对话已完成',CANCELLED:'执行已停止',FAILED:'本轮执行失败，可查看已返回的信息',INTERRUPTED:'执行曾中断，请核对历史后继续'}[session.lastOutcome]}</div>}
        </Messages>
        <div className={`dsh-composer ${dragging ? 'is-dragging' : ''}`} onDragOver={e => {
          if (e.dataTransfer.types.includes('Files') && !session?.archived) { e.preventDefault(); setDragging(true); }
        }} onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false); }} onDrop={e => {
          e.preventDefault(); setDragging(false); if (!session?.archived) addFiles(Array.from(e.dataTransfer.files));
        }}>{session?.archived ? <Space><span>此会话已归档</span><Button onClick={()=>changeArchive(session)} disabled={!!command}>恢复会话</Button></Space> : <>
          {attachments.length > 0 && <AttachmentDrafts items={attachments} disabled={!!command}
            remove={key => selected && attachmentDrafts.remove(selected,key)} retry={draft => selected && attachmentDrafts.retry(selected,draft)} />}
          {imageUnsupported && <InlineFeedback tone="warning" label="当前模型不支持图片" detail="请由管理员配置支持视觉的模型后再发送截图；其他附件可以正常使用。" />}
          <input ref={fileInput} type="file" hidden multiple accept={capabilities.data?.attachments?.extensions.join(',')} onChange={e => { addFiles(Array.from(e.target.files ?? [])); e.target.value = ''; }} />
          <Input.TextArea aria-label="发送给 AI 助手的消息" autoComplete="off" value={text} placeholder="输入你的问题，Enter 发送，Shift+Enter 换行" autoSize={{minRows:3,maxRows:8}}
            onPaste={e => { if (e.clipboardData.files.length) { e.preventDefault(); addFiles(Array.from(e.clipboardData.files)); } }}
            onChange={e=>selected && setDrafts(old=>({...old,[selected]:e.target.value}))} onKeyDown={e=>{
              if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing&&e.keyCode!==229){e.preventDefault();send();}
            }} />
          <div className="dsh-composer-actions"><Space><Tooltip title={capabilities.data?.attachments ? '上传 Excel、文本或图片；可拖拽文件、粘贴截图。每条最多 5 个，每个最多 5 MiB。' : '附件功能需要升级 DSH 插件'}>
            <Button type="text" icon={<PaperClipOutlined />} aria-label="添加附件" disabled={!capabilities.data?.attachments || !session || !!command} onClick={() => fileInput.current?.click()} />
          </Tooltip><small>{busy(session)?'执行中可保留草稿':'可粘贴截图或拖入附件'}</small></Space>
            {busy(session)?<Button icon={<StopOutlined />} loading={command==='cancel'||session?.runtimeState==='CANCELLING'} disabled={!!command} onClick={()=>session && run('cancel',async()=>saveSession(await dshApi.cancel(session.sessionId)))}>停止执行</Button>
              :<Button type="primary" icon={<SendOutlined />} disabled={(!text.trim()&&!attachments.length)||!attachmentsReady||imageUnsupported||!session||!!command} loading={command==='send'} onClick={send}>发送</Button>}</div>
        </>}</div>
          </>}
        </section>
      </div>
    </>}
  </Drawer><Modal title="重命名会话" open={!!rename} onCancel={()=>setRename(undefined)} confirmLoading={command==='rename'} okText="保存" cancelText="取消" onOk={()=>{
    void renameForm.validateFields().then(values=>mutation('rename',async()=>{if(rename)saveSession(await dshApi.update(rename.sessionId,values.title.trim()));setRename(undefined);})).catch(()=>undefined);
  }}><Form form={renameForm} layout="vertical" autoComplete="off"><Form.Item name="title" label="会话标题" rules={[{required:true,whitespace:true,message:'请输入会话标题'},{max:100,message:'最多 100 个字符'}]}><Input maxLength={100} autoComplete="off" /></Form.Item></Form></Modal></>;
}
