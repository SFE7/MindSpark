import { describe, it, expect, beforeEach, vi } from 'vitest';
import { IdeaPositionGuard } from './idea-position-guard';

describe('IdeaPositionGuard', () => {
  let guard: IdeaPositionGuard;

  beforeEach(() => {
    guard = new IdeaPositionGuard();
  });

  it('registers a drop and tracks optimistic pending position', () => {
    const seq = guard.registerDrop('idea-1', 200, 300);
    expect(seq).toBe(1);

    const pending = guard.getPendingMove('idea-1');
    expect(pending).toBeDefined();
    expect(pending?.x).toBe(200);
    expect(pending?.y).toBe(300);
    expect(pending?.status).toBe('saving');
  });

  it('does NOT clear pending move on poll while save is saving', () => {
    guard.registerDrop('idea-1', 200, 300);
    guard.onPollReceived(1);

    const pending = guard.getPendingMove('idea-1');
    expect(pending).toBeDefined();
    expect(pending?.x).toBe(200);
  });

  it('does NOT clear guard when save succeeds until a poll sent AFTER save returns', () => {
    // Poll 1 was sent before save
    const poll1Gen = guard.nextPollGeneration(); // 1

    // User drops idea
    const seq = guard.registerDrop('idea-1', 200, 300);

    // Save succeeds while pollGeneration is 1
    const success = guard.onSaveSuccess('idea-1', seq, 200, 300);
    expect(success).toBe(true);

    // Pending move should still exist with status 'saved'
    let pending = guard.getPendingMove('idea-1');
    expect(pending).toBeDefined();
    expect(pending?.status).toBe('saved');
    expect(pending?.clearAfterPollGen).toBe(2);

    // Stale Poll 1 returns (pollGen = 1) with stale coordinates -> must NOT clear guard
    guard.onPollReceived(poll1Gen, [{ id: 'idea-1', x: 100, y: 100 }]);
    pending = guard.getPendingMove('idea-1');
    expect(pending).toBeDefined();

    // Poll 2 is sent after save (pollGen = 2)
    const poll2Gen = guard.nextPollGeneration(); // 2

    // Poll 2 returns -> MUST clear guard
    guard.onPollReceived(poll2Gen);
    pending = guard.getPendingMove('idea-1');
    expect(pending).toBeUndefined();
  });

  it('clears guard immediately if server coordinates match dropped ones', () => {
    const poll1Gen = guard.nextPollGeneration();
    const seq = guard.registerDrop('idea-1', 250, 350);
    guard.onSaveSuccess('idea-1', seq, 250, 350);

    // Poll 1 returns with matching coordinates (e.g. backend processed quickly)
    guard.onPollReceived(poll1Gen, [{ id: 'idea-1', x: 250, y: 350 }]);
    expect(guard.getPendingMove('idea-1')).toBeUndefined();
  });

  it('protects remote card moves from stale server polls until coordinates match', () => {
    // User B receives remote move for idea-1 to (400, 500)
    guard.registerRemoteMove('idea-1', 400, 500);

    const pending = guard.getPendingMove('idea-1');
    expect(pending).toBeDefined();
    expect(pending?.x).toBe(400);
    expect(pending?.y).toBe(500);
    expect(pending?.isRemote).toBe(true);

    // A background poll arrives with stale server coordinates (100, 100)
    const pollGen = guard.nextPollGeneration();
    guard.onPollReceived(pollGen, [{ id: 'idea-1', x: 100, y: 100 }]);

    // Must still be protected!
    expect(guard.getPendingMove('idea-1')).toBeDefined();

    // Subsequent poll arrives with matching coordinates (400, 500)
    guard.onPollReceived(pollGen + 1, [{ id: 'idea-1', x: 400, y: 500 }]);
    expect(guard.getPendingMove('idea-1')).toBeUndefined();
  });

  it('handles rapid repeated moves: latest drop wins and older response does not revert', () => {
    guard.trackConfirmedPosition('idea-1', 100, 100);

    // First drop
    const seq1 = guard.registerDrop('idea-1', 200, 200);

    // Quick second drop before first save finishes
    const seq2 = guard.registerDrop('idea-1', 300, 300);
    expect(seq2).toBeGreaterThan(seq1);

    // Response from first save arrives late
    const applied1 = guard.onSaveSuccess('idea-1', seq1, 200, 200);
    expect(applied1).toBe(false);

    // Position must still be the second drop
    const pending = guard.getPendingMove('idea-1');
    expect(pending?.x).toBe(300);
    expect(pending?.y).toBe(300);
    expect(pending?.seq).toBe(seq2);

    // Response from second save arrives
    const applied2 = guard.onSaveSuccess('idea-1', seq2, 300, 300);
    expect(applied2).toBe(true);
    expect(guard.getConfirmedPosition('idea-1')).toEqual({ x: 300, y: 300 });
  });

  it('rolls back to last confirmed position on save failure', () => {
    guard.trackConfirmedPosition('idea-1', 100, 100);

    const seq = guard.registerDrop('idea-1', 250, 350);
    const failureResult = guard.onSaveFailure('idea-1', seq);

    expect(failureResult.rolledBack).toBe(true);
    expect(failureResult.rollbackPos).toEqual({ x: 100, y: 100 });
    expect(guard.getPendingMove('idea-1')).toBeUndefined();
  });

  it('ignores failure from an obsolete sequence when a newer move is active', () => {
    guard.trackConfirmedPosition('idea-1', 100, 100);

    const seq1 = guard.registerDrop('idea-1', 200, 200);
    const seq2 = guard.registerDrop('idea-1', 300, 300);

    // Failure for seq1 arrives
    const failureResult = guard.onSaveFailure('idea-1', seq1);
    expect(failureResult.rolledBack).toBe(false);

    // seq2 remains active
    const pending = guard.getPendingMove('idea-1');
    expect(pending?.seq).toBe(seq2);
    expect(pending?.x).toBe(300);
  });
});
