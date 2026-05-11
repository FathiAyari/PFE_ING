import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { DockerImage } from '../../core/models';

@Component({
  selector: 'app-safe-images',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe],
  templateUrl: './safe-images.component.html',
  styleUrl: './safe-images.component.css'
})
export class SafeImagesComponent implements OnInit {
  private api = inject(ApiService);
  images = signal<DockerImage[]>([]);
  selected = signal<DockerImage | null>(null);
  environment = 'staging';
  actor = 'admin';
  notes = '';
  busy = signal(false);
  message = signal<{ type: 'ok' | 'err'; text: string } | null>(null);

  ngOnInit() { this.load(); }

  load() {
    this.api.getSafeImages().subscribe(d => this.images.set(d));
  }

  open(img: DockerImage) {
    this.selected.set(img);
    this.message.set(null);
  }

  close() { this.selected.set(null); }

  deploy() {
    const img = this.selected();
    if (!img) return;
    this.busy.set(true);
    this.api.deployImage(img.id, {
      environment: this.environment, triggeredBy: this.actor, notes: this.notes
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set({ type: 'ok', text: `Deployed ${img.name}:${img.tag} to ${this.environment}` });
        setTimeout(() => this.close(), 1200);
      },
      error: e => {
        this.busy.set(false);
        this.message.set({ type: 'err', text: e?.error?.error ?? 'Deployment failed' });
      }
    });
  }
}
