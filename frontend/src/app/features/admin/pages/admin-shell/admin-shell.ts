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
      min-height: calc(100vh - 84px);
      background: var(--ms-bg, #060A22);
      color: var(--ms-text, #F4F6FF);
      font-family: var(--ms-font-sans, 'Inter', sans-serif);
    }
    .admin-sidebar {
      width: 260px;
      flex-shrink: 0;
      background: var(--ms-surface, #0D1438);
      border-right: 1px solid var(--ms-border, #27316E);
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
      border-bottom: 1px solid var(--ms-border-subtle);
    }
    .admin-mark {
      font-size: 1.5rem;
    }
    .admin-title {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.08rem;
      font-weight: 800;
      color: var(--ms-text, #F4F6FF);
    }
    .admin-role {
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.72rem;
      color: var(--ms-primary-light, #B4BEFF);
      font-weight: 600;
    }
    .admin-nav {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      border-radius: 12px;
      color: var(--ms-text-secondary, #C5CEEE);
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 600;
      border: 1px solid transparent;
      transition: all 0.16s ease;
    }
    .nav-item:hover {
      background: rgba(111, 130, 242, 0.16);
      border-color: rgba(180, 190, 255, 0.28);
      color: #FFFFFF;
    }
    .nav-item.active {
      background: var(--ms-primary, #6F82F2);
      border-color: rgba(180, 190, 255, 0.45);
      color: #FFFFFF;
      box-shadow: var(--ms-glow-primary);
    }
    .nav-item.super-item.active {
      background: var(--ms-violet, #8B5CF6);
      border-color: rgba(196, 181, 253, 0.55);
      color: #FFFFFF;
      box-shadow: 0 0 18px rgba(139, 92, 246, 0.45);
    }
    .nav-icon {
      font-size: 1.1rem;
    }
    .sidebar-footer {
      padding-top: 16px;
      border-top: 1px solid var(--ms-border-subtle);
    }
    .back-link {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.85rem;
      font-weight: 600;
      text-decoration: none;
    }
    .back-link:hover {
      color: var(--ms-primary-light, #B4BEFF);
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
        border-bottom: 1px solid var(--ms-border, #27316E);
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
