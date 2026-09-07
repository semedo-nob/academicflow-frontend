export type Role =
  | 'SUPER_ADMIN'
  | 'INSTITUTION_ADMIN'
  | 'SCHOOL_ADMIN'
  | 'DEPARTMENT_CHAIR'
  | 'LECTURER'
  | 'VIEWER';
/** Backend may also send SCHOOL_DEAN — normalized via access.normalizeRole */

export type RequestStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'SEARCHING'
  | 'RECOMMENDED'
  | 'AWAITING_RESPONSE'
  | 'ASSIGNED'
  | 'CONFLICT'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'MATCHED'
  | 'COMPLETED';

export type AllocationStatus =
  | 'UNALLOCATED'
  | 'RECOMMENDED'
  | 'ASSIGNED'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'CANCELLED';

export type ExpertiseLevel = 'Excellent' | 'Strong' | 'Moderate' | 'Basic';

export type ConflictSeverity = 'high' | 'med' | 'low';
export type ConflictCategory =
  | 'Timetable'
  | 'Workload'
  | 'Availability'
  | 'Expertise'
  | 'Policy'
  | 'Duplicate'
  | 'Room';

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  initials: string;
  departmentName: string;
  organizationNodeId?: string;
}

export interface OrganizationNode {
  id: string;
  tenantId: string;
  name: string;
  type: string;
  parentId: string | null;
  unitCount?: number;
  lecturerCount?: number;
}

export interface LecturerExpertise {
  subject: string;
  level: ExpertiseLevel;
}

export interface Lecturer {
  id: string;
  staffNumber: string;
  name: string;
  email: string;
  departmentId: string;
  department: string;
  qualifications: string;
  currentWorkload: number;
  maximumWorkload: number;
  status: 'Active' | 'Inactive';
  availability: string;
  availabilityLabel: 'Available' | 'Limited' | 'At limit';
  initials: string;
  expertise: string[];
  expertiseDetail: LecturerExpertise[];
  units: string[];
}

export interface AcademicUnit {
  id: string;
  code: string;
  name: string;
  sourceDepartmentId: string;
  sourceDepartment: string;
  requestingDepartment?: string | null;
  contactHours: number;
  studentCount: number;
  academicYear: string;
  semester: string;
  requiredExpertise: string[];
  status: string;
  lecturerName?: string | null;
}

export interface TeachingRequest {
  id: string;
  requestingDepartment: string;
  sourceDepartment: string;
  academicUnit: string;
  studentCount: number;
  contactHours: number;
  requiredExpertise: string;
  semester: string;
  academicYear: string;
  status: string;
  createdAt: string;
  direction: 'outgoing' | 'incoming';
}

export interface CandidateMetrics {
  expertise: number;
  availability: number;
  workload: number;
  studentLoad: number;
  policy: number;
}

export interface Candidate {
  rank: number;
  top?: boolean;
  lecturerId: string;
  name: string;
  dept: string;
  score: number;
  metrics: CandidateMetrics;
  load: string;
  availability: string;
  reasons: string[];
  warn?: string;
  hardConstraints: {
    qualified: boolean;
    available: boolean;
    noConflict: boolean;
    workloadOk: boolean;
    policyOk: boolean;
  };
}

export interface BoardCard {
  code: string;
  name: string;
  students: number;
  hours: number;
  flow: string;
  candidate: { name: string; score: number | null } | null;
}

export interface ConflictItem {
  id: string;
  severity: ConflictSeverity;
  category: ConflictCategory;
  who: string;
  text: string;
  action: string;
}

export interface ApprovalItem {
  id: string;
  unit: string;
  route: string;
  lecturer: string;
  submittedBy: string;
  date: string;
  conflicts: number;
  status: string;
}

export interface ActivityItem {
  text: string;
  time: string;
}

export interface DashboardStats {
  totalLecturers: number;
  academicUnits: number;
  allocated: number;
  pending: number;
  conflicts: number;
  crossDeptRequests: number;
  completionPct: number;
  allocatedCount: number;
  awaitingApproval: number;
  pendingUnits: number;
  conflicted: number;
  underloaded: number;
  optimal: number;
  nearLimit: number;
  overloaded: number;
}

export type BoardColumnKey =
  | 'unallocated'
  | 'recommended'
  | 'assigned'
  | 'approval'
  | 'published';
