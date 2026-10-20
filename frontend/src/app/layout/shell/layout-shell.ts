import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from '../header/header';

@Component({
  selector: 'app-layout-shell',
  standalone: true,
  imports: [RouterOutlet, Header],
  templateUrl: './layout-shell.html',
})
export class LayoutShellComponent {}
