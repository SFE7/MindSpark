import { describe, it, expect, beforeEach } from 'vitest';
import { DrawingPositionGuard } from './drawing-position-guard';

describe('DrawingPositionGuard', () => {
  let guard: DrawingPositionGuard;

  beforeEach(() => {
    guard = new DrawingPositionGuard();
  });

  it('tracks local drop and protects against stale server poll', () => {
    const seq = guard.registerDrop('draw-1', 40, 60);
    expect(guard.shouldApplyServerOffset('draw-1', 0, 0)).toBe(false);

    guard.onSaveSuccess('draw-1', seq, 40, 60);

    // Matching server response clears guard
    expect(guard.shouldApplyServerOffset('draw-1', 40, 60)).toBe(true);
    expect(guard.getConfirmedPosition('draw-1')).toEqual({ offsetX: 40, offsetY: 60 });
  });

  it('handles save failure with rollback position', () => {
    guard.trackConfirmedPosition('draw-2', 10, 10);
    const seq = guard.registerDrop('draw-2', 50, 50);

    const rollback = guard.onSaveFailure('draw-2', seq);
    expect(rollback.rolledBack).toBe(true);
    expect(rollback.rollbackPos).toEqual({ offsetX: 10, offsetY: 10 });
  });

  it('registers remote moves and protects against stale polls until match', () => {
    guard.registerRemoteMove('draw-3', 25, 35);
    expect(guard.getConfirmedPosition('draw-3')).toEqual({ offsetX: 25, offsetY: 35 });
    expect(guard.shouldApplyServerOffset('draw-3', 0, 0)).toBe(false);
    expect(guard.shouldApplyServerOffset('draw-3', 25, 35)).toBe(true);
  });
});
