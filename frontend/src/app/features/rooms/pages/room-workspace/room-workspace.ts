import { Component, OnDestroy, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subscription, timer } from 'rxjs';
import { catchError, exhaustMap, timeout } from 'rxjs/operators';

import { RoomsService } from '../../data-access/rooms.service';
import { Room } from '../../models/room.model';
import { WorkspaceCanvasComponent } from '../../components/workspace-canvas/workspace-canvas';

@Component({
  selector: 'app-room-workspace',
  standalone: true,
  imports: [WorkspaceCanvasComponent],
  templateUrl: './room-workspace.html',
  styleUrl: './room-workspace.scss',
})
export class RoomWorkspaceComponent implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  protected readonly router = inject(Router);
  private readonly mindspark = inject(RoomsService);

  protected readonly room = signal<Room | null>(null);
  protected readonly error = signal('');
  private pollSub?: Subscription;

  constructor() {
    const roomId = this.route.snapshot.paramMap.get('roomId');
    if (!roomId) {
      void this.router.navigate(['/']);
      return;
    }

    this.startPolling(roomId);
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private stopPolling(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = undefined;
    }
  }

  private startPolling(roomId: string): void {
    this.stopPolling();
    this.pollSub = timer(0, 3000)
      .pipe(
        exhaustMap(() =>
          this.mindspark.getRoom(roomId).pipe(
            timeout(10000),
            catchError((err: HttpErrorResponse) => {
              if (err.status === 403 || err.status === 404) {
                const message =
                  err.status === 403
                    ? 'You were removed from this room'
                    : 'This room was deleted';
                this.stopPolling();
                void this.router.navigate(['/'], {
                  queryParams: { error: message },
                  state: { message },
                });
                return EMPTY;
              }
              if (!this.room()) {
                this.error.set('This room is unavailable or you are no longer a member.');
              }
              return EMPTY;
            })
          )
        )
      )
      .subscribe({
        next: loadedRoom => {
          if (!loadedRoom) return;
          const current = this.room();
          if (!current) {
            this.room.set(loadedRoom);
            return;
          }

          const nameChanged = current.name !== loadedRoom.name;
          const closedChanged = Boolean(current.closed) !== Boolean(loadedRoom.closed);
          const offlineChanged = Boolean(current.offline) !== Boolean(loadedRoom.offline);
          const currentMemberIds = (current.members || []).map(m => m.id).sort().join(',');
          const loadedMemberIds = (loadedRoom.members || []).map(m => m.id).sort().join(',');
          const membersChanged =
            current.memberCount !== loadedRoom.memberCount ||
            currentMemberIds !== loadedMemberIds;

          if (nameChanged || closedChanged || offlineChanged || membersChanged) {
            this.room.set(loadedRoom);
          }
        },
      });
  }

  protected leaveRoom(): void {
    const currentRoom = this.room();
    if (!currentRoom) return;

    this.stopPolling();
    this.mindspark.leaveRoom(currentRoom.id).subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => this.error.set('Could not leave the room.'),
    });
  }

  protected updateRoom(updatedRoom: Room): void {
    this.room.set(updatedRoom);
  }
}
