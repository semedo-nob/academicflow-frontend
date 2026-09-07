export { api, setTenantId } from './api';
export { platformApi } from './platformApi';
export {
  authService,
  lecturerService,
  unitService,
  requestService,
  allocationService,
  organizationService,
  dashboardService,
  conflictService,
  approvalService,
  workloadService,
  timetableService,
  importService,
  auditService,
  reportService,
  userService,
  adminService,
  searchService,
  exportService,
} from './lecturerService';
export type { ApiAllocation, ApiTimetableEntry, ApiWorkloadSummary, ApiTeachingRequest } from './lecturerService';
