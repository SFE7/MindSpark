import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { MindsparkService, Room } from './services/mindspark.service';
import { KeycloakAuth } from './services/auth/keycloak-auth';
import { Header } from './components/header/header';
import { UserService } from './services/user-service';

@Component({
  imports: [DecimalPipe, FormsModule, RouterOutlet,Header],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly mindspark = inject(MindsparkService);
  private readonly router = inject(Router);
  private readonly auth = inject(KeycloakAuth);
  private readonly userService = inject(UserService);

  protected readonly rooms = signal<Room[]>([]);
  protected readonly isRoomRoute = signal(false);
  protected readonly roomName = signal('');
  protected readonly invitationCode = signal('');
  protected readonly ideaText = signal('');
  protected readonly editingIdeaId = signal<string | null>(null);
  protected readonly editingText = signal('');
  protected readonly status = signal('');
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly isLoggedIn = this.auth.isLoggedIn;

  constructor() {
    this.isRoomRoute.set(this.router.url.startsWith('/room/'));
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      this.isRoomRoute.set((event as NavigationEnd).urlAfterRedirects.startsWith('/room/'));
    });
    this.mindspark.health().subscribe({
      next: () => this.status.set('Connected to the workshop server'),
      error: () => this.status.set('Could not connect to the workshop server')
    });

    if (this.isLoggedIn) {
      this.userService.syncUser().subscribe({
        next: () => this.refreshRooms(),
        error: () => this.showError('Could not load your account. Please sign in again.')
      });
    } else {
      this.status.set('Sign in to create and join rooms');
    }
  }

  protected refreshRooms(): void {
    if (!this.isLoggedIn) return;
    this.mindspark.listRooms().subscribe({ next: rooms => this.rooms.set(rooms), error: () => this.showError('Could not load your rooms') });
  }

  protected createRoom(): void {
    if (!this.isLoggedIn) return this.showError('Sign in before creating a room');
    const name = this.roomName().trim();
    if (!name) return this.showError('Give your room a name first');
    this.loading.set(true);
    this.mindspark.createRoom(name).subscribe({
      next: room => { this.roomName.set(''); this.openRoom(room); this.loading.set(false); },
      error: () => { this.showError('Room creation failed'); this.loading.set(false); }
    });
  }

  protected joinRoom(): void {
    if (!this.isLoggedIn) return this.showError('Sign in before joining a room');
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
    void this.router.navigate(['/room', room.id]);
  }

  private showError(message: string): void { this.error.set(message); this.status.set(''); }
}
