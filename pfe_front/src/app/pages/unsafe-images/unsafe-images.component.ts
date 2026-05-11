import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { DockerImage } from '../../core/models';

@Component({
  selector: 'app-unsafe-images',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  templateUrl: './unsafe-images.component.html',
  styleUrl: './unsafe-images.component.css'
})
export class UnsafeImagesComponent implements OnInit {
  private api = inject(ApiService);
  images = signal<DockerImage[]>([]);

  ngOnInit() { this.load(); }
  load() { this.api.getUnsafeImages().subscribe(d => this.images.set(d)); }
}
