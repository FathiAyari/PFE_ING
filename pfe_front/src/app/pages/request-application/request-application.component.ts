import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ApplicationRequest } from '../../core/models';

@Component({
  selector: 'app-request-application',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './request-application.component.html'
})
export class RequestApplicationComponent {
  private api = inject(ApiService);

  busy = signal(false);
  submitted = signal<{ name: string } | null>(null);
  error = signal<string | null>(null);

  form: ApplicationRequest = this.blank();

  blank(): ApplicationRequest {
    return {
      name: '', description: '', team: '', repositoryUrl: '', contactEmail: '',
      deploymentType: 'CONTAINER_APP', database: 'POSTGRESQL',
      needsContainerRegistry: true, needsKeyVault: true, environment: 'DEVELOPMENT'
    };
  }

  submit() {
    if (!this.form.name.trim()) {
      this.error.set('Application name is required');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.submitApplication(this.form).subscribe({
      next: () => {
        this.busy.set(false);
        this.submitted.set({ name: this.form.name });
      },
      error: e => {
        this.busy.set(false);
        this.error.set(e?.error?.error ?? 'Submission failed. Please try again.');
      }
    });
  }

  reset() {
    this.form = this.blank();
    this.submitted.set(null);
    this.error.set(null);
  }
}

