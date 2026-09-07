-- Align role catalog: add LECTURER; broaden SUPER_ADMIN permissions for platform ops

INSERT INTO roles (id, tenant_id, code, name, permissions, description)
SELECT '44444444-4444-4444-4444-444444444405',
       '11111111-1111-1111-1111-111111111111',
       'LECTURER',
       'Lecturer',
       'dashboard,timetable,workload,units',
       'Own timetable and workload'
WHERE NOT EXISTS (
  SELECT 1 FROM roles
  WHERE tenant_id = '11111111-1111-1111-1111-111111111111' AND code = 'LECTURER'
);

UPDATE roles
SET permissions = 'users,roles,institutions,organization,years,semesters,rules,settings,imports,audit,allocations,approvals,dashboard,lecturers,units,requests,recommendations,workload,timetable,conflicts,reports',
    description = 'Full platform access including institutions'
WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
  AND code = 'SUPER_ADMIN';
