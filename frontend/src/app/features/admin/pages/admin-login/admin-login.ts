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
      background: var(--ms-bg, #060A22);
      color: var(--ms-text, #F4F6FF);
      font-family: var(--ms-font-sans, 'Inter', sans-serif);
    }
    .login-card {
      width: 100%;
      max-width: 460px;
      padding: 40px;
      background: var(--ms-surface, #0D1438);
      border: 1px solid var(--ms-border-glass);
      border-radius: 20px;
      box-shadow: var(--ms-shadow-glass);
      text-align: center;
    }
    .shield-badge {
      font-size: 2.8rem;
      margin-bottom: 12px;
    }
    h1 {
      font-family: var(--ms-font-display, 'Bricolage Grotesque', sans-serif);
      font-size: 1.65rem;
      font-weight: 800;
      color: var(--ms-text, #F4F6FF);
      margin: 0 0 8px;
    }
    .subtitle {
      color: var(--ms-text-muted, #9AA5CE);
      font-size: 0.92rem;
      margin: 0 0 28px;
      line-height: 1.5;
    }
    .primary-btn {
      width: 100%;
      padding: 12px 20px;
      background: var(--ms-primary, #6F82F2);
      color: #FFFFFF;
      border: 1px solid rgba(180, 190, 255, 0.4);
      border-radius: 12px;
      font-size: 0.92rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.16s ease;
    }
    .primary-btn:hover {
      background: var(--ms-primary-hover, #8294F8);
      box-shadow: var(--ms-glow-primary);
    }
    .secondary-btn {
      width: 100%;
      padding: 10px 16px;
      background: var(--ms-surface-raised, #141D4F);
      border: 1px solid var(--ms-border, #27316E);
      border-radius: 12px;
      color: var(--ms-text-secondary, #C5CEEE);
      font-size: 0.88rem;
      font-weight: 600;
      cursor: pointer;
      margin-bottom: 8px;
    }
    .secondary-btn:hover {
      border-color: var(--ms-primary-light, #B4BEFF);
      color: #FFFFFF;
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
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 700;
    }
    .role-badge.success {
      background: var(--ms-success-bg);
      color: var(--ms-mint, #34D399);
      border: 1px solid var(--ms-success-border);
    }
    .role-badge.warning {
      background: var(--ms-warning-bg);
      color: var(--ms-gold, #FFC83D);
      border: 1px solid var(--ms-warning-border);
    }
    .help-text, .info-text {
      font-size: 0.84rem;
      color: var(--ms-text-muted, #9AA5CE);
      margin: 0;
    }
    .actions {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .back-link, .link-btn {
      color: var(--ms-primary-light, #B4BEFF);
      font-size: 0.86rem;
      text-decoration: none;
      margin-top: 8px;
    }
    .back-link:hover, .link-btn:hover {
      color: #FFFFFF;
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
