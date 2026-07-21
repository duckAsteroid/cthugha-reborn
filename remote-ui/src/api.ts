import { getToken } from './token';
import type { LeafNode, ParamNode, ServerInfo, StringPatchResult } from './types';

const BASE_URL = window.location.origin;

function dispatchSessionExpired(): void {
  window.dispatchEvent(new Event('session-expired'));
}

async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error('Unauthorized: session expired');
  }

  if (!response.ok) {
    throw new Error(`API error ${response.status}: ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

export async function getParams(): Promise<ParamNode> {
  return apiFetch<ParamNode>('/api/v1/params');
}

export async function getParam(path: string): Promise<ParamNode> {
  return apiFetch<ParamNode>(`/api/v1/params/${path}`);
}

export async function patchParam(path: string, value: number): Promise<LeafNode> {
  return apiFetch<LeafNode>(`/api/v1/params/${path}`, {
    method: 'PATCH',
    body: JSON.stringify({ value }),
  });
}

export async function randomise(path: string): Promise<ParamNode> {
  return apiFetch<ParamNode>(`/api/v1/params/${path}/randomise`, {
    method: 'POST',
  });
}

export async function patchStringParam(path: string, value: string): Promise<StringPatchResult> {
  return apiFetch<StringPatchResult>(`/api/v1/params/${path}`, {
    method: 'PATCH',
    body: JSON.stringify({ value }),
  });
}

export async function executeAction(path: string): Promise<void> {
  await apiFetch<unknown>(`/api/v1/params/${path}/execute`, {
    method: 'POST',
  });
}

export async function createAnimation(path: string, script: string): Promise<LeafNode> {
  return apiFetch<LeafNode>(`/api/v1/params/${path}/animation`, {
    method: 'POST',
    body: JSON.stringify({ script }),
  });
}

export async function updateAnimation(
  path: string,
  patch: { script?: string; enabled?: boolean },
): Promise<LeafNode> {
  return apiFetch<LeafNode>(`/api/v1/params/${path}/animation`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export async function deleteAnimation(path: string): Promise<LeafNode> {
  return apiFetch<LeafNode>(`/api/v1/params/${path}/animation`, {
    method: 'DELETE',
  });
}

export async function getInfo(): Promise<ServerInfo> {
  // /api/v1/info does not require auth
  const response = await fetch(`${BASE_URL}/api/v1/info`);
  if (!response.ok) {
    throw new Error(`API error ${response.status}: ${response.statusText}`);
  }
  return response.json() as Promise<ServerInfo>;
}
