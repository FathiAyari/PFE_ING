export const environment = {
  production: true,
  // In containers nginx proxies /api/ to the backend, so a relative URL works.
  apiBaseUrl: '/api',
  wsUrl: '/ws'
};
