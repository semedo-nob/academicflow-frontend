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
    const res = await api.post<{
      email: string;
      name: string;
      role: string;
      tenantId: string;
      userId?: string;
      organizationNodeId?: string | null;
      departmentName?: string | null;
      activeDepartmentId?: string | null;
      activeDepartmentName?: string | null;
      activeRole?: string | null;
      memberships?: {
        id: string;
        organizationNodeId: string;
        organizationName: string;
        organizationType: string;
        role: string;
        isPrimary: boolean;
      }[];
    }>('/auth/login', { email, password });
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
    const res = await api.post<{
      email: string;
      name: string;
      role: string;
      tenantId: string;
      userId?: string;
      organizationNodeId?: string | null;
      departmentName?: string | null;
    }>('/auth/accept-invitation', body);
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
    departmentId?: string;
    department?: string;
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
    sourceDepartmentId?: string;
    sourceDepartment?: string;
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
  courseOfferingId?: string | null;
  createdBy?: string | null;
  briefingNote?: string | null;
  attachmentCount?: number;
  messageCount?: number;
};

export type RequestAttachment = {
  id: string;
  teachingRequestId: string;
  fileName: string;
  contentType: string | null;
  fileSizeBytes: number | null;
  docType: string;
  uploadedByName: string | null;
  departmentName: string | null;
  createdAt: string;
};

export type RequestMessage = {
  id: string;
  teachingRequestId: string;
  authorName: string | null;
  authorRole: string | null;
  authorDepartmentName: string | null;
  messageType: string;
  body: string;
  relatedLecturerId: string | null;
  relatedLecturerName: string | null;
  notifyAuthority: boolean;
  createdAt: string;
  mine: boolean;
};

export type TeachingRequestDetail = {
  request: ApiTeachingRequest;
  attachments: RequestAttachment[];
  messages: RequestMessage[];
};

function mapApiRequest(r: {
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
  courseOfferingId?: string | null;
  createdBy?: string | null;
  direction?: string | null;
  briefingNote?: string | null;
  attachmentCount?: number;
  messageCount?: number;
}): ApiTeachingRequest {
  return {
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
    direction: (r.direction as ApiTeachingRequest['direction']) || 'outgoing',
    academicUnitId: r.academicUnitId,
    preferredDepartmentId: r.preferredDepartmentId,
    requestingDepartmentId: r.requestingDepartmentId,
    courseOfferingId: r.courseOfferingId || null,
    createdBy: r.createdBy || null,
    briefingNote: r.briefingNote || null,
    attachmentCount: r.attachmentCount ?? 0,
    messageCount: r.messageCount ?? 0,
  };
}

export const requestService = {
  list: async (): Promise<ApiTeachingRequest[]> => {
    const rows = await api.get<Parameters<typeof mapApiRequest>[0][]>('/requests');
    return rows.map(mapApiRequest);
  },
  getDetail: async (id: string): Promise<TeachingRequestDetail> => {
    const detail = await api.get<{
      request: Parameters<typeof mapApiRequest>[0];
      attachments: RequestAttachment[];
      messages: RequestMessage[];
    }>(`/requests/${id}`);
    return {
      request: mapApiRequest(detail.request),
      attachments: detail.attachments,
      messages: detail.messages,
    };
  },
  create: (body: {
    requestingDepartmentId?: string;
    requestingDepartment?: string;
    preferredDepartmentId?: string | null;
    preferredDepartment?: string | null;
    academicUnitId?: string;
    academicUnit?: string;
    studentCount: number;
    contactHours: number;
    requiredExpertise?: string;
    createOffering?: boolean;
    programme?: string;
    contextLabel?: string;
    briefingNote?: string;
  }) => api.post<Parameters<typeof mapApiRequest>[0]>('/requests', body).then(mapApiRequest),
  respond: (id: string, action: 'ACCEPT' | 'DECLINE', note?: string) =>
    api.post(`/requests/${id}/respond`, { action, note }),
  uploadAttachment: (id: string, file: File, docType: string = 'COURSE_OUTLINE') => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('docType', docType);
    return api.upload<RequestAttachment>(`/requests/${id}/attachments`, fd);
  },
  downloadAttachment: (attachmentId: string, fileName: string) =>
    api.download(`/request-attachments/${attachmentId}/download`, fileName),
  postMessage: (
    id: string,
    body: {
      body: string;
      messageType?: 'COMMENT' | 'ELIGIBILITY_NOTE' | 'AUTHORITY_NOTICE';
      relatedLecturerId?: string;
      relatedLecturerName?: string;
      notifyAuthority?: boolean;
    },
  ) => api.post<RequestMessage>(`/requests/${id}/messages`, body),
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
    courseOfferingId?: string;
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
  create: (body: { name: string; type: string; parentId?: string | null; parentName?: string | null }) =>
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
      scopeDepartmentId?: string | null;
      scopeDepartmentName?: string | null;
      scopeRole?: string | null;
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
        scopeDepartmentId: d.scopeDepartmentId,
        scopeDepartmentName: d.scopeDepartmentName,
        scopeRole: d.scopeRole,
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

export type ImportResultSummary = {
  allocations: number;
  lecturers: number;
  units: number;
  warnings: number;
  errors: number;
  duplicatesSkipped: number;
  unmappedFields: number;
  details: string[];
};

export type MappingSuggestion = {
  sourceColumn: string;
  targetField: string | null;
  confidence: number;
  method: string;
  sampleValues: string[];
  unmapped: boolean;
};

export type ImportUploadResult = {
  sessionId: string;
  fileName: string;
  entityType: string;
  status: string;
  detectedColumns: string[];
  suggestedMap: Record<string, string>;
  mappingSuggestions: MappingSuggestion[];
  unmappedColumns: string[];
  canonicalFields: string[];
  profileApplied: string | null;
  rowCount: number;
  preview: Record<string, string>[];
  warnings: string[];
};

export type ImportSession = {
  id: string;
  fileName: string;
  entityType: string;
  status: string;
  columnMap?: string | null;
  createdAt: string;
  resultSummary?: ImportResultSummary | null;
};

export const importService = {
  list: () => api.get<ImportSession[]>('/imports'),
  create: (body: {
    fileName: string;
    entityType: string;
    columnMap?: Record<string, string>;
    rows?: Record<string, string>[];
  }) => api.post<ImportSession>('/imports', body),
  advance: (id: string) => api.post<ImportSession>(`/imports/${id}/advance`),
  reprocess: (id: string) => api.post<ImportSession>(`/imports/${id}/reprocess`),
  rows: (id: string) => api.get<Record<string, string>[]>(`/imports/${id}/rows`),
  updateMapping: (
    id: string,
    body: {
      columnMap: Record<string, string>;
      entityType?: string;
      ignoredColumns?: string[];
      saveAsProfileName?: string;
    },
  ) => api.post<ImportUploadResult>(`/imports/${id}/mapping`, body),
  upload: (file: File, entityType?: string) => {
    const fd = new FormData();
    fd.append('file', file);
    const q = entityType ? `?entityType=${encodeURIComponent(entityType)}` : '';
    return api.upload<ImportUploadResult>(`/imports/upload${q}`, fd);
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
    organization?: string | null;
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
  assignChair: (body: { userId?: string; email?: string; departmentId?: string; department?: string }) =>
    api.post<{
      id: string;
      organizationNodeId: string;
      organizationName: string;
      organizationType: string;
      role: string;
      isPrimary: boolean;
    }>('/departments/assign-chair', body),
  memberships: (departmentId?: string) =>
    api.get<
      {
        id: string;
        organizationNodeId: string;
        organizationName: string;
        organizationType: string;
        role: string;
        isPrimary: boolean;
      }[]
    >(departmentId ? `/memberships?departmentId=${encodeURIComponent(departmentId)}` : '/memberships'),
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

export type CourseOffering = {
  id: string;
  academicUnitId: string;
  unitCode: string;
  unitName: string;
  programme: string | null;
  contextLabel: string | null;
  levelLabel: string | null;
  displayTitle: string | null;
  requestingDepartment: string | null;
  owningDepartment: string | null;
  status: string;
};

export type CourseOfferingDetail = {
  offering: CourseOffering;
  requirements: { id: string; type: string; label: string; weight: number; source: string }[];
  outlines: {
    id: string;
    fileName: string;
    contentType: string | null;
    detectedContentType?: string | null;
    versionNo: number;
    extractionConfidence: number;
    needsReview: boolean;
    processingStatus?: string;
    extractionMethod?: string | null;
    processingMessage?: string | null;
    processingErrorCode?: string | null;
    createdAt: string;
    hasFile: boolean;
    warnings?: string[];
  }[];
};

export type SuitabilityCandidate = {
  lecturerId: string;
  name: string;
  department: string;
  score: number;
  classification: string;
  positives: string[];
  warnings: string[];
  missingEvidence: string[];
  breakdown: { key: string; label: string; earned: number; max: number }[];
  crossDepartment: boolean;
  load: string;
};

export const courseOfferingService = {
  list: () => api.get<CourseOffering[]>('/course-offerings'),
  get: (id: string) => api.get<CourseOfferingDetail>(`/course-offerings/${id}`),
  create: (body: {
    academicUnitId: string;
    programme?: string;
    contextLabel?: string;
    levelLabel?: string;
    displayTitle?: string;
    requestingDepartmentId?: string | null;
    owningDepartmentId?: string | null;
    requirements?: { type: string; label: string; weight?: number }[];
  }) => api.post<CourseOffering>('/course-offerings', body),
  /** Find an existing offering for the unit, or create one for allocate-by-context. */
  ensureForUnit: async (unit: {
    id: string;
    name: string;
    sourceDepartmentId?: string | null;
    requiredExpertise?: string[];
  }) => {
    const offerings = await api.get<CourseOffering[]>('/course-offerings');
    const existing = offerings.find((o) => o.academicUnitId === unit.id);
    if (existing) return existing;
    return api.post<CourseOffering>('/course-offerings', {
      academicUnitId: unit.id,
      displayTitle: unit.name,
      contextLabel: 'Department allocation',
      owningDepartmentId: unit.sourceDepartmentId || null,
      requirements: (unit.requiredExpertise || [])
        .map((label) => label.trim())
        .filter(Boolean)
        .map((label) => ({ type: 'EXPERTISE', label })),
    });
  },
  suitability: (id: string) => api.get<SuitabilityCandidate[]>(`/course-offerings/${id}/suitability`),
  allocate: (body: { courseOfferingId: string; lecturerId: string; decisionNote?: string }) =>
    api.post('/course-offerings/allocate', body),
  uploadOutline: (id: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.upload<{
      id: string;
      fileName: string;
      versionNo: number;
      extractionConfidence: number;
      needsReview: boolean;
      processingStatus?: string;
      extractionMethod?: string | null;
      processingMessage?: string | null;
      processingErrorCode?: string | null;
      warnings?: string[];
    }>(`/course-offerings/${id}/outline`, fd);
  },
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
