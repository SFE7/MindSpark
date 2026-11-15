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
  timestamp: number;
  isRemote?: boolean;
}

export interface ServerIdeaCoords {
  id: string;
  x?: number | null;
  y?: number | null;
}

export class IdeaPositionGuard {
  private pendingMoves = new Map<string, PendingMove>();
  private confirmedPositions = new Map<string, Position>();
  private moveSequence = 0;
  private pollGeneration = 0;
  private readonly SAFETY_NET_MS = 5000;

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
      timestamp: Date.now(),
      isRemote: false,
    });
    return seq;
  }

  registerRemoteMove(ideaId: string, x: number, y: number): void {
    const current = this.pendingMoves.get(ideaId);
    if (current && !current.isRemote && current.status === 'saving') {
      return;
    }
    const seq = ++this.moveSequence;
    this.pendingMoves.set(ideaId, {
      seq,
      x,
      y,
      status: 'saved',
      clearAfterPollGen: Infinity,
      timestamp: Date.now(),
      isRemote: true,
    });
    this.confirmedPositions.set(ideaId, { x, y });
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
    // Keep ignoring server x/y until a poll sent AFTER save completed returns or coords match
    pending.status = 'saved';
    pending.x = updatedX;
    pending.y = updatedY;
    pending.clearAfterPollGen = this.pollGeneration + 1;
    pending.timestamp = Date.now();
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

  onPollReceived(pollGen: number, serverIdeas?: ServerIdeaCoords[]): void {
    const now = Date.now();
    const serverMap = serverIdeas ? new Map(serverIdeas.map(i => [i.id, i])) : null;

    for (const [ideaId, pending] of this.pendingMoves.entries()) {
      if (pending.status === 'saved') {
        const serverIdea = serverMap?.get(ideaId);
        const serverCoordsMatch =
          serverIdea != null &&
          serverIdea.x != null &&
          serverIdea.y != null &&
          Math.abs(serverIdea.x - pending.x) < 0.5 &&
          Math.abs(serverIdea.y - pending.y) < 0.5;

        const pollStartedAfterSave = pollGen >= pending.clearAfterPollGen;
        const safetyNetExpired = now - pending.timestamp >= this.SAFETY_NET_MS;

        if (serverCoordsMatch) {
          this.pendingMoves.delete(ideaId);
        } else if (!pending.isRemote && pollStartedAfterSave) {
          this.pendingMoves.delete(ideaId);
        } else if (safetyNetExpired) {
          this.pendingMoves.delete(ideaId);
        }
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
