import { useEffect, useRef, useState } from 'react';
import { getToken } from './token';
import type { SSEParamChangedEvent } from './types';

export interface ParamState {
  value: number;
  controlled: boolean;
}

// NOTE: EventSource does not support custom headers in the browser.
// The server must accept ?token=<value> as a query parameter for SSE auth,
// in addition to the standard Authorization: Bearer header for regular requests.
function buildSSEUrl(paths: string[]): string {
  const token = getToken();
  const params = new URLSearchParams();
  if (token) {
    params.set('token', token);
  }
  for (const p of paths) {
    params.append('path', p);
  }
  return `/api/v1/events?${params.toString()}`;
}

export function useSSE(paths: string[]): Map<string, ParamState> {
  const [state, setState] = useState<Map<string, ParamState>>(new Map());
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }

    const url = buildSSEUrl(paths);
    const es = new EventSource(url);
    esRef.current = es;

    es.addEventListener('paramChanged', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string) as SSEParamChangedEvent;
        setState((prev) => {
          const next = new Map(prev);
          next.set(data.path, { value: data.value, controlled: data.controlled });
          return next;
        });
      } catch {
        // ignore malformed events
      }
    });

    es.addEventListener('tokenRotated', () => {
      window.dispatchEvent(new Event('session-expired'));
    });

    // 'ping' events are heartbeats — ignore them

    es.onerror = () => {
      // EventSource will auto-reconnect; nothing to do here
    };

    return () => {
      es.close();
      esRef.current = null;
    };
  }, [paths.join(',')]); // eslint-disable-line react-hooks/exhaustive-deps

  return state;
}
