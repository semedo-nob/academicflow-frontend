import type {
  AcademicUnit,
  ActivityItem,
  ApprovalItem,
  BoardCard,
  BoardColumnKey,
  Candidate,
  ConflictItem,
  DashboardStats,
  Lecturer,
  OrganizationNode,
  TeachingRequest,
  User,
} from '../types';

export const CURRENT_USER: User = {
  id: 'u-1',
  name: 'Dr. Jane Wanjiku',
  email: 'j.wanjiku@uonbi.ac.ke',
  role: 'DEPARTMENT_CHAIR',
  initials: 'JW',
  departmentName: 'Computer Science',
  organizationNodeId: 'org-cs',
};

export const TENANT_ID = 'tenant-uon';
export const ACADEMIC_YEAR = '2026/2027';
export const SEMESTER = 'Semester 1';

export const ORG_NODES: OrganizationNode[] = [
  { id: 'org-uni', tenantId: TENANT_ID, name: 'University of Nairobi', type: 'University', parentId: null },
  { id: 'org-eng', tenantId: TENANT_ID, name: 'School of Engineering', type: 'School', parentId: 'org-uni' },
  { id: 'org-civil', tenantId: TENANT_ID, name: 'Civil Engineering', type: 'Department', parentId: 'org-eng' },
  { id: 'org-mech', tenantId: TENANT_ID, name: 'Mechanical Engineering', type: 'Department', parentId: 'org-eng' },
  { id: 'org-comp', tenantId: TENANT_ID, name: 'School of Computing', type: 'School', parentId: 'org-uni' },
  { id: 'org-cs', tenantId: TENANT_ID, name: 'Computer Science', type: 'Department', parentId: 'org-comp', unitCount: 68, lecturerCount: 42 },
  { id: 'org-it', tenantId: TENANT_ID, name: 'Information Technology', type: 'Department', parentId: 'org-comp' },
  { id: 'org-sci', tenantId: TENANT_ID, name: 'School of Science', type: 'School', parentId: 'org-uni' },
  { id: 'org-mat', tenantId: TENANT_ID, name: 'Mathematics', type: 'Department', parentId: 'org-sci' },
  { id: 'org-phy', tenantId: TENANT_ID, name: 'Physics', type: 'Department', parentId: 'org-sci' },
];

export const LECTURERS: Lecturer[] = [
  {
    id: 'lec-1', staffNumber: 'MAT/0241', name: 'Dr. Jane Wanjiku', email: 'j.wanjiku@uonbi.ac.ke',
    departmentId: 'org-mat', department: 'Mathematics',
    qualifications: 'PhD Mathematics, University of Nairobi',
    currentWorkload: 8, maximumWorkload: 12, status: 'Active',
    availability: 'Monday, Wednesday, Friday', availabilityLabel: 'Available', initials: 'JW',
    expertise: ['Linear Algebra', 'Statistics'],
    expertiseDetail: [
      { subject: 'Linear Algebra', level: 'Excellent' },
      { subject: 'Statistics', level: 'Strong' },
      { subject: 'Calculus', level: 'Strong' },
      { subject: 'Numerical Methods', level: 'Moderate' },
    ],
    units: ['MAT 204 · Linear Algebra', 'MAT 108 · Statistics I'],
  },
  {
    id: 'lec-2', staffNumber: 'MAT/0198', name: 'Prof. David Otieno', email: 'd.otieno@uonbi.ac.ke',
    departmentId: 'org-mat', department: 'Mathematics',
    qualifications: 'PhD Applied Mathematics, Kenyatta University',
    currentWorkload: 13, maximumWorkload: 12, status: 'Active',
    availability: 'Tuesday, Thursday', availabilityLabel: 'Limited', initials: 'DO',
    expertise: ['Algebra', 'Calculus'],
    expertiseDetail: [
      { subject: 'Algebra', level: 'Excellent' },
      { subject: 'Calculus', level: 'Excellent' },
      { subject: 'Linear Algebra', level: 'Strong' },
      { subject: 'Topology', level: 'Moderate' },
    ],
    units: ['MAT 210 · Calculus II', 'MAT 150 · Applied Mathematics'],
  },
  {
    id: 'lec-3', staffNumber: 'CS/0112', name: 'Dr. Peter Mwangi', email: 'p.mwangi@uonbi.ac.ke',
    departmentId: 'org-cs', department: 'Computer Science',
    qualifications: 'PhD Computer Science, University of Nairobi',
    currentWorkload: 9, maximumWorkload: 12, status: 'Active',
    availability: 'Monday, Tuesday, Thursday', availabilityLabel: 'Available', initials: 'PM',
    expertise: ['Databases', 'Software Engineering'],
    expertiseDetail: [
      { subject: 'Database Systems', level: 'Excellent' },
      { subject: 'Software Engineering', level: 'Strong' },
      { subject: 'Data Structures', level: 'Strong' },
    ],
    units: ['CSC 305 · Database Systems', 'CSC 210 · Data Structures'],
  },
  {
    id: 'lec-4', staffNumber: 'CS/0087', name: 'Dr. Grace Achieng', email: 'g.achieng@uonbi.ac.ke',
    departmentId: 'org-cs', department: 'Computer Science',
    qualifications: 'PhD Computer Science, Strathmore University',
    currentWorkload: 6, maximumWorkload: 10, status: 'Active',
    availability: 'Wednesday, Thursday, Friday', availabilityLabel: 'Available', initials: 'GA',
    expertise: ['Artificial Intelligence', 'Machine Learning'],
    expertiseDetail: [
      { subject: 'Machine Learning', level: 'Excellent' },
      { subject: 'Artificial Intelligence', level: 'Excellent' },
      { subject: 'Statistics', level: 'Strong' },
    ],
    units: ['CSC 410 · Machine Learning'],
  },
  {
    id: 'lec-5', staffNumber: 'MAT/0301', name: 'Mr. Samuel Kiplagat', email: 's.kiplagat@uonbi.ac.ke',
    departmentId: 'org-mat', department: 'Mathematics',
    qualifications: 'MSc Mathematics, University of Nairobi',
    currentWorkload: 5, maximumWorkload: 12, status: 'Active',
    availability: 'Monday–Friday', availabilityLabel: 'Available', initials: 'SK',
    expertise: ['Numerical Methods'],
    expertiseDetail: [
      { subject: 'Numerical Methods', level: 'Excellent' },
      { subject: 'Calculus', level: 'Strong' },
      { subject: 'Linear Algebra', level: 'Moderate' },
    ],
    units: ['MAT 220 · Numerical Analysis'],
  },
  {
    id: 'lec-6', staffNumber: 'IT/0055', name: 'Dr. Esther Nyambura', email: 'e.nyambura@uonbi.ac.ke',
    departmentId: 'org-it', department: 'Information Technology',
    qualifications: 'PhD Information Systems, JKUAT',
    currentWorkload: 10, maximumWorkload: 12, status: 'Active',
    availability: 'Tuesday, Wednesday', availabilityLabel: 'Limited', initials: 'EN',
    expertise: ['Computer Networks'],
    expertiseDetail: [
      { subject: 'Computer Networks', level: 'Excellent' },
      { subject: 'Systems Security', level: 'Strong' },
    ],
    units: ['ITC 220 · Computer Networks'],
  },
  {
    id: 'lec-7', staffNumber: 'CS/0034', name: 'Prof. Fredrick Omondi', email: 'f.omondi@uonbi.ac.ke',
    departmentId: 'org-cs', department: 'Computer Science',
    qualifications: 'PhD Computer Science, University of Nairobi',
    currentWorkload: 12, maximumWorkload: 12, status: 'Active',
    availability: 'Monday, Friday', availabilityLabel: 'At limit', initials: 'FO',
    expertise: ['Operating Systems', 'Distributed Systems'],
    expertiseDetail: [
      { subject: 'Operating Systems', level: 'Excellent' },
      { subject: 'Distributed Systems', level: 'Strong' },
    ],
    units: ['CSC 210 · Data Structures', 'CSC 415 · Distributed Systems'],
  },
  {
    id: 'lec-8', staffNumber: 'MAT/0177', name: 'Dr. Mercy Wambui', email: 'm.wambui@uonbi.ac.ke',
    departmentId: 'org-mat', department: 'Mathematics',
    qualifications: 'PhD Statistics, Egerton University',
    currentWorkload: 7, maximumWorkload: 12, status: 'Active',
    availability: 'Monday, Tuesday, Wednesday', availabilityLabel: 'Available', initials: 'MW',
    expertise: ['Statistics', 'Probability'],
    expertiseDetail: [
      { subject: 'Statistics', level: 'Excellent' },
      { subject: 'Probability', level: 'Excellent' },
      { subject: 'Linear Algebra', level: 'Moderate' },
    ],
    units: ['MAT 108 · Statistics I'],
  },
];

export const UNITS: AcademicUnit[] = [
  { id: 'u-1', code: 'MAT 204', name: 'Linear Algebra', sourceDepartmentId: 'org-mat', sourceDepartment: 'Mathematics', requestingDepartment: 'Computer Science', contactHours: 4, studentCount: 180, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Linear Algebra', 'Matrix Algebra', 'Mathematics'], status: 'Unallocated', lecturerName: null },
  { id: 'u-2', code: 'CSC 305', name: 'Database Systems', sourceDepartmentId: 'org-cs', sourceDepartment: 'Computer Science', requestingDepartment: 'Information Science', contactHours: 3, studentCount: 120, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Database Systems', 'SQL'], status: 'Matched', lecturerName: 'Dr. Peter Mwangi' },
  { id: 'u-3', code: 'CSC 210', name: 'Data Structures', sourceDepartmentId: 'org-cs', sourceDepartment: 'Computer Science', requestingDepartment: null, contactHours: 3, studentCount: 150, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Data Structures', 'Algorithms'], status: 'Published', lecturerName: 'Dr. Peter Mwangi' },
  { id: 'u-4', code: 'MAT 210', name: 'Calculus II', sourceDepartmentId: 'org-mat', sourceDepartment: 'Mathematics', requestingDepartment: null, contactHours: 4, studentCount: 140, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Calculus', 'Mathematics'], status: 'Unallocated', lecturerName: null },
  { id: 'u-5', code: 'ITC 220', name: 'Computer Networks', sourceDepartmentId: 'org-it', sourceDepartment: 'Information Technology', requestingDepartment: null, contactHours: 3, studentCount: 95, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Computer Networks'], status: 'Assigned', lecturerName: 'Dr. Esther Nyambura' },
  { id: 'u-6', code: 'CSC 410', name: 'Machine Learning', sourceDepartmentId: 'org-cs', sourceDepartment: 'Computer Science', requestingDepartment: null, contactHours: 4, studentCount: 88, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Machine Learning', 'Artificial Intelligence'], status: 'Recommended', lecturerName: 'Dr. Grace Achieng' },
  { id: 'u-7', code: 'MAT 108', name: 'Statistics I', sourceDepartmentId: 'org-mat', sourceDepartment: 'Mathematics', requestingDepartment: null, contactHours: 3, studentCount: 200, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Statistics'], status: 'Published', lecturerName: 'Dr. Mercy Wambui' },
  { id: 'u-8', code: 'CSC 415', name: 'Distributed Systems', sourceDepartmentId: 'org-cs', sourceDepartment: 'Computer Science', requestingDepartment: null, contactHours: 4, studentCount: 64, academicYear: ACADEMIC_YEAR, semester: SEMESTER, requiredExpertise: ['Distributed Systems', 'Operating Systems'], status: 'Unallocated', lecturerName: null },
];

export const REQUESTS: TeachingRequest[] = [
  { id: 'TR-024', requestingDepartment: 'Computer Science', sourceDepartment: 'Mathematics', academicUnit: 'MAT 204 — Linear Algebra', studentCount: 180, contactHours: 4, requiredExpertise: 'Linear Algebra', semester: SEMESTER, academicYear: ACADEMIC_YEAR, status: 'Awaiting Mathematics Response', createdAt: '02 Sep 2026', direction: 'outgoing' },
  { id: 'TR-025', requestingDepartment: 'Information Science', sourceDepartment: 'Computer Science', academicUnit: 'CSC 305 — Database Systems', studentCount: 120, contactHours: 3, requiredExpertise: 'Database Systems', semester: SEMESTER, academicYear: ACADEMIC_YEAR, status: 'Matched', createdAt: '29 Aug 2026', direction: 'incoming' },
  { id: 'TR-018', requestingDepartment: 'Civil Engineering', sourceDepartment: 'Mathematics', academicUnit: 'MAT 150 — Applied Mathematics', studentCount: 96, contactHours: 3, requiredExpertise: 'Applied Mathematics', semester: SEMESTER, academicYear: ACADEMIC_YEAR, status: 'Completed', createdAt: '14 Aug 2026', direction: 'incoming' },
  { id: 'TR-030', requestingDepartment: 'Computer Science', sourceDepartment: 'Mathematics', academicUnit: 'MAT 220 — Numerical Analysis', studentCount: 70, contactHours: 3, requiredExpertise: 'Numerical Methods', semester: SEMESTER, academicYear: ACADEMIC_YEAR, status: 'Draft', createdAt: '01 Sep 2026', direction: 'outgoing' },
  { id: 'TR-011', requestingDepartment: 'Computer Science', sourceDepartment: 'Information Technology', academicUnit: 'ITC 220 — Computer Networks', studentCount: 95, contactHours: 3, requiredExpertise: 'Computer Networks', semester: SEMESTER, academicYear: ACADEMIC_YEAR, status: 'Completed', createdAt: '10 Aug 2026', direction: 'outgoing' },
];

export const CONFLICTS: ConflictItem[] = [
  { id: 'c-1', severity: 'high', category: 'Timetable', who: 'Dr. Jane Wanjiku', text: 'MAT 204 overlaps with CSC 302 — Monday, 10:00–12:00.', action: 'Resolve' },
  { id: 'c-2', severity: 'med', category: 'Workload', who: 'Prof. David Otieno', text: '13 / 12 teaching hours — 1 hour over the departmental maximum.', action: 'Review Allocation' },
  { id: 'c-3', severity: 'high', category: 'Availability', who: 'Dr. Esther Nyambura', text: 'Assigned to ITC 220 outside her declared availability window (Tue, Wed only).', action: 'Reassign Slot' },
];

export const ACTIVITY: ActivityItem[] = [
  { text: '<b>Dr. Wanjiku</b> assigned MAT 204 to <b>Prof. Otieno</b>', time: '12 minutes ago' },
  { text: '<b>Mathematics</b> accepted request <b>TR-024</b>', time: '1 hour ago' },
  { text: 'Allocation <b>A-103</b> submitted for approval', time: '3 hours ago' },
  { text: 'Timetable conflict detected for <b>CSC 301</b>', time: 'Yesterday, 16:42' },
];

export const APPROVALS: ApprovalItem[] = [
  { id: 'A-103', unit: 'MAT 204 — Linear Algebra', route: 'Computer Science → Mathematics', lecturer: 'Dr. Jane Wanjiku', submittedBy: 'Dr. Kamau', date: '04 Sep 2026', conflicts: 0, status: 'Pending Approval' },
  { id: 'A-098', unit: 'CSC 305 — Database Systems', route: 'Information Science → Computer Science', lecturer: 'Dr. Peter Mwangi', submittedBy: 'Prof. Omondi', date: '02 Sep 2026', conflicts: 0, status: 'Pending Approval' },
  { id: 'A-095', unit: 'ITC 220 — Computer Networks', route: 'Internal · Information Technology', lecturer: 'Dr. Esther Nyambura', submittedBy: 'Dr. Kamau', date: '31 Aug 2026', conflicts: 1, status: 'Pending Approval' },
  { id: 'A-081', unit: 'CSC 210 — Data Structures', route: 'Internal · Computer Science', lecturer: 'Dr. Peter Mwangi', submittedBy: 'Dr. Wanjiku', date: '20 Aug 2026', conflicts: 0, status: 'Approved' },
  { id: 'A-076', unit: 'MAT 108 — Statistics I', route: 'Internal · Mathematics', lecturer: 'Dr. Mercy Wambui', submittedBy: 'Prof. Otieno', date: '18 Aug 2026', conflicts: 0, status: 'Approved' },
];

export const INITIAL_BOARD: Record<BoardColumnKey, BoardCard[]> = {
  unallocated: [
    { code: 'MAT 210', name: 'Calculus II', students: 140, hours: 4, flow: 'Mathematics', candidate: null },
    { code: 'CSC 415', name: 'Distributed Systems', students: 64, hours: 4, flow: 'Computer Science', candidate: null },
  ],
  recommended: [
    { code: 'CSC 410', name: 'Machine Learning', students: 88, hours: 4, flow: 'Computer Science', candidate: { name: 'Dr. Grace Achieng', score: 91 } },
  ],
  assigned: [
    { code: 'ITC 220', name: 'Computer Networks', students: 95, hours: 3, flow: 'Information Technology', candidate: { name: 'Dr. Esther Nyambura', score: null } },
  ],
  approval: [
    { code: 'MAT 204', name: 'Linear Algebra', students: 180, hours: 4, flow: 'Computer Science → Mathematics', candidate: { name: 'Dr. Jane Wanjiku', score: 96 } },
  ],
  published: [
    { code: 'CSC 210', name: 'Data Structures', students: 150, hours: 3, flow: 'Computer Science', candidate: { name: 'Dr. Peter Mwangi', score: null } },
  ],
};

export const MAT204_CANDIDATES: Candidate[] = [
  {
    rank: 1, top: true, lecturerId: 'lec-1', name: 'Dr. Jane Wanjiku', dept: 'Mathematics Department', score: 96,
    metrics: { expertise: 98, availability: 100, workload: 91, studentLoad: 94, policy: 100 },
    load: '8 / 12 hrs', availability: 'Monday, Wednesday',
    reasons: ['Strong Linear Algebra expertise', 'Available during requested period', 'Within workload limit', 'No timetable conflict', 'Eligible for cross-department teaching'],
    hardConstraints: { qualified: true, available: true, noConflict: true, workloadOk: true, policyOk: true },
  },
  {
    rank: 2, lecturerId: 'lec-2', name: 'Prof. David Otieno', dept: 'Mathematics Department', score: 87,
    metrics: { expertise: 92, availability: 76, workload: 58, studentLoad: 90, policy: 100 },
    load: '13 / 12 hrs', availability: 'Tuesday, Thursday',
    reasons: ['Strong Algebra expertise', 'No timetable conflict', 'Eligible for cross-department teaching'],
    warn: 'Currently 1 hour over their maximum workload',
    hardConstraints: { qualified: true, available: true, noConflict: true, workloadOk: false, policyOk: true },
  },
  {
    rank: 3, lecturerId: 'lec-5', name: 'Mr. Samuel Kiplagat', dept: 'Mathematics Department', score: 74,
    metrics: { expertise: 68, availability: 100, workload: 97, studentLoad: 80, policy: 100 },
    load: '5 / 12 hrs', availability: 'Monday–Friday',
    reasons: ['Available across the full week', 'Comfortably within workload limit', 'Eligible for cross-department teaching'],
    warn: 'Moderate — not primary — expertise in Linear Algebra',
    hardConstraints: { qualified: true, available: true, noConflict: true, workloadOk: true, policyOk: true },
  },
];

export const DASHBOARD_STATS: DashboardStats = {
  totalLecturers: 42,
  academicUnits: 68,
  allocated: 54,
  pending: 14,
  conflicts: 3,
  crossDeptRequests: 8,
  completionPct: 79,
  allocatedCount: 41,
  awaitingApproval: 13,
  pendingUnits: 11,
  conflicted: 3,
  underloaded: 9,
  optimal: 21,
  nearLimit: 8,
  overloaded: 4,
};

export const REPORT_CATS = [
  'Lecturer Workload', 'Department Allocation', 'School / Faculty Allocation', 'University-wide Allocation',
  'Cross-Department Teaching', 'Unallocated Units', 'Conflict Report', 'Timetable Report',
  'Teaching Request Report', 'Approval Report',
];

export const ADMIN_SECTIONS: [string, string][] = [
  ['Users', 'Manage every account and its assigned role.'],
  ['Roles & Permissions', 'Control what each role can see and do.'],
  ['Institutions', 'Manage the universities on this platform.'],
  ['Organization Types', 'Define the hierarchy levels your institution uses.'],
  ['Academic Years', 'Set up academic years and their date ranges.'],
  ['Semesters', 'Configure semesters within each academic year.'],
  ['Institutional Rules', 'Workload limits, approval requirements, cross-department policy.'],
  ['Import Mapping Profiles', 'Saved field mappings for repeat imports.'],
  ['System Settings', 'General platform configuration.'],
  ['Audit Logs', 'A full history of changes made across the platform.'],
];
