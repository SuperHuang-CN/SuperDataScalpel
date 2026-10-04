import { UploadOutlined } from '@ant-design/icons';
import { Button, Upload } from 'antd';
import { useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useFileDatasetFiles, useUploadFileDatasetFiles } from '../hooks/useFileDatasets';
import { fileDatasetAccept, fileDatasetAllowsAdditionalUpload, fileDatasetTypeLabels, formatFileSize, type FileDataset } from '../model/fileDataset';
import './file-dataset-activity.css';

export const FileDatasetUploadControl = ({ dataset, onUploaded }: { dataset: FileDataset; onUploaded?: () => void }) => {
  const [file, setFile] = useState<File>();
  const [error, setError] = useState<string>();
  const [submittedName, setSubmittedName] = useState<string>();
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const filesQuery = useFileDatasetFiles(dataset.id, true);
  const uploadMutation = useUploadFileDatasetFiles();
  const count = filesQuery.data?.totalElements ?? dataset.fileCount;
  const limited = !fileDatasetAllowsAdditionalUpload(dataset.type, count);
  const unavailable = limited || !filesQuery.isSuccess || filesQuery.isFetching || busy;

  const upload = async () => {
    if (!file || submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setError(undefined);
    setSubmittedName(undefined);
    try {
      // Recheck immediately before upload: the list snapshot may predate another upload.
      const latest = await filesQuery.refetch();
      if (latest.isError || !latest.data) throw new Error('无法确认当前文件数量，请重试后上传。');
      if (!fileDatasetAllowsAdditionalUpload(dataset.type, latest.data.totalElements)) {
        throw new Error(`${fileDatasetTypeLabels[dataset.type]} 只允许一个物理文件，请在文件列表使用“替换文件”。`);
      }
      await uploadMutation.mutateAsync({ id: dataset.id, files: [file] });
      setSubmittedName(file.name);
      setFile(undefined);
      onUploaded?.();
    } catch (cause) {
      setError(cause instanceof ApiError || cause instanceof Error ? cause.message : '上传失败，请重试。');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  };

  return <div className="file-dataset-upload-control" aria-busy={busy}>
    <div className="file-dataset-upload-line">
      <Upload accept={fileDatasetAccept(dataset.type)} multiple={false} disabled={unavailable} showUploadList={false} beforeUpload={(selected) => { setFile(selected); setError(undefined); setSubmittedName(undefined); return Upload.LIST_IGNORE; }}>
        <Button icon={<UploadOutlined />} disabled={unavailable}>{file ? '重新选择' : '选择文件'}</Button>
      </Upload>
      {file && <><span className="file-dataset-upload-name" title={file.name}>{file.name} <small>{formatFileSize(file.size)}</small></span>
        <Button type="text" disabled={busy} onClick={() => { setFile(undefined); setError(undefined); }}>移除</Button>
        <Button type="primary" disabled={unavailable && !busy} loading={busy} onClick={() => void upload()}>上传并解析</Button></>}
      {!file && !limited && <span className="file-dataset-activity-hint">{fileDatasetTypeLabels[dataset.type]} · {fileDatasetAccept(dataset.type)}</span>}
    </div>
    {limited && <InlineFeedback tone="info" label={`${fileDatasetTypeLabels[dataset.type]} 只允许一个物理文件。更新内容请在文件列表使用“替换文件”。`} />}
    {filesQuery.isError && <InlineFeedback tone="error" label="无法读取文件数量，暂不能上传" action={<Button type="link" onClick={() => void filesQuery.refetch()}>重试</Button>} />}
    {error && <InlineFeedback tone="error" label={error} />}
    {submittedName && <InlineFeedback tone="info" label={`“${submittedName}”已提交后台解析，请在解析记录中查看最终结果。`} />}
  </div>;
};
