import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { DashboardStats, PipelineRun, SecurityAlert } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);

  stats = signal<DashboardStats | null>(null);
  pipelines = signal<PipelineRun[]>([]);
  alerts = signal<SecurityAlert[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void { this.refresh(); }

  refresh() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getStats().subscribe({
      next: s => this.stats.set(s),
      error: e => this.error.set('Failed to load stats: ' + (e?.message ?? e))
    });
    this.api.getPipelines().subscribe(p => this.pipelines.set(p.slice(0, 6)));
    this.api.getOpenAlerts().subscribe(a => {
      this.alerts.set(a.slice(0, 5));
      this.loading.set(false);
    });
  }

  safePct(): number {
    const s = this.stats();
    if (!s || s.totalImages === 0) return 0;
    return Math.round((s.safeImages / s.totalImages) * 100);
  }
}
