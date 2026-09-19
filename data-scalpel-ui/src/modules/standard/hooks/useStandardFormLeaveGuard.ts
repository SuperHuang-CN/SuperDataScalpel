import { Modal } from 'antd';
import { useCallback, useEffect } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';

interface StandardFormLeaveGuardOptions {
  dirty: boolean;
  content: string;
  onDiscard: () => void;
}

export const useStandardFormLeaveGuard = ({
  dirty,
  content,
  onDiscard,
}: StandardFormLeaveGuardOptions) => {
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => dirty && (
      currentLocation.pathname !== nextLocation.pathname
      || currentLocation.search !== nextLocation.search
      || currentLocation.hash !== nextLocation.hash
    ),
    [dirty],
  ));

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    const confirmation = Modal.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃未保存修改？',
      content,
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => {
        onDiscard();
        blocker.proceed();
      },
      onCancel: () => blocker.reset(),
    });
    return () => confirmation.destroy();
  }, [blocker, content, onDiscard]);

  return useCallback(() => {
    if (!dirty) {
      onDiscard();
      return;
    }
    Modal.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃未保存修改？',
      content,
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: onDiscard,
    });
  }, [content, dirty, onDiscard]);
};
