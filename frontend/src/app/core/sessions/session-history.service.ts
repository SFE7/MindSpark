import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, tap, catchError, throwError, map } from 'rxjs';
import {
  CreateSnapshotRequest,
  ExportFormat,
  SessionSnapshotDetail,
  SessionSnapshotSummary,
} from './session.models';

export function extractFilenameFromHeader(header: string | null, fallback: string): string {
  if (!header) return fallback;
  const rfc5987Match = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (rfc5987Match && rfc5987Match[1]) {
    try {
      return decodeURIComponent(rfc5987Match[1]);
    } catch {
      return rfc5987Match[1];
    }
  }
  const standardMatch = header.match(/filename="?([^";]+)"?/i);
  if (standardMatch && standardMatch[1]) {
    return standardMatch[1].trim();
  }
  return fallback;
}

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => window.URL.revokeObjectURL(url), 1000);
}

@Injectable({
  providedIn: 'root',
})
export class SessionHistoryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/sessions';

  readonly snapshots = signal<SessionSnapshotSummary[]>([]);
  readonly selectedSnapshot = signal<SessionSnapshotDetail | null>(null);
  readonly loading = signal<boolean>(false);
  readonly saving = signal<boolean>(false);
  readonly exporting = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  listSnapshots(roomId?: string, q?: string): Observable<SessionSnapshotSummary[]> {
    this.loading.set(true);
    this.error.set(null);

    let params = new HttpParams();
    if (roomId && roomId.trim()) {
      params = params.set('roomId', roomId.trim());
    }
    if (q && q.trim()) {
      params = params.set('q', q.trim());
    }

    return this.http.get<SessionSnapshotSummary[]>(`${this.baseUrl}/snapshots`, { params }).pipe(
      tap(list => {
        this.snapshots.set(list);
        this.loading.set(false);
      }),
      catchError(err => {
        const msg = err?.error?.message || 'Failed to load saved sessions';
        this.error.set(msg);
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }

  getSnapshot(id: string): Observable<SessionSnapshotDetail> {
    this.loading.set(true);
    this.error.set(null);

    return this.http.get<SessionSnapshotDetail>(`${this.baseUrl}/snapshots/${id}`).pipe(
      tap(detail => {
        this.selectedSnapshot.set(detail);
        this.loading.set(false);
      }),
      catchError(err => {
        const msg = err?.error?.message || 'Failed to load session details';
        this.error.set(msg);
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }

  saveSnapshot(request: CreateSnapshotRequest): Observable<SessionSnapshotDetail> {
    this.saving.set(true);
    this.error.set(null);

    return this.http.post<SessionSnapshotDetail>(`${this.baseUrl}/snapshots`, request).pipe(
      tap(saved => {
        this.saving.set(false);
        this.snapshots.update(prev => [saved, ...prev.filter(s => s.id !== saved.id)]);
      }),
      catchError(err => {
        const msg = err?.error?.message || 'Failed to save session snapshot';
        this.error.set(msg);
        this.saving.set(false);
        return throwError(() => err);
      })
    );
  }

  deleteSnapshot(id: string): Observable<void> {
    this.error.set(null);

    return this.http.delete<void>(`${this.baseUrl}/snapshots/${id}`).pipe(
      tap(() => {
        this.snapshots.update(prev => prev.filter(s => s.id !== id));
        if (this.selectedSnapshot()?.id === id) {
          this.selectedSnapshot.set(null);
        }
      }),
      catchError(err => {
        const msg = err?.error?.message || 'Failed to delete snapshot';
        this.error.set(msg);
        return throwError(() => err);
      })
    );
  }

  exportSnapshot(id: string, format: ExportFormat): Observable<Blob> {
    this.exporting.set(true);
    const params = new HttpParams().set('format', format);

    return this.http.get(`${this.baseUrl}/snapshots/${id}/export`, {
      params,
      responseType: 'blob',
      observe: 'response',
    }).pipe(
      map((res: HttpResponse<Blob>) => {
        this.exporting.set(false);
        const disposition = res.headers.get('content-disposition');
        const filename = extractFilenameFromHeader(disposition, `session-${id}.${format}`);
        const blob = res.body ?? new Blob();
        triggerBlobDownload(blob, filename);
        return blob;
      }),
      catchError(err => {
        this.exporting.set(false);
        const msg = 'Failed to export session snapshot';
        this.error.set(msg);
        return throwError(() => err);
      })
    );
  }

  exportLiveRoom(roomId: string, format: ExportFormat): Observable<Blob> {
    this.exporting.set(true);
    const params = new HttpParams().set('format', format);

    return this.http.get(`${this.baseUrl}/rooms/${roomId}/export`, {
      params,
      responseType: 'blob',
      observe: 'response',
    }).pipe(
      map((res: HttpResponse<Blob>) => {
        this.exporting.set(false);
        const disposition = res.headers.get('content-disposition');
        const filename = extractFilenameFromHeader(disposition, `room-${roomId}.${format}`);
        const blob = res.body ?? new Blob();
        triggerBlobDownload(blob, filename);
        return blob;
      }),
      catchError(err => {
        this.exporting.set(false);
        const msg = 'Failed to export live room';
        this.error.set(msg);
        return throwError(() => err);
      })
    );
  }
}
