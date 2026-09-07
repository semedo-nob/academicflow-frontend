import { api, setTenantId } from './api';
import type {
  AcademicUnit,
  ApprovalItem,
  Candidate,
  ConflictItem,
  DashboardStats,
  Lecturer,
  OrganizationNode,
  TeachingRequest,
} from '../types';

export interface ApiAllocation {
  id: string;
  academicUnitId: string;
  unitCode: string;
  unitName: string;
  lecturerId: string | null;
  lecturerName: string | null;
  matchScore: number | null;
  status: string;
  overrideReason: string | null;
  teachingRequestId: string | null;
}

export interface ApiTimetableEntry {
  id: string;
  lecturerId: string | null;
  lecturerName: string | null;
  academicUnitId: string | null;
  unitCode: string | null;
  unitName: string | null;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room: string | null;
  conflict: boolean;
}

export interface ApiWorkloadSummary {
  totalHours: number;
  averageLoad: number;
  underloaded: number;
  optimal: number;
  nearLimit: number;
  overloaded: number;
  rows: {
    lecturerId: string;
    name: string;
    department: string;
    currentWorkload: number;
    maximumWorkload: number;
    utilization: number;
    status: string;
  }[];
}

function initials(name: string): string {
  const parts = name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
}

function availabilityLabel(current: number, max: number): 'Available' | 'Limited' | 'At limit' {
  const util = max ? current / max : 1;
  if (util >= 1) return 'At limit';
  if (util >= 0.85) return 'Limited';
  return 'Available';
}

function mapCandidate(c: {
  id: string | null;
  rank: number;
  lecturerId: string;
  name: string;
  dept: string;
  score: number;
  metrics: Record<string, number>;
  load: string;
  availability: string | null;
  reasons: string[];
  warn: string | null;
  hardConstraints: Record<string, boolean>;
}): Candidate {
  return {
    rank: c.rank,
    top: c.rank === 1,
    lecturerId: c.lecturerId,
    name: c.name,
    dept: c.dept,
    score: Number(c.score),
    metrics: {
      expertise: Number(c.metrics.expertise),
      availability: Number(c.metrics.availability),
      workload: Number(c.metrics.workload),
      studentLoad: Number(c.metrics.studentLoad),
      policy: Number(c.metrics.policy),
    },
    load: c.load,
    availability: c.availability || '',
    reasons: c.reasons,
    warn: c.warn || undefined,
    hardConstraints: {
      qualified: !!c.hardConstraints.qualified,
      available: !!c.hardConstraints.available,
      noConflict: !!c.hardConstraints.noConflict,
      workloadOk: !!c.hardConstraints.workloadOk,
      policyOk: !!c.hardConstraints.policyOk,
    },
  };
}

export const authService = {
  login: async (email: string, password?: string) => {
    const res = await api.post<{ email: string; name: string; role: string; tenantId: string }>(
      '/auth/login',
      { email, password },
    );
    setTenantId(res.tenantId);
    localStorage.setItem('af_user', JSON.stringify(res));
    return res;
  },
  registerInstitution: (body: {
    name: string;
    code: string;
    adminEmail: string;
    adminName: string;
  }) =>
    api.post<{ id: string; name: string; code: string; status: string; message: string }>(
      '/auth/register-institution',
      body,
    ),
  previewInvitation: (token: string) =>
    api.get<{
      email: string;
      name: string;
      role: string;
      institutionName: string;
      status: string;
      expired: boolean;
    }>(`/auth/invitations/${encodeURIComponent(token)}`),
  acceptInvitation: async (body: { token: string; name?: string; password?: string }) => {
    const res = await api.post<{ email: string; name: string; role: string; tenantId: string }>(
      '/auth/accept-invitation',
      body,
    );
    setTenantId(res.tenantId);
    localStorage.setItem('af_user', JSON.stringify(res));
    return res;
  },
};

export const lecturerService = {
  list: async (): Promise<Lecturer[]> => {
    const rows = await api.get<
      {
        id: string;
        staffNumber: string;
        name: string;
        email: string;
        departmentId: string;
        department: string;
        qualifications: string | null;
        currentWorkload: number;
        maximumWorkload: number;
        status: string;
        availability: string | null;
        expertise: string[];
        expertiseDetail: { subject: string; level: string }[];
      }[]
    >('/lecturers');
    return rows.map((l) => ({
      id: l.id,
      staffNumber: l.staffNumber,
      name: l.name,
      email: l.email,
      departmentId: l.departmentId,
      department: l.department,
      qualifications: l.qualifications || '',
      currentWorkload: Number(l.currentWorkload),
      maximumWorkload: Number(l.maximumWorkload),
      status: (l.status as 'Active' | 'Inactive') || 'Active',
      availability: l.availability || '',
      availabilityLabel: availabilityLabel(Number(l.currentWorkload), Number(l.maximumWorkload)),
      initials: initials(l.name),
      expertise: l.expertise,
      expertiseDetail: l.expertiseDetail.map((e) => ({
        subject: e.subject,
        level: e.level as Lecturer['expertiseDetail'][0]['level'],
      })),
      units: [],
    }));
  },
  create: (body: {
    staffNumber: string;
    name: string;
    email: string;
    departmentId: string;
    qualifications?: string;
    maximumWorkload?: number;
    availability?: string;
    expertise?: { subject: string; level: string }[];
  }) => api.post('/lecturers', body),
};

export const unitService = {
  list: async (): Promise<AcademicUnit[]> => {
    const rows = await api.get<
      {
        id: string;
        code: string;
        name: string;
        sourceDepartmentId: string;
        sourceDepartment: string;
        contactHours: number;
        studentCount: number;
        requiredExpertise: string[];
        status: string;
        lecturerName: string | null;
      }[]
    >('/academic-units');
    return rows.map((u) => ({
      id: u.id,
      code: u.code,
      name: u.name,
      sourceDepartmentId: u.sourceDepartmentId,
      sourceDepartment: u.sourceDepartment,
      contactHours: Number(u.contactHours),
      studentCount: u.studentCount,
      academicYear: '2026/2027',
      semester: 'Semester 1',
      requiredExpertise: u.requiredExpertise,
      status: u.status,
      lecturerName: u.lecturerName,
    }));
  },
  create: (body: {
    code: string;
    name: string;
    sourceDepartmentId: string;
    contactHours?: number;
    studentCount?: number;
    requiredExpertise?: string[];
  }) => api.post('/academic-units', body),
};

export type ApiTeachingRequest = TeachingRequest & {
  rawId: string;
  academicUnitId: string;
  requestingDepartmentId: string;
  preferredDepartmentId: string | null;
};

export const requestService = {
  list: async (): Promise<ApiTeachingRequest[]> => {
    const rows = await api.get<
      {
        id: string;
        requestingDepartment: string;
        requestingDepartmentId: string;
        sourceDepartment: string | null;
        preferredDepartmentId: string | null;
        academicUnitId: string;
        academicUnit: string;
        studentCount: number;
        contactHours: number;
        requiredExpertise: string | null;
        status: string;
        createdAt: string;
      }[]
    >('/requests');
    return rows.map((r) => ({
      id: r.id.slice(0, 8).toUpperCase(),
      rawId: r.id,
      requestingDepartment: r.requestingDepartment,
      sourceDepartment: r.sourceDepartment || '',
      academicUnit: r.academicUnit,
      studentCount: r.studentCount,
      contactHours: Number(r.contactHours),
      requiredExpertise: r.requiredExpertise || '',
      semester: 'Semester 1',
      academicYear: '2026/2027',
      status: r.status.replace(/_/g, ' '),
      createdAt: new Date(r.createdAt).toLocaleDateString('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      }),
      direction: 'outgoing' as const,
      academicUnitId: r.academicUnitId,
      preferredDepartmentId: r.preferredDepartmentId,
      requestingDepartmentId: r.requestingDepartmentId,
    }));
  },
  create: (body: {
    requestingDepartmentId: string;
    preferredDepartmentId?: string | null;
    academicUnitId: string;
    studentCount: number;
    contactHours: number;
    requiredExpertise?: string;
  }) => api.post('/requests', body),
  findCandidates: async (requestId: string) => {
    const rows = await api.post<Parameters<typeof mapCandidate>[0][]>(`/requests/${requestId}/candidates`);
    return rows.map(mapCandidate);
  },
  getCandidates: async (requestId: string) => {
    const rows = await api.get<Parameters<typeof mapCandidate>[0][]>(`/requests/${requestId}/candidates`);
    return rows.map(mapCandidate);
  },
};

export const allocationService = {
  list: () => api.get<ApiAllocation[]>('/allocations'),
  create: (body: {
    academicUnitId: string;
    lecturerId: string;
    teachingRequestId?: string;
    matchScore?: number;
    overrideReason?: string;
    recommendedLecturerId?: string;
  }) => api.post<ApiAllocation>('/allocations', body),
  submit: (id: string) => api.post(`/allocations/${id}/submit`),
  approve: (id: string, approve = true, note?: string) =>
    api.post(
      `/allocations/${id}/approve?approve=${approve}${note ? `&note=${encodeURIComponent(note)}` : ''}`,
    ),
};

export const organizationService = {
  list: async (): Promise<OrganizationNode[]> => {
    const rows = await api.get<
      {
        id: string;
        tenantId: string;
        name: string;
        type: string;
        parentId: string | null;
        lecturerCount: number;
        unitCount: number;
      }[]
    >('/organization');
    return rows.map((n) => ({
      id: n.id,
      tenantId: n.tenantId,
      name: n.name,
      type: n.type,
      parentId: n.parentId,
      lecturerCount: n.lecturerCount,
      unitCount: n.unitCount,
    }));
  },
  create: (body: { name: string; type: string; parentId?: string | null }) =>
    api.post('/organization', body),
};

export const dashboardService = {
  get: async (): Promise<{
    stats: DashboardStats;
    activity: { text: string; time: string }[];
    requests: TeachingRequest[];
  }> => {
    const d = await api.get<{
      totalLecturers: number;
      academicUnits: number;
      allocated: number;
      pending: number;
      conflicts: number;
      crossDeptRequests: number;
      completionPct: number;
      recentRequests: {
        id: string;
        requestingDepartment: string;
        sourceDepartment: string | null;
        academicUnit: string;
        studentCount: number;
        contactHours: number;
        requiredExpertise: string | null;
        status: string;
        createdAt: string;
      }[];
      recentActivity: { action: string; details: string | null; createdAt: string }[];
    }>('/dashboard');
    const wl = await workloadService.get().catch(() => null);
    return {
      stats: {
        totalLecturers: d.totalLecturers,
        academicUnits: d.academicUnits,
        allocated: d.allocated,
        pending: d.pending,
        conflicts: d.conflicts,
        crossDeptRequests: d.crossDeptRequests,
        completionPct: d.completionPct,
        allocatedCount: d.allocated,
        awaitingApproval: 0,
        pendingUnits: d.pending,
        conflicted: d.conflicts,
        underloaded: wl?.underloaded ?? 0,
        optimal: wl?.optimal ?? 0,
        nearLimit: wl?.nearLimit ?? 0,
        overloaded: wl?.overloaded ?? 0,
      },
      activity: d.recentActivity.map((a) => ({
        text: `<b>${a.action}</b>${a.details ? ` — ${a.details}` : ''}`,
        time: new Date(a.createdAt).toLocaleString(),
      })),
      requests: d.recentRequests.map((r) => ({
        id: r.id.slice(0, 8).toUpperCase(),
        requestingDepartment: r.requestingDepartment,
        sourceDepartment: r.sourceDepartment || '',
        academicUnit: r.academicUnit,
        studentCount: r.studentCount,
        contactHours: Number(r.contactHours),
        requiredExpertise: r.requiredExpertise || '',
        semester: 'Semester 1',
        academicYear: '2026/2027',
        status: r.status.replace(/_/g, ' '),
        createdAt: new Date(r.createdAt).toLocaleDateString(),
        direction: 'outgoing' as const,
      })),
    };
  },
};

export const conflictService = {
  list: async (): Promise<(ConflictItem & { allocationId?: string | null })[]> => {
    const rows = await api.get<
      {
        id: string;
        category: string;
        severity: string;
        description: string;
        relatedEntity: string | null;
        allocationId: string | null;
      }[]
    >('/conflicts');
    return rows.map((c) => ({
      id: c.id,
      severity: (c.severity === 'high' ? 'high' : 'med') as ConflictItem['severity'],
      category: c.category as ConflictItem['category'],
      who: c.relatedEntity || 'Unknown',
      text: c.description,
      action: 'Resolve',
      allocationId: c.allocationId,
    }));
  },
  resolve: (id: string) => api.post(`/conflicts/${id}/resolve`),
};

export const approvalService = {
  list: async (): Promise<(ApprovalItem & { allocationId: string })[]> => {
    const rows = await api.get<
      {
        id: string;
        allocationId: string;
        unit: string;
        lecturer: string | null;
        status: string;
        conflicts: number;
        submittedAt: string;
      }[]
    >('/approvals');
    return rows.map((a) => ({
      id: a.id.slice(0, 8).toUpperCase(),
      allocationId: a.allocationId,
      unit: a.unit,
      route: '',
      lecturer: a.lecturer || '',
      submittedBy: 'System',
      date: new Date(a.submittedAt).toLocaleDateString(),
      conflicts: a.conflicts,
      status:
        a.status === 'PENDING'
          ? 'Pending Approval'
          : a.status === 'APPROVED'
            ? 'Approved'
            : a.status,
    }));
  },
};

export const workloadService = {
  get: () => api.get<ApiWorkloadSummary>('/workload'),
};

export const timetableService = {
  list: () => api.get<ApiTimetableEntry[]>('/timetable'),
  create: (body: {
    lecturerId: string;
    academicUnitId?: string;
    dayOfWeek: number;
    startTime: string;
    endTime: string;
    room?: string;
  }) => api.post<ApiTimetableEntry>('/timetable', body),
};

export const importService = {
  list: () =>
    api.get<{ id: string; fileName: string; entityType: string; status: string; createdAt: string }[]>(
      '/imports',
    ),
  create: (body: {
    fileName: string;
    entityType: string;
    columnMap?: Record<string, string>;
    rows?: Record<string, string>[];
  }) =>
    api.post<{ id: string; fileName: string; entityType: string; status: string; createdAt: string }>(
      '/imports',
      body,
    ),
  advance: (id: string) =>
    api.post<{ id: string; fileName: string; entityType: string; status: string; createdAt: string }>(
      `/imports/${id}/advance`,
    ),
  rows: (id: string) => api.get<Record<string, string>[]>(`/admin/imports/${id}/rows`),
  upload: (file: File, entityType?: string) => {
    const fd = new FormData();
    fd.append('file', file);
    const q = entityType ? `?entityType=${encodeURIComponent(entityType)}` : '';
    return api.upload<{
      sessionId: string;
      fileName: string;
      entityType: string;
      status: string;
      detectedColumns: string[];
      suggestedMap: Record<string, string>;
      rowCount: number;
      preview: Record<string, string>[];
      warnings: string[];
    }>(`/imports/upload${q}`, fd);
  },
};

export const searchService = {
  search: (q: string) =>
    api.get<{ hits: { type: string; id: string; title: string; subtitle: string; path: string }[] }>(
      `/search?q=${encodeURIComponent(q)}`,
    ),
};

export const userService = {
  list: () =>
    api.get<{ id: string; email: string; name: string; role: string; active: boolean; organizationNodeId: string | null }[]>(
      '/users',
    ),
  create: (body: {
    email: string;
    name: string;
    role: string;
    organizationNodeId?: string | null;
    active?: boolean;
  }) => api.post('/admin/users', body),
  update: (id: string, body: { role?: string; active?: boolean; name?: string; organizationNodeId?: string | null }) =>
    api.put(`/admin/users/${id}`, body),
  invitations: () =>
    api.get<
      {
        id: string;
        email: string;
        name: string;
        role: string;
        organizationNodeId: string | null;
        status: string;
        token: string;
        invitePath: string;
        createdAt: string;
        expiresAt: string;
      }[]
    >('/admin/invitations'),
  invite: (body: {
    email: string;
    name: string;
    role: string;
    organizationNodeId?: string | null;
  }) =>
    api.post<{
      id: string;
      email: string;
      name: string;
      role: string;
      invitePath: string;
      token: string;
      status: string;
    }>('/admin/invitations', body),
};

export const adminService = {
  institutions: () =>
    api.get<
      {
        id: string;
        name: string;
        code: string;
        status: string;
        adminEmail: string | null;
        adminName: string | null;
        decisionNote: string | null;
        createdAt: string | null;
      }[]
    >('/admin/institutions'),
  decideInstitution: (id: string, body: { approve: boolean; note?: string }) =>
    api.post<{
      id: string;
      name: string;
      code: string;
      status: string;
    }>(`/admin/institutions/${id}/decision`, body),
  setInstitutionStatus: (id: string, body: { status: 'SUSPENDED' | 'APPROVED'; note?: string }) =>
    api.post<{
      id: string;
      name: string;
      code: string;
      status: string;
    }>(`/admin/institutions/${id}/status`, body),
  platformOverview: () =>
    api.get<{
      pendingRegistrations: number;
      approvedInstitutions: number;
      suspendedInstitutions: number;
      rejectedInstitutions: number;
      totalInstitutions: number;
    }>('/admin/platform/overview'),
  organizationTypes: () =>
    api.get<{ id: string; name: string; levelNo: number; description: string | null }[]>('/admin/organization-types'),
  roles: () =>
    api.get<{ id: string; code: string; name: string; permissions: string[]; description: string | null }[]>(
      '/admin/roles',
    ),
  years: () =>
    api.get<{ id: string; label: string; startDate: string | null; endDate: string | null }[]>('/admin/academic-years'),
  createYear: (body: { label: string; startDate?: string; endDate?: string }) =>
    api.post('/admin/academic-years', body),
  semesters: (academicYearId?: string) =>
    api.get<{ id: string; academicYearId: string; academicYearLabel: string; name: string; sequenceNo: number }[]>(
      academicYearId ? `/admin/semesters?academicYearId=${academicYearId}` : '/admin/semesters',
    ),
  createSemester: (body: { academicYearId: string; name: string; sequenceNo?: number }) =>
    api.post('/admin/semesters', body),
  period: () =>
    api.get<{
      academicYear: { id: string; label: string; startDate: string | null; endDate: string | null } | null;
      semester: {
        id: string;
        academicYearId: string;
        academicYearLabel: string;
        name: string;
        sequenceNo: number;
      } | null;
      years: { id: string; label: string; startDate: string | null; endDate: string | null }[];
      semesters: {
        id: string;
        academicYearId: string;
        academicYearLabel: string;
        name: string;
        sequenceNo: number;
      }[];
    }>('/admin/period'),
  rules: () => api.get<{ id: string; key: string; value: string; description: string | null }[]>('/admin/rules'),
  updateRule: (id: string, value: string) => api.put(`/admin/rules/${id}`, { value }),
  settings: () =>
    api.get<{ id: string; key: string; value: string; description: string | null }[]>('/admin/settings'),
  updateSetting: (id: string, value: string) => api.put(`/admin/settings/${id}`, { value }),
  mappingProfiles: () =>
    api.get<{ id: string; name: string; entityType: string; columnMap: Record<string, string> }[]>(
      '/admin/mapping-profiles',
    ),
  createMappingProfile: (body: { name: string; entityType: string; columnMap: Record<string, string> }) =>
    api.post('/admin/mapping-profiles', body),
};

export const auditService = {
  list: () =>
    api.get<{ id: string; action: string; entityType: string; entityId: string | null; details: string | null; createdAt: string }[]>(
      '/audit',
    ),
};

export const reportService = {
  get: (type: string) =>
    api.get<{ title: string; generatedAt: string; metrics: Record<string, string> }>(
      `/reports/${encodeURIComponent(type)}`,
    ),
};

export const exportService = {
  /** kind: allocations | timetable | full ; format: csv | pdf */
  download: (kind: 'allocations' | 'timetable' | 'full', format: 'csv' | 'pdf') =>
    api.download(`/exports/${kind}/${format}`, `academicflow-${kind}.${format}`),
};
