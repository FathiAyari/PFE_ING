import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Application } from '../../core/models';

@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.css'
})
export class ApplicationsComponent implements OnInit {
  private api = inject(ApiService);

  apps = signal<Application[]>([]);
  loading = signal(false);
  selected = signal<Application | null>(null);

  pendingCount = computed(() => this.apps().filter(a => a.status === 'PENDING').length);
  readyCount = computed(() => this.apps().filter(a => a.status === 'READY').length);

  ngOnInit() { this.refresh(); }

  refresh() {
    this.loading.set(true);
    this.api.getApplications().subscribe({
      next: d => { this.apps.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }


  view(app: Application) { this.selected.set(app); }
  closeDetail() { this.selected.set(null); }

  copy(text?: string) {
    if (!text) return;
    try { navigator.clipboard.writeText(text); } catch {}
  }

  statusClass(s: string): string {
    switch (s) {
      case 'READY': return 'success';
      case 'PENDING': return 'warn';
      case 'PROVISIONING': return 'running';
      case 'REJECTED': return 'failed';
      default: return 'info';
    }
  }
}

