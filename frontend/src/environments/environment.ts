export const environment = {
  production: false,
  get keycloakUrl(): string {
    return `${window.location.protocol}//${window.location.hostname}:8180`;
  },
  get apiUrl(): string {
    return `${window.location.protocol}//${window.location.hostname}:8080`;
  },
  get wsUrl(): string {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${window.location.host}`;
  },
};
