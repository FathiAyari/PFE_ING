import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html'
})
export class RegisterComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  theme = inject(ThemeService);

  username = '';
  email = '';
  password = '';
  confirm = '';
  busy = signal(false);
  error = signal<string | null>(null);

  submit() {
    if (!this.username || !this.password) return;
    if (this.password !== this.confirm) {
      this.error.set('Passwords do not match');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.register(this.username, this.password, this.email).subscribe({
      next: () => {
        this.busy.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(e?.error?.error ?? 'Registration failed');
      }
    });
  }
}
