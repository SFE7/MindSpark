import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { GuestIdentityService } from './guest-identity.service';

export const guestIdInterceptor: HttpInterceptorFn = (request, next) => {
  const guestId = inject(GuestIdentityService).id;
  return next(request.clone({ setHeaders: { 'X-Guest-Id': guestId } }));
};