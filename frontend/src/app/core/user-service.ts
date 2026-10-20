
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, shareReplay, tap, throwError } from 'rxjs';
import { User } from './models/User';

@Injectable({ providedIn: 'root' })
export class UserService {
  readonly currentUser = signal<User | null>(null);

  private readonly http = inject(HttpClient);
  private userRequest$?: Observable<User>;

  syncUser(): Observable<User> {
    if (!this.userRequest$) {
      this.userRequest$ = this.http.post<User>('/api/users/sync', {}).pipe(
        tap(user => this.currentUser.set(user)),
        catchError(error => {
          this.userRequest$ = undefined;
          this.currentUser.set(null);
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }

    return this.userRequest$;
  }

  clearCurrentUser(): void {
    this.userRequest$ = undefined;
    this.currentUser.set(null);
  }
}
