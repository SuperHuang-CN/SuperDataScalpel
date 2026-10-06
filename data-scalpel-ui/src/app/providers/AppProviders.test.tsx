import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StrictMode, useState } from 'react';
import { afterEach, expect, it } from 'vitest';
import { clearAccessToken, getAccessToken, saveAccessToken } from '../../shared/api/accessSession';
import { AppProviders } from './AppProviders';

afterEach(() => { clearAccessToken(); });

it('replaces account queries and local drafts across a shared session change under StrictMode', async () => {
  let firstClient: QueryClient | undefined;
  const AccountPage = () => {
    const client = useQueryClient();
    if (getAccessToken() === 'first') firstClient = client;
    const [draft, setDraft] = useState('');
    const query = useQuery({ queryKey: ['private-data'], queryFn: async () => getAccessToken() ?? 'anonymous' });
    return <><span>{query.data}</span><input aria-label="draft" value={draft} onChange={e => setDraft(e.target.value)} /></>;
  };
  saveAccessToken('first');
  const view = render(<StrictMode><AppProviders><AccountPage /></AppProviders></StrictMode>);
  await screen.findByText('first');
  fireEvent.change(screen.getByLabelText('draft'), { target: { value: 'first account draft' } });
  act(() => { firstClient!.setQueryData(['other-private-cache'], 'old account'); saveAccessToken('second'); });
  await screen.findByText('second');
  expect(screen.getByLabelText('draft')).toHaveValue('');
  expect(screen.queryByText('first')).toBeNull();
  expect(firstClient!.getQueryCache().getAll()).toHaveLength(0);
  act(() => clearAccessToken());
  await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument());
  view.unmount();
});

it('discards a late query result from the previous account', async () => {
  let resolvePrevious!: (value: string) => void;
  const pending = new Promise<string>(resolve => { resolvePrevious = resolve; });
  const AccountPage = () => {
    const query = useQuery({
      queryKey: ['private-data'],
      queryFn: () => getAccessToken() === 'first' ? pending : Promise.resolve('second account'),
    });
    return <span>{query.data ?? 'loading'}</span>;
  };
  saveAccessToken('first');
  const view = render(<AppProviders><AccountPage /></AppProviders>);
  await screen.findByText('loading');
  act(() => saveAccessToken('second'));
  await screen.findByText('second account');
  await act(async () => { resolvePrevious('previous account result'); await pending; });
  expect(screen.queryByText('previous account result')).toBeNull();
  expect(screen.getByText('second account')).toBeInTheDocument();
  view.unmount();
});
