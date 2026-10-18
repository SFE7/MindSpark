import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterOutlet } from '@angular/router';

import { GuestIdentityService } from './services/guest-identity.service';
import { Idea, MindsparkService, Room } from './services/mindspark.service';

@Component({
  imports: [DecimalPipe, FormsModule, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly mindspark = inject(MindsparkService);
  private readonly guestIdentity = inject(GuestIdentityService);

  protected readonly rooms = signal<Room[]>([]);
  protected readonly ideas = signal<Idea[]>([]);
  protected readonly activeRoom = signal<Room | null>(null);
  protected readonly roomName = signal('');
  protected readonly invitationCode = signal('');
  protected readonly ideaText = signal('');
  protected readonly editingIdeaId = signal<string | null>(null);
  protected readonly editingText = signal('');
  protected readonly status = signal('');
  protected readonly error = signal('');
  protected readonly loading = signal(false);

  constructor() {
    this.refreshRooms();
    this.mindspark.health().subscribe({
      next: () => this.status.set('Connected to the workshop server'),
      error: () => this.status.set('Guest workshop')
    });
  }

  protected refreshRooms(): void {
    this.mindspark.listRooms().subscribe({ next: rooms => this.rooms.set(rooms), error: () => this.showError('Could not load your rooms') });
  }

  protected createRoom(): void {
    const name = this.roomName().trim();
    if (!name) return this.showError('Give your room a name first');
    this.loading.set(true);
    this.mindspark.createRoom(name).subscribe({
      next: room => { this.roomName.set(''); this.openRoom(room); this.loading.set(false); },
      error: () => { this.showError('Room creation failed'); this.loading.set(false); }
    });
  }

  protected joinRoom(): void {
    const code = this.invitationCode().trim();
    if (!code) return this.showError('Enter an invitation code');
    this.loading.set(true);
    this.mindspark.joinRoom(code).subscribe({
      next: room => { this.invitationCode.set(''); this.openRoom(room); this.loading.set(false); },
      error: () => { this.showError('That invitation code is not available'); this.loading.set(false); }
    });
  }

  protected reopenRoom(room: Room): void {
    this.mindspark.reopenRoom(room.id).subscribe({ next: reopened => this.openRoom(reopened), error: () => this.showError('Could not reopen this room') });
  }

  protected openRoom(room: Room): void {
    this.activeRoom.set(room);
    this.loadIdeas(room.id);
  }

  protected leaveRoom(): void {
    const room = this.activeRoom();
    if (!room) return;
    this.mindspark.leaveRoom(room.id).subscribe({
      next: () => { this.activeRoom.set(null); this.ideas.set([]); this.refreshRooms(); this.status.set('You left the room'); },
      error: () => this.showError('Could not leave the room')
    });
  }

  protected addIdea(): void {
    const room = this.activeRoom();
    const text = this.ideaText().trim();
    if (!room || !text) return;
    this.mindspark.createIdea(room.id, text).subscribe({ next: () => { this.ideaText.set(''); this.loadIdeas(room.id); }, error: () => this.showError('Could not add the idea') });
  }

  protected startEditing(idea: Idea): void { this.editingIdeaId.set(idea.id); this.editingText.set(idea.text); }

  protected saveIdea(idea: Idea): void {
    this.mindspark.updateIdea(idea.id, this.editingText().trim()).subscribe({ next: () => { this.cancelEditing(); this.loadIdeas(idea.roomId); }, error: () => this.showError('Could not update the idea') });
  }

  protected deleteIdea(idea: Idea): void {
    this.mindspark.deleteIdea(idea.id).subscribe({ next: () => this.loadIdeas(idea.roomId), error: () => this.showError('Only the idea author can delete it') });
  }

  protected toggleVote(idea: Idea): void {
    const request = idea.voted ? this.mindspark.removeVote(idea.id) : this.mindspark.vote(idea.id);
    request.subscribe({ next: () => this.loadIdeas(idea.roomId), error: () => this.showError('Only room members can vote') });
  }

  protected cancelEditing(): void { this.editingIdeaId.set(null); this.editingText.set(''); }

  protected isOwner(room: Room): boolean { return room.ownerGuestId === this.guestIdentity.id; }

  private loadIdeas(roomId: string): void { this.mindspark.listIdeas(roomId).subscribe({ next: ideas => this.ideas.set(ideas), error: () => this.showError('Could not load ideas') }); }
  private showError(message: string): void { this.error.set(message); this.status.set(''); }
}
