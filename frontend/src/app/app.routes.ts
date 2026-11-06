import { Routes } from '@angular/router';
import { LayoutShellComponent } from './layout/shell/layout-shell';

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
    ],
  },
  {
    path: 'room/:roomId',
    loadComponent: () =>
      import('./features/rooms/pages/room-workspace/room-workspace').then(m => m.RoomWorkspaceComponent),
  },
];
