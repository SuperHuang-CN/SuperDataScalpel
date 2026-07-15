const shellQuote = (value: string): string => `'${value.replace(/'/g, `'"'"'`)}'`;

export const buildDataServiceCurlCommand = (publicUrl: string, routePath: string): string => {
  const baseUrl = publicUrl.replace(/\/+$/, '');
  const normalizedRoutePath = routePath.replace(/^\/+/, '');
  const requestUrl = `${baseUrl}/${normalizedRoutePath}`;

  return [
    'curl --request POST \\',
    `  --url ${shellQuote(requestUrl)} \\`,
    `  --header ${shellQuote('Content-Type: application/json')} \\`,
    `  --data ${shellQuote('{"pageNo":1,"pageSize":20}')}`,
  ].join('\n');
};
