import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { RoomsService } from '../../data-access/rooms.service';
import { Room } from '../../models/room.model';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';
import { UserService } from '../../../../core/user-service';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './home-page.html',
  styleUrl: './home-page.scss',
})
export class HomePageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly mindspark = inject(RoomsService);
  private readonly router = inject(Router);
  private readonly auth = inject(KeycloakAuth);
  private readonly userService = inject(UserService);

  protected readonly rooms = signal<Room[]>([]);
  protected readonly roomName = signal('');
  protected readonly invitationCode = signal('');
  protected readonly otpSlots = signal<string[]>(['', '', '', '', '', '']);
  protected readonly ideaText = signal<string>('');
  protected readonly editingIdeaId = signal<string | null>(null);
  protected readonly editingText = signal('');
  protected readonly status = signal('');
  protected readonly error = signal('');
  protected readonly roomLimitHit = signal<{ current: number; limit: number; message: string } | null>(null);
  protected readonly loading = signal(false);
  protected readonly isLoggedIn = this.auth.isLoggedIn;

  constructor() {
    const redirectMsg = this.route.snapshot.queryParams['error'] || (history.state && history.state.message);
    if (redirectMsg) {
      this.error.set(redirectMsg);
    }
    this.route.queryParams.subscribe(params => {
      if (params['error']) {
        this.error.set(params['error']);
      }
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
    this.mindspark.listRooms().subscribe({
      next: rooms => this.rooms.set(rooms),
      error: () => this.showError('Could not load your rooms')
    });
  }

  protected createRoom(): void {
    if (!this.isLoggedIn) {
      this.showError('Sign in before creating a room');
      return;
    }
    const name = this.roomName().trim();
    if (!name) return this.showError('Give your room a name first');
    this.loading.set(true);
    this.roomLimitHit.set(null);
    this.error.set('');

    this.mindspark.createRoom(name).subscribe({
      next: room => {
        this.roomName.set('');
        this.openRoom(room);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 403) {
          const body = (err as { error?: { error?: string; message?: string; current?: number; limit?: number } }).error;
          if (body?.error === 'ROOM_LIMIT_REACHED') {
            this.roomLimitHit.set({
              current: body.current ?? 3,
              limit: body.limit ?? 3,
              message: body.message || 'You have reached your active room limit.',
            });
            return;
          }
        }
        this.showError('Room creation failed');
      }
    });
  }

  protected onOtpInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = (input.value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (raw.length > 1) {
      this.pasteCode(raw, index);
      return;
    }
    const slots = [...this.otpSlots()];
    slots[index] = raw;
    this.otpSlots.set(slots);
    this.invitationCode.set(slots.join(''));
    if (raw && index < 5) {
      this.focusOtpSlot(index + 1);
    }
  }

  protected onOtpKeyDown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace') {
      const slots = [...this.otpSlots()];
      if (!slots[index] && index > 0) {
        slots[index - 1] = '';
        this.otpSlots.set(slots);
        this.invitationCode.set(slots.join(''));
        this.focusOtpSlot(index - 1);
        event.preventDefault();
      } else if (slots[index]) {
        slots[index] = '';
        this.otpSlots.set(slots);
        this.invitationCode.set(slots.join(''));
      }
    } else if (event.key === 'ArrowLeft' && index > 0) {
      this.focusOtpSlot(index - 1);
      event.preventDefault();
    } else if (event.key === 'ArrowRight' && index < 5) {
      this.focusOtpSlot(index + 1);
      event.preventDefault();
    } else if (event.key === 'Enter') {
      this.joinRoom();
    }
  }

  protected onOtpPaste(event: ClipboardEvent, startIndex = 0): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text') ?? '';
    this.pasteCode(text, startIndex);
  }

  protected pasteCode(text: string, startIndex = 0): void {
    const clean = text.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (!clean) return;
    const slots = [...this.otpSlots()];
    let slotIdx = clean.length >= 6 ? 0 : startIndex;
    for (let i = 0; i < clean.length && slotIdx < 6; i++, slotIdx++) {
      slots[slotIdx] = clean[i];
    }
    this.otpSlots.set(slots);
    this.invitationCode.set(slots.join(''));
    const nextFocus = Math.min(slotIdx, 5);
    this.focusOtpSlot(nextFocus);
  }

  protected focusOtpSlot(index: number): void {
    if (typeof document !== 'undefined') {
      const el = document.getElementById(`otp-slot-${index}`) as HTMLInputElement | null;
      el?.focus();
      el?.select();
    }
  }

  protected joinRoom(): void {
    if (!this.isLoggedIn) {
      this.showError('Sign in before joining a room');
      return;
    }
    const otpJoined = this.otpSlots().join('').trim();
    const code = (this.invitationCode().trim() || otpJoined);
    if (!code) return this.showError('Enter an invitation code');
    this.loading.set(true);
    this.roomLimitHit.set(null);
    this.error.set('');

    this.mindspark.joinRoom(code).subscribe({
      next: room => {
        this.invitationCode.set('');
        this.otpSlots.set(['', '', '', '', '', '']);
        this.openRoom(room);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err && typeof err === 'object' && 'status' in err && (err as { status: number }).status === 403) {
          const body = (err as { error?: { error?: string; message?: string; limit?: number; current?: number } }).error;
          if (body?.error === 'PARTICIPANT_LIMIT_REACHED') {
            this.showError(body.message || `This room has reached its host's participant limit (${body.limit ?? 5}/${body.limit ?? 5}).`);
            return;
          }
        }
        this.showError('That invitation code is not available');
      }
    });
  }

  protected navigateToRoom(room: Room): void {
    if (room.closed) {
      this.reopenRoom(room);
    } else {
      this.openRoom(room);
    }
  }

  protected reopenRoom(room: Room): void {
    this.mindspark.reopenRoom(room.id).subscribe({
      next: reopened => this.openRoom(reopened),
      error: () => this.showError('Could not reopen this room')
    });
  }

  protected deleteRoom(event: MouseEvent, room: Room): void {
    event.stopPropagation();
    const confirmed = typeof window !== 'undefined' && window.confirm ? window.confirm('Permanently delete this room? All ideas and votes will be lost.') : true;
    if (!confirmed) return;

    this.mindspark.deleteRoom(room.id).subscribe({
      next: () => this.refreshRooms(),
      error: () => this.showError('Could not delete the room.'),
    });
  }

  protected openRoom(room: Room): void {
    void this.router.navigate(['/room', room.id]);
  }

  protected getRoomIconType(index: number): 'bulb' | 'chart' | 'code' {
    const mod = index % 3;
    if (mod === 0) return 'bulb';
    if (mod === 1) return 'chart';
    return 'code';
  }

  protected formatRelativeTime(isoString?: string): string {
    if (!isoString) return 'Recent';
    try {
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return 'Recent';
      const diffMs = Date.now() - date.getTime();
      const diffSec = Math.floor(diffMs / 1000);
      if (diffSec < 60) return 'Just now';
      const diffMin = Math.floor(diffSec / 60);
      if (diffMin < 60) return `${diffMin}m ago`;
      const diffHrs = Math.floor(diffMin / 60);
      if (diffHrs < 24) return `${diffHrs}h ago`;
      const diffDays = Math.floor(diffHrs / 24);
      return `${diffDays}d ago`;
    } catch {
      return 'Recent';
    }
  }

  protected getInitialsBadge(room: Room): string[] {
    const badges: string[] = [];
    if (room.owner?.username) {
      badges.push(room.owner.username.slice(0, 2).toUpperCase());
    } else if (room.name) {
      const words = room.name.trim().split(/\s+/);
      if (words.length > 1) {
        badges.push((words[0][0] + words[1][0]).toUpperCase());
      } else {
        badges.push(room.name.slice(0, 2).toUpperCase());
      }
    } else {
      badges.push('MS');
    }
    if (room.memberCount > 1) {
      badges.push(`+${room.memberCount - 1}`);
    }
    return badges;
  }

  private showError(message: string): void {
    this.error.set(message);
    this.status.set('');
  }
}
