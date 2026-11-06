import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

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
export class RoomWorkspaceComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly router = inject(Router);
  private readonly mindspark = inject(RoomsService);

  protected readonly room = signal<Room | null>(null);
  protected readonly error = signal('');

  constructor() {
    const roomId = this.route.snapshot.paramMap.get('roomId');
    if (!roomId) {
      void this.router.navigate(['/']);
      return;
    }

    this.mindspark.getRoom(roomId).subscribe({
      next: loadedRoom => {
        this.room.set(loadedRoom);
      },
      error: () => {
        this.error.set('This room is unavailable or you are no longer a member.');
      },
    });
  }

  protected leaveRoom(): void {
    const currentRoom = this.room();
    if (!currentRoom) return;

    this.mindspark.leaveRoom(currentRoom.id).subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => this.error.set('Could not leave the room.'),
    });
  }

}
