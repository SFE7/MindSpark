import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';
import { CreateVotePoolRequest, VotePool } from './vote-pool.model';

@Injectable({ providedIn: 'root' })
export class VotePoolService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/rooms';

  readonly activePool = signal<VotePool | null>(null);
  readonly latestClosedPool = signal<VotePool | null>(null);
  readonly myVotesUsed = computed(() => this.activePool()?.myVotesUsed ?? 0);
  readonly myVotesRemaining = computed(() => this.activePool()?.myVotesRemaining ?? 0);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  loadPool(roomId: string, includeLatestClosed: boolean = false): Observable<VotePool | null> {
    this.loading.set(true);
    this.error.set(null);

    const url = `${this.baseUrl}/${roomId}/vote-pools/active?includeLatestClosed=${includeLatestClosed}`;
    return this.http.get<VotePool>(url).pipe(
      tap({
        next: (pool) => {
          this.loading.set(false);
          if (pool && pool.status === 'OPEN') {
            this.activePool.set(pool);
          } else if (pool && pool.status === 'CLOSED') {
            this.activePool.set(null);
            this.latestClosedPool.set(pool);
          } else {
            this.activePool.set(null);
          }
        },
        error: (err) => {
          this.loading.set(false);
          if (err.status !== 204) {
            this.error.set(err.message || 'Failed to load vote pool');
          }
        },
      }),
      catchError((err) => {
        if (err.status === 204) {
          this.activePool.set(null);
          return of(null);
        }
        return throwError(() => err);
      })
    );
  }

  createPool(roomId: string, req: CreateVotePoolRequest): Observable<VotePool> {
    this.loading.set(true);
    this.error.set(null);

    return this.http.post<VotePool>(`${this.baseUrl}/${roomId}/vote-pools`, req).pipe(
      tap({
        next: (pool) => {
          this.loading.set(false);
          this.activePool.set(pool);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || err.message || 'Failed to create vote pool');
        },
      })
    );
  }

  castVote(roomId: string, poolId: string, ideaId: string): Observable<VotePool> {
    this.error.set(null);

    return this.http
      .post<VotePool>(`${this.baseUrl}/${roomId}/vote-pools/${poolId}/votes`, { ideaId })
      .pipe(
        tap({
          next: (pool) => {
            this.activePool.set(pool);
          },
          error: (err) => {
            const errCode = err.error?.error || err.error?.message || 'Vote failed';
            this.error.set(errCode);
          },
        })
      );
  }

  removeVote(roomId: string, poolId: string, ideaId: string): Observable<VotePool> {
    this.error.set(null);

    return this.http
      .delete<VotePool>(`${this.baseUrl}/${roomId}/vote-pools/${poolId}/votes/${ideaId}`)
      .pipe(
        tap({
          next: (pool) => {
            this.activePool.set(pool);
          },
          error: (err) => {
            this.error.set(err.error?.message || 'Failed to remove vote');
          },
        })
      );
  }

  closePool(roomId: string, poolId: string): Observable<VotePool> {
    this.loading.set(true);
    this.error.set(null);

    return this.http.post<VotePool>(`${this.baseUrl}/${roomId}/vote-pools/${poolId}/close`, {}).pipe(
      tap({
        next: (pool) => {
          this.loading.set(false);
          this.activePool.set(null);
          this.latestClosedPool.set(pool);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || 'Failed to close vote pool');
        },
      })
    );
  }

  reset(): void {
    this.activePool.set(null);
    this.latestClosedPool.set(null);
    this.error.set(null);
    this.loading.set(false);
  }
}
