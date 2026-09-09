const API_BASE = import.meta.env.VITE_API_BASE || import.meta.env.VITE_API_BASE_URL || '/api';
const DEFAULT_TENANT = '11111111-1111-1111-1111-111111111111';

type TokenGetter = () => Promise<string | null>;
let clerkTokenGetter: TokenGetter | null = null;

/** Registered by ClerkTokenBridge so API calls attach Authorization: Bearer <session>. */
export function setClerkTokenGetter(getter: TokenGetter | null) {
  clerkTokenGetter = getter;
}

export function resolveTenantId(): string {
  const stored = localStorage.getItem('af_tenant');
  if (stored && /^[0-9a-fA-F-]{36}$/.test(stored)) return stored;
  if (stored) localStorage.removeItem('af_tenant');
  return DEFAULT_TENANT;
}

export function resolveActiveDepartmentId(): string | null {
  const stored = localStorage.getItem('af_active_department');
  if (stored && /^[0-9a-fA-F-]{36}$/.test(stored)) return stored;
  return null;
}

export function setActiveDepartmentId(id: string | null) {
  if (id) localStorage.setItem('af_active_department', id);
  else localStorage.removeItem('af_active_department');
}

export function setTenantId(id: string) {
  if (/^[0-9a-fA-F-]{36}$/.test(id)) {
    localStorage.setItem('af_tenant', id);
  }
}

/** Wipe identity headers/storage so the next account cannot inherit the previous one. */
export function clearClientSession(opts?: { keepRemember?: boolean }) {
  localStorage.removeItem('af_auth');
  localStorage.removeItem('af_user');
  localStorage.removeItem('af_tenant');
  localStorage.removeItem('af_active_department');
  if (!opts?.keepRemember) {
    localStorage.removeItem('af_remember');
    localStorage.removeItem('af_remember_email');
  }
}

function resolveUserEmail(): string {
  try {
    const raw = localStorage.getItem('af_user');
    if (!raw) return '';
    const parsed = JSON.parse(raw) as { email?: string };
    return parsed.email || '';
  } catch {
    return '';
  }
}

async function buildHeaders(extra?: Record<string, string>): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'X-Tenant-Id': resolveTenantId(),
    ...(extra || {}),
  };
  const token = clerkTokenGetter ? await clerkTokenGetter() : null;
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  } else {
    const email = resolveUserEmail();
    if (email) headers['X-User-Email'] = email;
  }
  const dept = resolveActiveDepartmentId();
  if (dept) headers['X-Active-Department-Id'] = dept;
  return headers;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = await buildHeaders(options.headers as Record<string, string> | undefined);
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json';
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });
  if (!res.ok) {
    let message = res.statusText;
    let code: string | undefined;
    try {
      const body = await res.json();
      code = body.code;
      message = body.message || body.detail || body.error || JSON.stringify(body);
    } catch {
      try {
        message = await res.text();
      } catch {
        /* ignore */
      }
    }
    const err = new Error(message || `Request failed (${res.status})`) as Error & { code?: string; status?: number };
    err.code = code;
    err.status = res.status;
    throw err;
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PUT',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData) =>
    request<T>(path, {
      method: 'POST',
      body: formData,
    }),
  download: async (path: string, fallbackName: string) => {
    const headers = await buildHeaders();
    const res = await fetch(`${API_BASE}${path}`, { headers });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message || body.detail || body.error || message;
      } catch {
        /* ignore */
      }
      throw new Error(message || `Download failed (${res.status})`);
    }
    const blob = await res.blob();
    const cd = res.headers.get('Content-Disposition') || '';
    const match = /filename="?([^"]+)"?/.exec(cd);
    const filename = match?.[1] || fallbackName;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  },
};
