import { Injectable } from '@angular/core';

const guestStorageKey = 'mindspark-guest-id';

@Injectable({ providedIn: 'root' })
export class GuestIdentityService {
  private readonly guestId = this.loadOrCreateGuestId();

  get id(): string {
    return this.guestId;
  }

  private loadOrCreateGuestId(): string {
    const existingId = localStorage.getItem(guestStorageKey);
    if (existingId && this.isUuid(existingId)) {
      return existingId;
    }

    const newId = crypto.randomUUID();
    localStorage.setItem(guestStorageKey, newId);
    return newId;
  }

  private isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
}