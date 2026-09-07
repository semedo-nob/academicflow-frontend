import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { allocationService, unitService, type ApiAllocation } from '../services';
import { Badge, initialsAvatar } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DrawerCloseButton, PageHead } from '../components/ui/Drawer';
import { IconCheck, IconChevRight, IconExchange } from '../components/ui/Icons';
import type { AcademicUnit, BoardCard, BoardColumnKey } from '../types';

const BOARD_COLS: [BoardColumnKey, string][] = [
  ['unallocated', 'Unallocated'],
  ['recommended', 'Recommended'],
  ['assigned', 'Assigned'],
  ['approval', 'Awaiting Approval'],
  ['published', 'Published'],
];

function mapStatusToCol(status: string): BoardColumnKey {
  const s = status.toUpperCase().replace(/ /g, '_');
  if (s.includes('PUBLISH') || s === 'APPROVED') return 'published';
  if (s.includes('APPROVAL') || s.includes('AWAITING_APPROVAL')) return 'approval';
  if (s.includes('RECOMMEND')) return 'recommended';
  if (s.includes('ASSIGN') || s.includes('CONFLICT') || s.includes('MATCH')) return 'assigned';
  return 'unallocated';
}

function buildBoard(units: AcademicUnit[], allocations: ApiAllocation[]): Record<BoardColumnKey, (BoardCard & { allocationId?: string; unitId: string })[]> {
  const byUnit = new Map(allocations.map((a) => [a.academicUnitId, a]));
  const board: Record<BoardColumnKey, (BoardCard & { allocationId?: string; unitId: string })[]> = {
    unallocated: [],
    recommended: [],
    assigned: [],
    approval: [],
    published: [],
  };
  units.forEach((u) => {
    const alloc = byUnit.get(u.id);
    const col = alloc ? mapStatusToCol(alloc.status) : mapStatusToCol(u.status);
    board[col].push({
      code: u.code,
      name: u.name,
      students: u.studentCount,
      hours: u.contactHours,
      flow: u.sourceDepartment,
      candidate: alloc?.lecturerName
        ? { name: alloc.lecturerName, score: alloc.matchScore }
        : u.lecturerName
          ? { name: u.lecturerName, score: null }
          : null,
      allocationId: alloc?.id,
      unitId: u.id,
    });
  });
  return board;
}

function AllocationDrawer({
  card,
  col,
  onClose,
  onSubmit,
}: {
  card: BoardCard & { allocationId?: string; unitId: string };
  col: BoardColumnKey;
  onClose: () => void;
  onSubmit: () => void;
}) {
  const stageDone: Record<BoardColumnKey, number> = {
    unallocated: 0,
    recommended: 1,
    assigned: 2,
    approval: 3,
    published: 5,
  };
  const stages = ['Request', 'Match', 'Assign', 'Conflict check', 'Approval', 'Publish'];
  const label = BOARD_COLS.find((c) => c[0] === col)![1];
  const done = stageDone[col];

  return (
    <>
      <div className="drawer-head">
        <div>
          <div className="mono cell-sub">{card.code}</div>
          <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>{card.name}</div>
          <Badge status={label} />
        </div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">
        <div className="def-list">
          <div className="def-row">
            <span className="def-label">Route</span>
            <span className="def-value">{card.flow}</span>
          </div>
          <div className="def-row">
            <span className="def-label">Lecturer</span>
            <span className="def-value">{card.candidate ? card.candidate.name : 'Unassigned'}</span>
          </div>
          <div className="def-row">
            <span className="def-label">Students</span>
            <span className="def-value">{card.students}</span>
          </div>
          <div className="def-row">
            <span className="def-label">Contact hours</span>
            <span className="def-value">{card.hours}</span>
          </div>
        </div>
        <div className="section-title" style={{ marginTop: 18 }}>
          Progress
        </div>
        <div className="steps-row" style={{ flexWrap: 'wrap', marginTop: 12 }}>
          {stages.map((s, i) => (
            <div key={s} style={{ display: 'contents' }}>
              <div className={`step-item ${i < done ? 'done' : i === done ? 'current' : ''}`}>
                <div className="step-num">{i < done ? '✓' : i + 1}</div>
                <span className="step-label">{s}</span>
              </div>
              {i < stages.length - 1 && <div className="step-line" />}
            </div>
          ))}
        </div>
        <div className="section-title" style={{ marginTop: 20 }}>
          Validation
        </div>
        <div className="rec-why" style={{ marginTop: 8 }}>
          {['Qualified', 'Available', 'Workload within limit', 'No timetable conflict', 'Cross-department policy satisfied'].map(
            (t) => (
              <div className="rec-why-item" key={t}>
                <IconCheck />
                <span>{t}</span>
              </div>
            ),
          )}
        </div>
      </div>
      <div className="drawer-foot">
        <Button variant="danger-text" style={{ border: 'none' }} onClick={onClose}>
          Close
        </Button>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          {card.allocationId && col === 'assigned' && (
            <Button size="sm" variant="primary" onClick={onSubmit}>
              Submit for approval
            </Button>
          )}
        </div>
      </div>
    </>
  );
}

export function AllocationPage() {
  const { openDrawer, closeDrawer } = useApp();
  const navigate = useNavigate();
  const [tick, setTick] = useState(0);
  const { data: units } = useAsyncData(() => unitService.list(), [], [tick]);
  const { data: allocations } = useAsyncData(() => allocationService.list(), [], [tick]);
  const board = useMemo(() => buildBoard(units, allocations), [units, allocations]);

  const refresh = () => setTick((t) => t + 1);

  const submitApproval = async (allocationId: string) => {
    try {
      await allocationService.submit(allocationId);
      closeDrawer();
      refresh();
      navigate('/approvals');
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Submit failed');
    }
  };

  return (
    <>
      <PageHead
        title="Allocation Board"
        subtitle="Live allocation status from the API — recommendation ≠ approval ≠ published."
        actions={
          <div className="filters-bar" style={{ margin: 0 }}>
            <Button size="sm" onClick={refresh}>
              Refresh
            </Button>
            <div className="filter-chip">
              Semester <IconChevRight />
            </div>
          </div>
        }
      />
      <div className="kanban-wrap">
        {BOARD_COLS.map(([key, label]) => (
          <div key={key} className="kanban-col">
            <div className="kanban-col-head">
              <span className="kanban-col-title">{label}</span>
              <span className="kanban-col-count">{board[key].length}</span>
            </div>
            {board[key].map((c) => (
              <div
                key={c.unitId}
                className="kanban-card"
                onClick={() =>
                  openDrawer(
                    <AllocationDrawer
                      card={c}
                      col={key}
                      onClose={closeDrawer}
                      onSubmit={() => c.allocationId && submitApproval(c.allocationId)}
                    />,
                  )
                }
              >
                <div className="kanban-card-code">{c.code}</div>
                <div className="kanban-card-title">{c.name}</div>
                <div className="kanban-flow">
                  <IconExchange /> {c.flow}
                </div>
                <div className="kanban-card-meta">
                  {c.students} students · {c.hours} hrs
                </div>
                {c.candidate && (
                  <div className="kanban-candidate">
                    <div className="avatar-sm" style={{ width: 20, height: 20, fontSize: 9 }}>
                      {initialsAvatar(c.candidate.name)}
                    </div>
                    <span>{c.candidate.name}</span>
                    {c.candidate.score != null && <span className="match">{c.candidate.score}%</span>}
                  </div>
                )}
              </div>
            ))}
          </div>
        ))}
      </div>
    </>
  );
}
