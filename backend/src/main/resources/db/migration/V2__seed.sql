-- Seed University of Nairobi demo tenant and MAT 204 workflow data

INSERT INTO tenants (id, name, code) VALUES
  ('11111111-1111-1111-1111-111111111111', 'University of Nairobi', 'UON');

INSERT INTO academic_years (id, tenant_id, label, start_date, end_date) VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', '2026/2027', '2026-09-01', '2027-08-31');

INSERT INTO semesters (id, tenant_id, academic_year_id, name, sequence_no) VALUES
  ('22222222-2222-2222-2222-222222222211', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222201', 'Semester 1', 1);

INSERT INTO organization_nodes (id, tenant_id, name, type, parent_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111', 'University of Nairobi', 'University', NULL),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '11111111-1111-1111-1111-111111111111', 'School of Engineering', 'School', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '11111111-1111-1111-1111-111111111111', 'Civil Engineering', 'Department', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', '11111111-1111-1111-1111-111111111111', 'School of Computing', 'School', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', '11111111-1111-1111-1111-111111111111', 'Computer Science', 'Department', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', '11111111-1111-1111-1111-111111111111', 'Information Technology', 'Department', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7', '11111111-1111-1111-1111-111111111111', 'School of Science', 'School', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', '11111111-1111-1111-1111-111111111111', 'Mathematics', 'Department', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9', '11111111-1111-1111-1111-111111111111', 'Physics', 'Department', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7');

INSERT INTO users (id, tenant_id, email, full_name, role, organization_node_id, password_hash) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '11111111-1111-1111-1111-111111111111', 'j.wanjiku@uonbi.ac.ke', 'Dr. Jane Wanjiku', 'DEPARTMENT_CHAIR', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'local'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '11111111-1111-1111-1111-111111111111', 'admin@uonbi.ac.ke', 'System Administrator', 'SUPER_ADMIN', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'local');

INSERT INTO lecturers (id, tenant_id, staff_number, full_name, email, organization_node_id, qualifications, current_workload, maximum_workload, availability_text, status) VALUES
  ('cccccccc-cccc-cccc-cccc-ccccccccccc1', '11111111-1111-1111-1111-111111111111', 'MAT/0241', 'Dr. Jane Wanjiku', 'j.wanjiku@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 'PhD Mathematics, University of Nairobi', 8, 12, 'Monday, Wednesday, Friday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc2', '11111111-1111-1111-1111-111111111111', 'MAT/0198', 'Prof. David Otieno', 'd.otieno@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 'PhD Applied Mathematics, Kenyatta University', 13, 12, 'Tuesday, Thursday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc3', '11111111-1111-1111-1111-111111111111', 'CS/0112', 'Dr. Peter Mwangi', 'p.mwangi@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'PhD Computer Science, University of Nairobi', 9, 12, 'Monday, Tuesday, Thursday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc4', '11111111-1111-1111-1111-111111111111', 'CS/0087', 'Dr. Grace Achieng', 'g.achieng@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'PhD Computer Science, Strathmore University', 6, 10, 'Wednesday, Thursday, Friday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc5', '11111111-1111-1111-1111-111111111111', 'MAT/0301', 'Mr. Samuel Kiplagat', 's.kiplagat@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 'MSc Mathematics, University of Nairobi', 5, 12, 'Monday–Friday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc6', '11111111-1111-1111-1111-111111111111', 'IT/0055', 'Dr. Esther Nyambura', 'e.nyambura@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'PhD Information Systems, JKUAT', 10, 12, 'Tuesday, Wednesday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc7', '11111111-1111-1111-1111-111111111111', 'CS/0034', 'Prof. Fredrick Omondi', 'f.omondi@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'PhD Computer Science, University of Nairobi', 12, 12, 'Monday, Friday', 'Active'),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc8', '11111111-1111-1111-1111-111111111111', 'MAT/0177', 'Dr. Mercy Wambui', 'm.wambui@uonbi.ac.ke', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 'PhD Statistics, Egerton University', 7, 12, 'Monday, Tuesday, Wednesday', 'Active');

INSERT INTO lecturer_expertise (id, tenant_id, lecturer_id, subject, level) VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd01', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Linear Algebra', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd02', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Statistics', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd03', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Calculus', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd04', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Numerical Methods', 'Moderate'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd05', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Algebra', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd06', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Calculus', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd07', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Linear Algebra', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd08', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', 'Database Systems', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd09', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', 'Software Engineering', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd10', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', 'Data Structures', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd11', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc4', 'Machine Learning', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd12', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc4', 'Artificial Intelligence', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd13', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc5', 'Numerical Methods', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd14', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc5', 'Linear Algebra', 'Moderate'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd15', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc6', 'Computer Networks', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd16', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc7', 'Operating Systems', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd17', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc7', 'Distributed Systems', 'Strong'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd18', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc8', 'Statistics', 'Excellent'),
  ('dddddddd-dddd-dddd-dddd-dddddddddd19', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-ccccccccccc8', 'Probability', 'Excellent');

INSERT INTO academic_units (id, tenant_id, code, name, source_department_id, contact_hours, student_count, academic_year_id, semester_id, required_expertise, status) VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', '11111111-1111-1111-1111-111111111111', 'MAT 204', 'Linear Algebra', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 4, 180, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Linear Algebra,Matrix Algebra,Mathematics', 'Unallocated'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', '11111111-1111-1111-1111-111111111111', 'CSC 305', 'Database Systems', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 3, 120, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Database Systems,SQL', 'Matched'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', '11111111-1111-1111-1111-111111111111', 'CSC 210', 'Data Structures', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 3, 150, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Data Structures,Algorithms', 'Published'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4', '11111111-1111-1111-1111-111111111111', 'MAT 210', 'Calculus II', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 4, 140, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Calculus,Mathematics', 'Unallocated'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee5', '11111111-1111-1111-1111-111111111111', 'ITC 220', 'Computer Networks', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 3, 95, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Computer Networks', 'Assigned'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee6', '11111111-1111-1111-1111-111111111111', 'CSC 410', 'Machine Learning', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 4, 88, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Machine Learning,Artificial Intelligence', 'Recommended'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee7', '11111111-1111-1111-1111-111111111111', 'MAT 108', 'Statistics I', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 3, 200, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Statistics', 'Published'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee8', '11111111-1111-1111-1111-111111111111', 'CSC 415', 'Distributed Systems', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 4, 64, '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'Distributed Systems,Operating Systems', 'Unallocated');

INSERT INTO teaching_requests (id, tenant_id, requesting_department_id, preferred_department_id, academic_unit_id, student_count, contact_hours, required_expertise, academic_year_id, semester_id, status, created_by, created_at) VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff1', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 180, 4, 'Linear Algebra', '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'AWAITING_RESPONSE', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '2026-09-02 10:00:00+00'),
  ('ffffffff-ffff-ffff-ffff-fffffffffff2', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', 120, 3, 'Database Systems', '22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222211', 'MATCHED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '2026-08-29 09:00:00+00');

INSERT INTO allocations (id, tenant_id, academic_unit_id, lecturer_id, teaching_request_id, match_score, status, created_by) VALUES
  ('99999999-9999-9999-9999-999999999901', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', NULL, NULL, 'PUBLISHED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'),
  ('99999999-9999-9999-9999-999999999902', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee5', 'cccccccc-cccc-cccc-cccc-ccccccccccc6', NULL, NULL, 'ASSIGNED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'),
  ('99999999-9999-9999-9999-999999999903', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee6', 'cccccccc-cccc-cccc-cccc-ccccccccccc4', NULL, 91, 'RECOMMENDED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'),
  ('99999999-9999-9999-9999-999999999904', '11111111-1111-1111-1111-111111111111', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee7', 'cccccccc-cccc-cccc-cccc-ccccccccccc8', NULL, NULL, 'PUBLISHED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1');

INSERT INTO approvals (id, tenant_id, allocation_id, status, submitted_by, submitted_at) VALUES
  ('88888888-8888-8888-8888-888888888801', '11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999902', 'PENDING', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '2026-08-31 12:00:00+00');

INSERT INTO timetable_entries (id, tenant_id, allocation_id, lecturer_id, academic_unit_id, day_of_week, start_time, end_time, room) VALUES
  ('77777777-7777-7777-7777-777777777701', '11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999901', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', 2, '09:00', '11:00', 'Lab 2'),
  ('77777777-7777-7777-7777-777777777702', '11111111-1111-1111-1111-111111111111', NULL, 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 1, '10:00', '12:00', 'LT 4'),
  ('77777777-7777-7777-7777-777777777703', '11111111-1111-1111-1111-111111111111', NULL, 'cccccccc-cccc-cccc-cccc-ccccccccccc1', NULL, 1, '10:00', '12:00', 'LT 1');

INSERT INTO conflicts (id, tenant_id, allocation_id, category, severity, description, related_entity, resolved) VALUES
  ('66666666-6666-6666-6666-666666666601', '11111111-1111-1111-1111-111111111111', NULL, 'Timetable', 'high', 'MAT 204 overlaps with CSC 302 — Monday, 10:00–12:00.', 'Dr. Jane Wanjiku', FALSE),
  ('66666666-6666-6666-6666-666666666602', '11111111-1111-1111-1111-111111111111', NULL, 'Workload', 'med', '13 / 12 teaching hours — 1 hour over the departmental maximum.', 'Prof. David Otieno', FALSE),
  ('66666666-6666-6666-6666-666666666603', '11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999902', 'Availability', 'high', 'Assigned to ITC 220 outside her declared availability window (Tue, Wed only).', 'Dr. Esther Nyambura', FALSE);

INSERT INTO audit_logs (id, tenant_id, actor_id, action, entity_type, entity_id, details) VALUES
  ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'Request submitted', 'TeachingRequest', 'ffffffff-ffff-ffff-ffff-fffffffffff1', 'CS requested MAT 204 from Mathematics');
