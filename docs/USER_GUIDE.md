# AcademicFlow User Guide (Institutions)

For institution administrators, department chairs, and other campus roles. Platform product-owner steps are in [SUPER_ADMIN_GUIDE.md](SUPER_ADMIN_GUIDE.md).

## Before you start

1. Your institution registers at `/login` → **Register institution**.  
2. The AcademicFlow product owner **approves** the institution.  
3. The institution admin signs in and completes **Set up your institution** (`/onboarding`): create departments and invite one chair per department.

You cannot access `/platform` — that console is for the product owner only.

## Core workflows

### Assign chairs
- Prefer the onboarding wizard, or **Administration → Users & invitations**  
- Invite as Department Chair (copy the invite link) and/or **Assign department chair** (one active chair per department)

### Cross-department teaching request
1. Chair A opens **Teaching Requests** → **+ New request**  
2. Choose preferred source department, unit, expertise, and optional **briefing**  
3. Attach a **course outline** or supporting document  
4. Chair B sees it under **Incoming** → **Open thread** to download docs, reply, post eligibility notes, or notify academic authority  
5. Accept or Decline → then **Find candidates** or **Allocate by context**

### Import department timetable
1. **Import / Export** → upload CSV, Excel, or PDF (scanned PDFs use OCR and can take longer)  
2. Map columns → validate → commit  
3. Department chairs only commit rows for **their** department; other departments are skipped with warnings  
4. Use the post-import links to review **Unallocated** units or open **Allocate by context**

### Allocate by expertise and workload
- From an unallocated unit → **Allocate by context**  
- Or from an accepted request → recommendations / allocate-by-context  
- Review suitability scores, then allocate (override reason recorded when needed)

### Finish the cycle
Allocation board → resolve **Conflicts** → **Approvals** → timetable / export / reports
