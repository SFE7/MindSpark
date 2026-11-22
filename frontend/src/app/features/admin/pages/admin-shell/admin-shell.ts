import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="admin-layout">
      <aside class="admin-sidebar">
        <div class="admin-sidebar-header">
          <div class="admin-mark">⚙️</div>
          <div>
            <div class="admin-title">Admin Console</div>
            <div class="admin-role">{{ isSuperAdmin ? 'Super Administrator' : 'Administrator' }}</div>
          </div>
        </div>

        <nav class="admin-nav">
          <a routerLink="/admin/overview" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">📊</span>
            <span>Platform Stats</span>
          </a>
          <a routerLink="/admin/plans" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">🏷️</span>
            <span>Plans &amp; Pricing</span>
          </a>
          <a routerLink="/admin/subscriptions" routerLinkActive="active" class="nav-item">
            <span class="nav-icon">💳</span>
            <span>Subscriptions</span>
          </a>
          @if (isSuperAdmin) {
            <a routerLink="/admin/admins" routerLinkActive="active" class="nav-item super-item">
              <span class="nav-icon">👑</span>
              <span>Admin Accounts</span>
            </a>
          }
        </nav>

        <div class="sidebar-footer">
          <a routerLink="/" class="back-link">← Back to App</a>
        </div>
      </aside>

      <main class="admin-main">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .admin-layout {
      display: flex;
      min-height: calc(100vh - 81px);
      background: #faf8f5;
    }
    .admin-sidebar {
      width: 260px;
      flex-shrink: 0;
      background: #ffffff;
      border-right: 1px solid #e5e2dc;
      display: flex;
      flex-direction: column;
      padding: 24px 16px;
    }
    .admin-sidebar-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding-bottom: 20px;
      margin-bottom: 16px;
      border-bottom: 1px solid #f0ede8;
    }
    .admin-mark {
      font-size: 1.5rem;
    }
    .admin-title {
      font: 700 1.05rem Georgia, serif;
      color: #233b3b;
    }
    .admin-role {
      font-size: 0.75rem;
      color: #7c3aed;
      font-weight: 600;
    }
    .admin-nav {
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex: 1;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      border-radius: 8px;
      color: #4b5563;
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: all 0.15s ease;
    }
    .nav-item:hover {
      background: #f4f2ee;
      color: #111827;
    }
    .nav-item.active {
      background: #233b3b;
      color: #fffaf1;
    }
    .nav-item.super-item.active {
      background: #5b21b6;
      color: #ffffff;
    }
    .nav-icon {
      font-size: 1.1rem;
    }
    .sidebar-footer {
      padding-top: 16px;
      border-top: 1px solid #f0ede8;
    }
    .back-link {
      color: #6b7280;
      font-size: 0.85rem;
      text-decoration: none;
    }
    .back-link:hover {
      color: #233b3b;
      text-decoration: underline;
    }
    .admin-main {
      flex: 1;
      padding: 32px 40px;
      overflow-y: auto;
    }
    @media (max-width: 768px) {
      .admin-layout {
        flex-direction: column;
      }
      .admin-sidebar {
        width: 100%;
        border-right: none;
        border-bottom: 1px solid #e5e2dc;
      }
      .admin-main {
        padding: 20px 16px;
      }
    }
  `]
})
export class AdminShellComponent {
  private readonly auth = inject(KeycloakAuth);

  get isSuperAdmin(): boolean {
    return this.auth.isSuperAdmin();
  }
}
