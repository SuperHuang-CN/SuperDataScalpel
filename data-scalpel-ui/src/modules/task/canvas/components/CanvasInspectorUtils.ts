import type { CanvasNodeConfiguration } from '../canvasTypes';

export const configurationFingerprint = (configuration: CanvasNodeConfiguration): string => (
  JSON.stringify(configuration)
);

export const focusFirstInvalidField = (
  form: { scrollToField: (name: (string | number)[], options?: { focus?: boolean; block?: ScrollLogicalPosition }) => void },
  error: unknown,
) => {
  if (typeof error !== 'object' || error === null || !('errorFields' in error)) return;
  const firstError = (error as { errorFields?: { name: (string | number)[] }[] }).errorFields?.[0];
  if (firstError) form.scrollToField(firstError.name, { focus: true, block: 'center' });
};
