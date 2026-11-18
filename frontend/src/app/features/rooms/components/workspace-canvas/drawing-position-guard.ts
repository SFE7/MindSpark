export interface DrawingOffset {
  offsetX: number;
  offsetY: number;
}

export interface PendingDrawingMove {
  seq: number;
  offsetX: number;
  offsetY: number;
  status: 'saving' | 'saved';
  clearAfterPollGen: number;
  timestamp: number;
  isRemote?: boolean;
}

export class DrawingPositionGuard {
  private pendingMoves = new Map<string, PendingDrawingMove>();
  private confirmedPositions = new Map<string, DrawingOffset>();
  private moveSequence = 0;
  private pollGeneration = 0;

  nextPollGeneration(): number {
    return ++this.pollGeneration;
  }

  getPollGeneration(): number {
    return this.pollGeneration;
  }

  registerDrop(drawingId: string, offsetX: number, offsetY: number): number {
    const seq = ++this.moveSequence;
    this.pendingMoves.set(drawingId, {
      seq,
      offsetX,
      offsetY,
      status: 'saving',
      clearAfterPollGen: Infinity,
      timestamp: Date.now(),
      isRemote: false,
    });
    return seq;
  }

  registerRemoteMove(drawingId: string, offsetX: number, offsetY: number): void {
    const current = this.pendingMoves.get(drawingId);
    if (current && !current.isRemote && current.status === 'saving') {
      return;
    }
    const seq = ++this.moveSequence;
    this.pendingMoves.set(drawingId, {
      seq,
      offsetX,
      offsetY,
      status: 'saved',
      clearAfterPollGen: Infinity,
      timestamp: Date.now(),
      isRemote: true,
    });
    this.confirmedPositions.set(drawingId, { offsetX, offsetY });
  }

  onPollReceived(pollGen: number, drawings: { id: string; offsetX?: number; offsetY?: number }[]): void {
    for (const d of drawings) {
      this.shouldApplyServerOffset(d.id, d.offsetX || 0, d.offsetY || 0);
    }
  }

  getPendingMove(drawingId: string): PendingDrawingMove | undefined {
    return this.pendingMoves.get(drawingId);
  }

  trackConfirmedPosition(drawingId: string, offsetX: number, offsetY: number): void {
    this.confirmedPositions.set(drawingId, { offsetX, offsetY });
  }

  getConfirmedPosition(drawingId: string): DrawingOffset | undefined {
    return this.confirmedPositions.get(drawingId);
  }

  onSaveSuccess(drawingId: string, seq: number, updatedOffsetX: number, updatedOffsetY: number): boolean {
    const pending = this.pendingMoves.get(drawingId);
    if (!pending || pending.seq !== seq) {
      return false;
    }
    this.confirmedPositions.set(drawingId, { offsetX: updatedOffsetX, offsetY: updatedOffsetY });
    pending.status = 'saved';
    pending.offsetX = updatedOffsetX;
    pending.offsetY = updatedOffsetY;
    pending.clearAfterPollGen = this.pollGeneration + 1;
    pending.timestamp = Date.now();
    return true;
  }

  onSaveFailure(drawingId: string, seq: number): { rolledBack: boolean; rollbackPos?: DrawingOffset } {
    const pending = this.pendingMoves.get(drawingId);
    if (!pending || pending.seq !== seq) {
      return { rolledBack: false };
    }
    this.pendingMoves.delete(drawingId);
    const rollbackPos = this.confirmedPositions.get(drawingId);
    return { rolledBack: true, rollbackPos };
  }

  shouldApplyServerOffset(drawingId: string, serverOffsetX: number, serverOffsetY: number): boolean {
    const pending = this.pendingMoves.get(drawingId);
    if (!pending) return true;

    // While saving local move, always preserve local position
    if (pending.status === 'saving' && !pending.isRemote) {
      return false;
    }

    // If server coordinates match what was saved, clear pending move
    if (pending.offsetX === serverOffsetX && pending.offsetY === serverOffsetY) {
      this.pendingMoves.delete(drawingId);
      this.confirmedPositions.set(drawingId, { offsetX: serverOffsetX, offsetY: serverOffsetY });
      return true;
    }

    // Safety net: if older than poll generation, clear
    if (this.pollGeneration >= pending.clearAfterPollGen) {
      this.pendingMoves.delete(drawingId);
      this.confirmedPositions.set(drawingId, { offsetX: serverOffsetX, offsetY: serverOffsetY });
      return true;
    }

    return false;
  }

  clear(): void {
    this.pendingMoves.clear();
    this.confirmedPositions.clear();
    this.moveSequence = 0;
    this.pollGeneration = 0;
  }
}
