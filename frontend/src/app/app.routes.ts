import { Routes } from '@angular/router';
import { RoomWorkspaceComponent } from './features/rooms/pages/room-workspace/room-workspace';

export const routes: Routes = [
	{ path: 'room/:roomId', component: RoomWorkspaceComponent },
	{ path: '**', redirectTo: '' },
];
