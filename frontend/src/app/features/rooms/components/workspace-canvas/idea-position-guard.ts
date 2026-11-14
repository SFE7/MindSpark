export interface Position {
  x: number;
  y: number;
}

export interface PendingMove {
  seq: number;
  x: number;
  y: number;
  status: 'saving' | 'saved';
  clearAfterPollGen: number;
}

export class IdeaPositionGuard {
  private pendingMoves = new Map<string, PendingMove>();
  private confirmedPositions = new Map<string, Position>();
  private moveSequence = 0;
  private pollGeneration = 0;

  nextPollGeneration(): number {
    return ++this.pollGeneration;
  }

  getPollGeneration(): number {
    return this.pollGeneration;
  }

  registerDrop(ideaId: string, x: number, y: number): number {
    const seq = ++this.moveSequence;
    this.pendingMoves.set(ideaId, {
      seq,
      x,
      y,
      status: 'saving',
      clearAfterPollGen: Infinity,
    });
    return seq;
  }

  getPendingMove(ideaId: string): PendingMove | undefined {
    return this.pendingMoves.get(ideaId);
  }

  trackConfirmedPosition(ideaId: string, x: number, y: number): void {
    this.confirmedPositions.set(ideaId, { x, y });
  }

  getConfirmedPosition(ideaId: string): Position | undefined {
    return this.confirmedPositions.get(ideaId);
  }

  onSaveSuccess(ideaId: string, seq: number, updatedX: number, updatedY: number): boolean {
    const pending = this.pendingMoves.get(ideaId);
    if (!pending || pending.seq !== seq) {
      // Stale response from an earlier drop; ignore older response
      return false;
    }

    this.confirmedPositions.set(ideaId, { x: updatedX, y: updatedY });
    // Keep ignoring server x/y until a poll sent AFTER save completed returns
    pending.status = 'saved';
    pending.x = updatedX;
    pending.y = updatedY;
    pending.clearAfterPollGen = this.pollGeneration + 1;
    return true;
  }

  onSaveFailure(ideaId: string, seq: number): { rolledBack: boolean; rollbackPos?: Position } {
    const pending = this.pendingMoves.get(ideaId);
    if (!pending || pending.seq !== seq) {
      // Stale failure response; a newer move is already active
      return { rolledBack: false };
    }

    this.pendingMoves.delete(ideaId);
    const rollbackPos = this.confirmedPositions.get(ideaId);
    return { rolledBack: true, rollbackPos };
  }

  onPollReceived(pollGen: number): void {
    for (const [ideaId, pending] of this.pendingMoves.entries()) {
      if (pending.status === 'saved' && pollGen >= pending.clearAfterPollGen) {
        this.pendingMoves.delete(ideaId);
      }
    }
  }

  clear(): void {
    this.pendingMoves.clear();
    this.confirmedPositions.clear();
    this.moveSequence = 0;
    this.pollGeneration = 0;
  }
}
