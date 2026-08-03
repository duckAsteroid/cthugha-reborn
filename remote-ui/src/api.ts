import { getToken } from './token';
import type { LeafNode, ActionNode, ParamNode, ServerInfo, StringPatchResult, VideoEntry, ImageEntry } from './types';

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

export async function createTrigger(
  path: string,
  body: { condition: string; cooldown?: number; value?: string },
): Promise<LeafNode | ActionNode> {
  return apiFetch<LeafNode | ActionNode>(`/api/v1/params/${path}/triggers`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export async function updateTrigger(
  path: string,
  name: string,
  patch: { condition?: string; cooldown?: number; value?: string; enabled?: boolean },
): Promise<LeafNode | ActionNode> {
  return apiFetch<LeafNode | ActionNode>(`/api/v1/params/${path}/triggers/${encodeURIComponent(name)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export async function deleteTrigger(path: string, name: string): Promise<LeafNode | ActionNode> {
  return apiFetch<LeafNode | ActionNode>(`/api/v1/params/${path}/triggers/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  });
}

export async function getVideos(): Promise<VideoEntry[]> {
  return apiFetch<VideoEntry[]>('/api/v1/videos');
}

export async function updateVideo(
  file: string,
  patch: { title?: string; tags?: string[]; source?: string; license?: string; defaultChapter?: string | null },
): Promise<VideoEntry> {
  return apiFetch<VideoEntry>(`/api/v1/videos/${encodeURIComponent(file)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export async function renameVideo(file: string, newFile: string): Promise<VideoEntry> {
  return apiFetch<VideoEntry>(`/api/v1/videos/${encodeURIComponent(file)}/rename`, {
    method: 'PATCH',
    body: JSON.stringify({ file: newFile }),
  });
}

export async function createChapter(
  file: string,
  chapter: { name: string; start: number; end: number },
): Promise<VideoEntry> {
  return apiFetch<VideoEntry>(`/api/v1/videos/${encodeURIComponent(file)}/chapters`, {
    method: 'POST',
    body: JSON.stringify(chapter),
  });
}

export async function updateChapter(
  file: string,
  name: string,
  patch: { name?: string; start?: number; end?: number },
): Promise<VideoEntry> {
  return apiFetch<VideoEntry>(`/api/v1/videos/${encodeURIComponent(file)}/chapters/${encodeURIComponent(name)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export async function deleteChapter(file: string, name: string): Promise<VideoEntry> {
  return apiFetch<VideoEntry>(`/api/v1/videos/${encodeURIComponent(file)}/chapters/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  });
}

export function videoStreamUrl(file: string): string {
  const token = getToken();
  return `${BASE_URL}/api/v1/videos/stream/${encodeURIComponent(file)}?token=${encodeURIComponent(token ?? '')}`;
}

export async function getImages(): Promise<ImageEntry[]> {
  return apiFetch<ImageEntry[]>('/api/v1/images');
}

export async function updateImage(
  file: string,
  patch: { title?: string; tags?: string[]; source?: string; license?: string },
): Promise<ImageEntry> {
  return apiFetch<ImageEntry>(`/api/v1/images/${encodeURIComponent(file)}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export async function renameImage(file: string, newFile: string): Promise<ImageEntry> {
  return apiFetch<ImageEntry>(`/api/v1/images/${encodeURIComponent(file)}/rename`, {
    method: 'PATCH',
    body: JSON.stringify({ file: newFile }),
  });
}

export async function getMaps(): Promise<string[]> {
  return apiFetch<string[]>('/api/v1/maps');
}

export async function saveMap(name: string, colors: string[]): Promise<{ name: string; size: number }> {
  return apiFetch<{ name: string; size: number }>(`/api/v1/maps/${encodeURIComponent(name)}`, {
    method: 'POST',
    body: JSON.stringify({ colors }),
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
