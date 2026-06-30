let token: string | null = null;

export function initToken(): string | null {
  const params = new URLSearchParams(window.location.search);
  const t = params.get('token');
  if (t) {
    token = t;
    const url = new URL(window.location.href);
    url.searchParams.delete('token');
    window.history.replaceState({}, '', url.toString());
  }
  return token;
}

export function getToken(): string | null {
  return token;
}

export function clearToken(): void {
  token = null;
}
