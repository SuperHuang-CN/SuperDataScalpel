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
          darkItemBg: 'transparent',
          darkSubMenuItemBg: 'rgba(4, 18, 61, 0.22)',
          darkItemColor: 'rgba(235, 241, 255, 0.76)',
          darkItemHoverBg: 'rgba(255, 255, 255, 0.10)',
          darkItemSelectedBg: 'rgba(255, 255, 255, 0.17)',
          darkItemSelectedColor: '#FFFFFF',
          itemHeight: 38,
          itemMarginBlock: 3,
          itemBorderRadius: 10,
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
