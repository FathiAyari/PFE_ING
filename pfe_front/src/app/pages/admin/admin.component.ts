import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuditLog, Deployment, DockerImage } from '../../core/models';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  private api = inject(ApiService);
  logs = signal<AuditLog[]>([]);
  deployments = signal<Deployment[]>([]);
  safeImages = signal<DockerImage[]>([]);

  selectedImageId: number | null = null;
  environment = 'staging';
  actor = 'admin';
  busy = signal(false);
  message = signal<{ type: 'ok' | 'err'; text: string } | null>(null);

  ngOnInit() { this.refresh(); }

  refresh() {
    this.api.getAuditLogs().subscribe(d => this.logs.set(d));
    this.api.getDeployments().subscribe(d => this.deployments.set(d));
    this.api.getSafeImages().subscribe(d => {
      this.safeImages.set(d);
      if (!this.selectedImageId && d.length) this.selectedImageId = d[0].id;
    });
  }

  trigger() {
    if (!this.selectedImageId) return;
    this.busy.set(true);
    this.message.set(null);
    this.api.deployImage(this.selectedImageId, {
      environment: this.environment, triggeredBy: this.actor
    }).subscribe({
      next: d => {
        this.busy.set(false);
        this.message.set({ type: 'ok', text: `Deployed ${d.image.name}:${d.image.tag} → ${d.environment}` });
        this.refresh();
      },
      error: e => {
        this.busy.set(false);
        this.message.set({ type: 'err', text: e?.error?.error ?? 'Deployment failed' });
      }
    });
  }

  resultClass(r: string) {
    return r === 'OK' ? 'success' : r === 'DENIED' ? 'failed' : 'cancelled';
  }
}
