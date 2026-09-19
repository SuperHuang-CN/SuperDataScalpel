import { Modal, message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { TaskRunDetailDrawer, useCancelTaskRun, useForceTerminateTaskRun, useStopTaskRun, type TaskRun } from '../../task';
import { useCurrentUser } from '../../system';

export const RuntimeRunDrawer = ({ runId, onClose }: { runId: string | null; onClose: () => void }) => {
  const user = useCurrentUser(); const cancel = useCancelTaskRun(); const terminate = useForceTerminateTaskRun(); const stop = useStopTaskRun();
  const client = useQueryClient(); const [modal, modalContext] = Modal.useModal(); const [notice, noticeContext] = message.useMessage();
  const command = (run: TaskRun, force: boolean) => {
    const streaming = run.taskType === 'SPARK_STREAMING_CANVAS' || run.taskType === 'SPARK_STREAMING_JAR';
    const label = force ? '强制终止' : streaming ? '正常停止' : '取消运行';
    modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: label,
      content: `确认${label}运行“${run.id}”吗？${force ? '已写入的数据可能无法回滚。' : ''}`,
      okText: label, cancelText: '返回', okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await (force ? terminate : streaming ? stop : cancel).mutateAsync(run.id);
          await client.invalidateQueries({ queryKey: ['operations'] }); notice.success('操作已提交');
        } catch (error) { notice.error(error instanceof Error ? error.message : '操作失败'); throw error; }
      },
    });
  };
  return <>{modalContext}{noticeContext}<TaskRunDetailDrawer open={Boolean(runId)} runId={runId}
    canExecute={user.data?.permissions.includes('task.execute') ?? false} cancelLoading={cancel.isPending || stop.isPending}
    forceTerminateLoading={terminate.isPending} onClose={onClose} onCancel={run => command(run, false)} onForceTerminate={run => command(run, true)} /></>;
};
