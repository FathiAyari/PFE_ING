import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { PipelineRun } from '../../core/models';

@Component({
  selector: 'app-pipelines',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './pipelines.component.html',
  styleUrl: './pipelines.component.css'
})
export class PipelinesComponent implements OnInit {
  private api = inject(ApiService);
  pipelines = signal<PipelineRun[]>([]);

  ngOnInit() { this.load(); }
  load() { this.api.getPipelines().subscribe(d => this.pipelines.set(d)); }

  count(status: string): number {
    return this.pipelines().filter(p => p.status === status).length;
  }
}
