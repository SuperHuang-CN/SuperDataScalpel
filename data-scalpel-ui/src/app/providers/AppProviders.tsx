import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { PropsWithChildren } from 'react';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export const AppProviders = ({ children }: PropsWithChildren) => (
  <ConfigProvider
    locale={zhCN}
    componentSize="small"
    theme={{
      token: {
        colorPrimary: '#1668dc',
        borderRadius: 4,
        controlHeightSM: 24,
        fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
      },
      components: {
        Card: {
          bodyPadding: 12,
          bodyPaddingSM: 12,
        },
        Menu: {
          itemHeight: 34,
          itemMarginBlock: 2,
          itemBorderRadius: 4,
        },
        Table: {
          cellPaddingBlockSM: 6,
          cellPaddingInlineSM: 10,
        },
      },
    }}
  >
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  </ConfigProvider>
);
