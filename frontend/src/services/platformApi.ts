import { api } from './api';

export const platformApi = {
  commandCenter: () =>
    api.get<{
      customers: Record<string, number>;
      users: Record<string, number>;
      activity: Record<string, number>;
      attention: string[];
      health: { component: string; status: string; detail: string }[];
      recentInstitutions: { id: string; name: string; code: string; status: string; createdAt: string }[];
      productVersion: string;
    }>('/platform/command-center'),
  health: () => api.get<{ checks: { component: string; status: string; detail: string }[] }>('/platform/health'),
  system: () => api.get<Record<string, unknown>>('/platform/system'),
  institutions: (status?: string) =>
    api.get<
      {
        id: string;
        name: string;
        code: string;
        status: string;
        onboardingStage: string;
        planCode: string;
        adminEmail: string | null;
        adminName: string | null;
        userCount: number;
        createdAt: string;
        lastActivityAt: string | null;
        decisionNote: string | null;
      }[]
    >(status ? `/platform/institutions?status=${encodeURIComponent(status)}` : '/platform/institutions'),
  institution: (id: string) =>
    api.get<{
      id: string;
      name: string;
      code: string;
      status: string;
      onboardingStage: string;
      planCode: string;
      adminEmail: string | null;
      adminName: string | null;
      createdAt: string;
      lastActivityAt: string | null;
      decisionNote: string | null;
      usage: Record<string, number>;
      administrators: {
        id: string;
        name: string;
        email: string;
        role: string;
        active: boolean;
        accountStatus: string;
        lastLoginAt: string | null;
      }[];
      recentAudit: { id: string; action: string; entityType: string; details: string | null; createdAt: string }[];
    }>(`/platform/institutions/${id}`),
  lifecycle: (id: string, body: { status: string; note?: string }) =>
    api.post(`/platform/institutions/${id}/lifecycle`, body),
  users: (q?: string) =>
    api.get<
      {
        id: string;
        email: string;
        name: string;
        role: string;
        accountStatus: string;
        active: boolean;
        tenantId: string;
        institutionName: string;
        institutionCode: string;
        lastLoginAt: string | null;
        failedLoginCount: number;
        createdAt: string;
      }[]
    >(q ? `/platform/users?q=${encodeURIComponent(q)}` : '/platform/users'),
  setUserStatus: (id: string, body: { status: string; note?: string }) =>
    api.post(`/platform/users/${id}/status`, body),
  analytics: () => api.get<Record<string, unknown>>('/platform/analytics'),
  securityEvents: () =>
    api.get<
      {
        id: string;
        severity: string;
        eventType: string;
        email: string | null;
        tenantId: string | null;
        details: string | null;
        createdAt: string;
      }[]
    >('/platform/security/events'),
  audit: () =>
    api.get<
      {
        id: string;
        action: string;
        entityType: string;
        entityId: string | null;
        details: string | null;
        createdAt: string;
        actorId: string | null;
      }[]
    >('/platform/audit'),
  features: () =>
    api.get<{ id: string; key: string; name: string; description: string | null; enabled: boolean; updatedAt: string }[]>(
      '/platform/features',
    ),
  setFeature: (id: string, enabled: boolean) => api.put(`/platform/features/${id}`, { enabled }),
  configuration: () =>
    api.get<{ id: string; key: string; value: string; description: string | null; updatedAt: string }[]>(
      '/platform/configuration',
    ),
  updateConfiguration: (id: string, value: string) => api.put(`/platform/configuration/${id}`, { value }),
  dataQuality: () => api.get<Record<string, unknown>>('/platform/data-quality'),
  supportLookup: (q: string) => api.get<Record<string, unknown>>(`/platform/support/lookup?q=${encodeURIComponent(q)}`),
  search: (q: string) =>
    api.get<{ type: string; title: string; subtitle: string; path: string }[]>(
      `/platform/search?q=${encodeURIComponent(q)}`,
    ),
};
