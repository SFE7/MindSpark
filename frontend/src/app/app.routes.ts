import { Routes } from '@angular/router';
import { LayoutShellComponent } from './layout/shell/layout-shell';
import { adminGuard } from './core/auth/admin.guard';
import { superAdminGuard } from './core/auth/super-admin.guard';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: LayoutShellComponent,
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/rooms/pages/home/home-page').then(m => m.HomePageComponent),
      },
      {
        path: 'sessions',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/sessions/pages/sessions-list/sessions-list').then(m => m.SessionsListComponent),
      },
      {
        path: 'sessions/:id',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/sessions/pages/session-detail/session-detail').then(m => m.SessionDetailComponent),
      },
      {
        path: 'billing',
        loadComponent: () =>
          import('./features/billing/pages/billing-page/billing-page').then(m => m.BillingPageComponent),
      },
      {
        path: 'admin/login',
        loadComponent: () =>
          import('./features/admin/pages/admin-login/admin-login').then(m => m.AdminLoginComponent),
      },
      {
        path: 'admin/forbidden',
        loadComponent: () =>
          import('./features/admin/pages/admin-forbidden/admin-forbidden').then(m => m.AdminForbiddenComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/admin/pages/admin-shell/admin-shell').then(m => m.AdminShellComponent),
        children: [
          {
            path: '',
            redirectTo: 'overview',
            pathMatch: 'full',
          },
          {
            path: 'overview',
            loadComponent: () =>
              import('./features/admin/pages/admin-overview/admin-overview').then(m => m.AdminOverviewComponent),
          },
          {
            path: 'plans',
            loadComponent: () =>
              import('./features/admin/pages/admin-plans/admin-plans').then(m => m.AdminPlansComponent),
          },
          {
            path: 'subscriptions',
            loadComponent: () =>
              import('./features/admin/pages/admin-subscriptions/admin-subscriptions').then(m => m.AdminSubscriptionsComponent),
          },
          {
            path: 'admins',
            canActivate: [superAdminGuard],
            loadComponent: () =>
              import('./features/admin/pages/admin-accounts/admin-accounts').then(m => m.AdminAccountsComponent),
          },
        ],
      },
    ],
  },
  {
    path: 'room/:roomId',
    loadComponent: () =>
      import('./features/rooms/pages/room-workspace/room-workspace').then(m => m.RoomWorkspaceComponent),
  },
];
