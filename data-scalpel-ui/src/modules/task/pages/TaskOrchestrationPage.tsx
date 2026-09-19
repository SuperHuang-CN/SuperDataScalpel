import { Modal } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { CanvasDesigner } from '../canvas/CanvasDesigner';

export const TaskOrchestrationPage = () => {
  const [inspectorDirty, setInspectorDirty] = useState(false);
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => inspectorDirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [inspectorDirty],
  ));

  useEffect(() => {
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!inspectorDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [inspectorDirty]);

  useEffect(() => {
    if (!inspectorDirty && blocker.state === 'blocked') blocker.reset();
  }, [blocker, inspectorDirty]);

  return (
    <>
      <div className="task-orchestration-page">
        <CanvasDesigner onInspectorDirtyChange={setInspectorDirty} />
      </div>
      <Modal
        open={blocker.state === 'blocked'}
        title="节点配置尚未应用"
        okText="放弃并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        closable={false}
        maskClosable={false}
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        当前节点的配置尚未应用，离开页面后这些修改会丢失。
      </Modal>
    </>
  );
};
