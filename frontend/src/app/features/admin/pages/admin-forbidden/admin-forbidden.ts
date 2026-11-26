import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="forbidden-container">
      <div class="forbidden-card">
        <div class="badge-icon">⛔</div>
        <h1>403 — Admin Access Required</h1>
        <p class="description">
          You are signed in, but your account lacks the required <code>admin</code> or <code>super-admin</code> role
          to access this administration portal.
        </p>
        <p class="sub-description">
          If you believe this is an error, contact your system super-administrator or consult
          <code>docs/keycloak-admin-roles-setup.md</code> to assign the appropriate realm role.
        </p>
        <div class="btn-group">
          <a routerLink="/" class="primary-btn">Return to Application</a>
          <a routerLink="/admin/login" class="secondary-btn">Sign in with Another Account</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .forbidden-container {
      min-height: 80vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: var(--ms-bg, #060A22);
      color: var(--ms-text, #F4F6FF);
      font-family: var(--ms-font-sans, 'Inter', sans-serif);
    }
    .forbidden-card {
      width: 100%;
      max-width: 520px;
      padding: 44px;
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-error-border);
      border-radius: 20px;
      box-shadow: var(--ms-shadow-glass);
      text-align: center;
    }
    .badge-icon {
      font-size: 3rem;
      margin-bottom: 12px;
    }
    h1 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.5rem;
      font-weight: 800;
      color: var(--ms-coral, #FF9A8F);
      margin: 0 0 14px;
    }
    .description {
      color: var(--ms-text-secondary, #C5CEEE);
      font-size: 0.95rem;
      line-height: 1.5;
      margin: 0 0 12px;
    }
    .sub-description {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.85rem;
      line-height: 1.45;
      margin: 0 0 28px;
    }
    code {
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      color: var(--ms-primary-light, #B4BEFF);
      padding: 2px 6px;
      border-radius: 6px;
      font-family: var(--ms-font-mono, monospace);
      font-size: 0.85em;
    }
    .btn-group {
      display: flex;
      gap: 12px;
      justify-content: center;
      flex-wrap: wrap;
    }
    .primary-btn {
      padding: 10px 20px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border-radius: 12px;
      font-weight: 700;
      font-size: 0.88rem;
      text-decoration: none;
      transition: all 0.15s ease;
    }
    .primary-btn:hover {
      background: var(--ms-primary-hover, #8294F8);
      box-shadow: var(--ms-glow-primary);
    }
    .secondary-btn {
      padding: 10px 18px;
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 12px;
      color: var(--ms-text-secondary, #C5CEEE);
      font-weight: 600;
      font-size: 0.88rem;
      text-decoration: none;
    }
    .secondary-btn:hover {
      border-color: var(--ms-primary-light, #B4BEFF);
      color: #FFFFFF;
    }
  `]
})
export class AdminForbiddenComponent {}
