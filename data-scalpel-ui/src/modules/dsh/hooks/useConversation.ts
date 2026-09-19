import { useEffect, useReducer } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, requestEventStream } from '../../../shared/api/http';
import { dshApi } from '../api/dsh';
import { mergeMessages, object, type BridgeEvent, type History, type Session } from '../model/types';

interface Transient { connection: string; error: string; text: string; attemptId?: string; tools: Record<string, unknown> }
type Action = { type: 'reset' } | { type: 'patch'; value: Partial<Transient> } | { type: 'delta'; text: string; attemptId: string } | { type: 'tool'; id: string; value?: unknown };
const initial: Transient = { connection: '正在连接', error: '', text: '', tools: {} };
export function conversationReducer(state: Transient, action: Action): Transient {
  if (action.type === 'reset') return initial;
  if (action.type === 'patch') return { ...state, ...action.value };
  if (action.type === 'delta') return { ...state, text: (state.attemptId === action.attemptId ? state.text : '') + action.text, attemptId: action.attemptId };
  const tools = { ...state.tools };
  if (action.value === undefined) delete tools[action.id]; else tools[action.id] = action.value;
  return { ...state, tools };
}
export const sessionKey = (user: string, id: string) => ['dsh', user, 'session', id] as const;
export const historyKey = (user: string, id: string) => ['dsh', user, 'history', id] as const;
export const listKey = (user: string) => ['dsh', user, 'list'] as const;
const terminalCodes = new Set(['DSH_AUTHORITY_UNAVAILABLE','DSH_RELOGIN_REQUIRED', 'DSH_USER_UNAVAILABLE', 'DSH_IDENTITY_EXPIRED', 'DSH_JWT_EXPIRED', 'BRIDGE_IDENTITY_EXPIRED']);
export function useConversation(user: string, id: string | undefined, open: boolean) {
  const client = useQueryClient();
  const [transient, dispatch] = useReducer(conversationReducer, initial);
  const detail = useQuery({ queryKey: sessionKey(user, id ?? ''), queryFn: () => dshApi.session(id!), enabled: open && !!id, retry: false });
  const history = useQuery({ queryKey: historyKey(user, id ?? ''), queryFn: () => dshApi.history(id!), enabled: false, retry: false });
  useEffect(() => {
    dispatch({ type: 'reset' });
    if (!open || !id) return;
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false; let retries = 0; let generation = 0;
    const sync = async () => {
      const [session, recent] = await Promise.all([dshApi.session(id), dshApi.history(id)]);
      if (stopped) return recent.historyThroughSeq;
      client.setQueryData(sessionKey(user,id), session);
      client.setQueryData<History>(historyKey(user,id), old => old && (!recent.hasMore || (old.items.at(-1)?.sourceSeq ?? -1) >= (recent.items[0]?.sourceSeq ?? Infinity)) ? {
        ...recent, items: mergeMessages(old.items, recent.items),
        hasMore: old.hasMore, nextBeforeSeq: old.nextBeforeSeq,
      } : recent);
      return recent.historyThroughSeq;
    };
    const connect = async () => {
      let seq = 0; let streamId: string | undefined; let boundary = -1; let snapshotted = false;
      const current = ++generation;
      dispatch({ type: 'patch', value: { connection: retries ? '正在重连' : '正在同步' } });
      try {
        await requestEventStream(`/v1/dsh/sessions/${id}/events`, controller.signal, async frame => {
          if (stopped || current !== generation) return;
          const event = JSON.parse(frame.data) as BridgeEvent;
          const data = object(event.data);
          if (event.type === 'connection.failed') {
            const code = String(data.code ?? 'DSH_EVENT_DISCONNECTED');
            throw new ApiError(terminalCodes.has(code) ? '登录或用户状态已失效，请重新登录' : '事件连接中断，正在重新同步', terminalCodes.has(code) ? 401 : undefined, { code });
          }
          if (event.sessionId !== id || !Number.isSafeInteger(event.eventSeq) || !event.streamId) throw new ApiError('事件格式无效，需要重新同步');
          if (streamId && streamId !== event.streamId || event.eventSeq !== seq + 1) throw new ApiError('事件存在缺口，需要重新同步');
          streamId = event.streamId; seq = event.eventSeq!;
          if (event.type === 'session.snapshot') {
            boundary = await sync();
            if (stopped || current !== generation) return;
            snapshotted = true; retries = 0;
            dispatch({ type: 'patch', value: { connection: '已连接', error: '', text: '', tools: {} } });
            return;
          }
          if (!snapshotted) throw new ApiError('缺少会话快照');
          if (event.sourceSeq !== undefined && event.sourceSeq <= boundary) return;
          if (event.type === 'assistant.started') dispatch({ type: 'patch', value: { text: '', attemptId: String(data.attemptId) } });
          if (event.type === 'assistant.delta') dispatch({ type: 'delta', text: String(data.text ?? ''), attemptId: String(data.attemptId) });
          if (event.type === 'tool.started' && event.toolCallId) dispatch({ type: 'tool', id: event.toolCallId, value: data });
          if (event.type === 'tool.completed' && event.toolCallId) dispatch({ type: 'tool', id: event.toolCallId });
          if (event.type === 'session.updated') {
            client.setQueryData<Session>(sessionKey(user,id), old => old ? { ...old, ...data } : old);
            void client.invalidateQueries({ queryKey: listKey(user) });
          }
          if (event.type === 'session.persistence-failed') throw new ApiError('会话持久化失败，正在重新核对历史');
          if (['assistant.completed','tool.completed','message.accepted','interaction.requested','interaction.resolved'].includes(event.type) || event.type.startsWith('run.')) {
            boundary = await sync();
            if (stopped || current !== generation) return;
            if (event.type === 'assistant.completed' || event.type.startsWith('run.')) dispatch({ type: 'patch', value: { text: '', ...(event.type.startsWith('run.') ? { tools: {} } : {}) } });
            void client.invalidateQueries({ queryKey: listKey(user) });
          }
        });
      } catch (error) {
        if (stopped) return;
        const fatal = error instanceof ApiError && ([401,403,404].includes(error.status ?? 0) || terminalCodes.has(error.problem?.code ?? ''));
        dispatch({ type: 'patch', value: { connection: fatal ? '连接已停止' : '连接中断', error: error instanceof Error ? error.message : '事件连接失败' } });
        if (!fatal) timer = setTimeout(() => { void connect(); }, [1000,2000,5000,10000][Math.min(retries++,3)]);
      }
    };
    void connect();
    return () => { stopped = true; generation++; controller.abort(); clearTimeout(timer); };
  }, [client, user, id, open]);
  const loadOlder = async () => {
    if (!id || !history.data?.hasMore || history.data.nextBeforeSeq === null) return;
    const older = await dshApi.history(id, history.data.nextBeforeSeq);
    client.setQueryData<History>(historyKey(user,id), old => old ? { ...old, items: mergeMessages(older.items, old.items), hasMore: older.hasMore, nextBeforeSeq: older.nextBeforeSeq } : older);
  };
  return { detail, history, transient, loadOlder };
}
