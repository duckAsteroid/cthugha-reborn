import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { getToken } from './token';
import type { SSEParamChangedEvent } from './types';

export interface ParamState {
  value: number;
  controlled: boolean;
}

const SSEContext = createContext<Map<string, ParamState>>(new Map());

// NOTE: EventSource does not support custom headers in the browser.
// The server must accept ?token=<value> as a query parameter for SSE auth,
// in addition to the standard Authorization: Bearer header for regular requests.
function buildSSEUrl(): string {
  const token = getToken();
  const params = new URLSearchParams();
  if (token) {
    params.set('token', token);
  }
  // No `path` filter: subscribes to the whole tree over this one connection, so every
  // consumer can read live state from context instead of opening its own EventSource.
  return `/api/v1/events?${params.toString()}`;
}

/**
 * Opens a single, app-wide SSE connection for the lifetime of the authenticated session and
 * exposes the live param-state map via context. Mount once, near the root, after a token is
 * known to exist — every {@link useSSEState} caller shares this one connection.
 *
 * Browsers cap concurrent HTTP/1.1 connections per origin (typically 6); one connection per
 * expanded tree section used to exhaust that limit after a few clicks, silently queuing further
 * requests (including action POSTs) forever. A single shared connection avoids that entirely.
 */
export function SSEProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<Map<string, ParamState>>(new Map());
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const es = new EventSource(buildSSEUrl());
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

    es.addEventListener('treeChanged', () => {
      window.dispatchEvent(new Event('tree-changed'));
    });

    // 'ping' events are heartbeats — ignore them

    let disconnectTimer: ReturnType<typeof setTimeout> | null = null;

    es.onopen = () => {
      if (disconnectTimer !== null) {
        clearTimeout(disconnectTimer);
        disconnectTimer = null;
      }
      window.dispatchEvent(new Event('connection-restored'));
    };

    es.onerror = () => {
      // Wait briefly before declaring connection lost to avoid flashing on transient hiccups
      if (disconnectTimer === null) {
        disconnectTimer = setTimeout(() => {
          window.dispatchEvent(new Event('connection-lost'));
        }, 3000);
      }
    };

    return () => {
      if (disconnectTimer !== null) {
        clearTimeout(disconnectTimer);
      }
      es.close();
      esRef.current = null;
    };
  }, []);

  return <SSEContext.Provider value={state}>{children}</SSEContext.Provider>;
}

/** Returns the shared live param-state map maintained by the app-wide {@link SSEProvider}. */
export function useSSEState(): Map<string, ParamState> {
  return useContext(SSEContext);
}
