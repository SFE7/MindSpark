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
      background: #faf8f5;
    }
    .forbidden-card {
      width: 100%;
      max-width: 520px;
      padding: 44px;
      background: #ffffff;
      border: 1px solid #ebd9d9;
      border-radius: 12px;
      box-shadow: 0 8px 24px rgba(180, 40, 40, 0.05);
      text-align: center;
    }
    .badge-icon {
      font-size: 3rem;
      margin-bottom: 12px;
    }
    h1 {
      font: 700 1.5rem Georgia, serif;
      color: #991b1b;
      margin: 0 0 14px;
    }
    .description {
      color: #374151;
      font-size: 0.95rem;
      line-height: 1.5;
      margin: 0 0 12px;
    }
    .sub-description {
      color: #6b7280;
      font-size: 0.85rem;
      line-height: 1.45;
      margin: 0 0 28px;
    }
    code {
      background: #f3f4f6;
      padding: 2px 6px;
      border-radius: 4px;
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
      background: #233b3b;
      color: #fffaf1;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.88rem;
      text-decoration: none;
      transition: background 0.15s ease;
    }
    .primary-btn:hover {
      background: #e9784d;
    }
    .secondary-btn {
      padding: 10px 18px;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      color: #374151;
      font-weight: 500;
      font-size: 0.88rem;
      text-decoration: none;
    }
    .secondary-btn:hover {
      background: #f9fafb;
    }
  `]
})
export class AdminForbiddenComponent {}
