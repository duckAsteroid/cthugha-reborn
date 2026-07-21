let token: string | null = null;

export function initToken(): string | null {
  const params = new URLSearchParams(window.location.search);
  const t = params.get('token');
  if (t) {
    token = t;
  }
  return token;
}

export function getToken(): string | null {
  return token;
}

export function clearToken(): void {
  token = null;
}
