import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { KeycloakAuth } from '../../../../core/auth/keycloak-auth';

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="admin-login-container">
      <div class="login-card">
        <div class="shield-badge">🛡️</div>
        <h1>MindSpark Admin Area</h1>
        <p class="subtitle">
          Restricted management console. Authorized administrators only.
        </p>

        @if (isLoggedIn) {
          <div class="status-box">
            <p>Signed in as <strong>{{ username }}</strong></p>
            @if (isAdmin) {
              <p class="role-badge success">Admin Role Verified</p>
              <button class="primary-btn" (click)="goToAdmin()">Enter Admin Portal →</button>
            } @else {
              <p class="role-badge warning">Non-Admin Account</p>
              <p class="help-text">This account does not have administrator privileges.</p>
              <div class="actions">
                <button class="secondary-btn" (click)="switchAccount()">Sign in with different account</button>
                <a routerLink="/" class="link-btn">Return to MindSpark</a>
              </div>
            }
          </div>
        } @else {
          <div class="action-box">
            <p class="info-text">
              Authentication is handled via MindSpark Keycloak realm.
            </p>
            <button class="primary-btn" (click)="login()">Sign In with Admin Account →</button>
            <a routerLink="/" class="back-link">← Return to Application</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .admin-login-container {
      min-height: 85vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: #faf8f5;
    }
    .login-card {
      width: 100%;
      max-width: 460px;
      padding: 40px;
      background: #ffffff;
      border: 1px solid #e2ded9;
      border-radius: 12px;
      box-shadow: 0 8px 24px rgba(35, 59, 59, 0.06);
      text-align: center;
    }
    .shield-badge {
      font-size: 2.8rem;
      margin-bottom: 12px;
    }
    h1 {
      font: 700 1.6rem Georgia, serif;
      color: #233b3b;
      margin: 0 0 8px;
    }
    .subtitle {
      color: #60736d;
      font-size: 0.92rem;
      margin: 0 0 28px;
      line-height: 1.45;
    }
    .primary-btn {
      width: 100%;
      padding: 12px 20px;
      background: #233b3b;
      color: #fffaf1;
      border: none;
      border-radius: 6px;
      font: 600 0.92rem inherit;
      cursor: pointer;
      transition: background 0.15s ease;
    }
    .primary-btn:hover {
      background: #e9784d;
    }
    .secondary-btn {
      width: 100%;
      padding: 10px 16px;
      background: transparent;
      border: 1px solid #d9ddd5;
      border-radius: 6px;
      color: #233b3b;
      font: 500 0.88rem inherit;
      cursor: pointer;
      margin-bottom: 8px;
    }
    .secondary-btn:hover {
      border-color: #233b3b;
      background: #f5f5f0;
    }
    .status-box, .action-box {
      display: flex;
      flex-direction: column;
      gap: 12px;
      align-items: center;
    }
    .role-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .role-badge.success {
      background: #e6f4ea;
      color: #137333;
    }
    .role-badge.warning {
      background: #fef7e0;
      color: #b06000;
    }
    .help-text, .info-text {
      font-size: 0.84rem;
      color: #70757a;
      margin: 0;
    }
    .actions {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .back-link, .link-btn {
      color: #60736d;
      font-size: 0.86rem;
      text-decoration: none;
      margin-top: 8px;
    }
    .back-link:hover, .link-btn:hover {
      color: #233b3b;
      text-decoration: underline;
    }
  `]
})
export class AdminLoginComponent {
  private readonly auth = inject(KeycloakAuth);
  private readonly router = inject(Router);

  get isLoggedIn(): boolean {
    return this.auth.isLoggedIn;
  }

  get username(): string | undefined {
    return this.auth.username;
  }

  get isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  login(): void {
    void this.auth.login();
  }

  switchAccount(): void {
    void this.auth.logout();
  }

  goToAdmin(): void {
    void this.router.navigate(['/admin']);
  }
}
