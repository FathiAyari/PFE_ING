import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AuditLog,
  DashboardStats,
  Deployment,
  DeployRequest,
  DockerImage,
  PipelineRun,
  SecurityAlert,
  SystemNode
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  // Dashboard
  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.base}/dashboard/stats`);
  }

  // Images
  getAllImages(): Observable<DockerImage[]>   { return this.http.get<DockerImage[]>(`${this.base}/images`); }
  getSafeImages(): Observable<DockerImage[]>  { return this.http.get<DockerImage[]>(`${this.base}/images/safe`); }
  getUnsafeImages(): Observable<DockerImage[]>{ return this.http.get<DockerImage[]>(`${this.base}/images/unsafe`); }

  // Pipelines
  getPipelines(): Observable<PipelineRun[]> { return this.http.get<PipelineRun[]>(`${this.base}/pipelines`); }

  // Alerts
  getAlerts(): Observable<SecurityAlert[]>     { return this.http.get<SecurityAlert[]>(`${this.base}/alerts`); }
  getOpenAlerts(): Observable<SecurityAlert[]> { return this.http.get<SecurityAlert[]>(`${this.base}/alerts/open`); }
  acknowledgeAlert(id: number, actor = 'admin'): Observable<SecurityAlert> {
    return this.http.post<SecurityAlert>(`${this.base}/alerts/${id}/acknowledge?actor=${actor}`, {});
  }

  // Infrastructure
  getNodes(): Observable<SystemNode[]> { return this.http.get<SystemNode[]>(`${this.base}/infrastructure/nodes`); }
  getInfraHealth(): Observable<{ up: number; total: number; healthy: boolean }> {
    return this.http.get<{ up: number; total: number; healthy: boolean }>(`${this.base}/infrastructure/health`);
  }

  // Deployments
  getDeployments(): Observable<Deployment[]> { return this.http.get<Deployment[]>(`${this.base}/deployments`); }
  deployImage(imageId: number, req: DeployRequest): Observable<Deployment> {
    return this.http.post<Deployment>(`${this.base}/deployments/images/${imageId}`, req);
  }

  // Audit
  getAuditLogs(): Observable<AuditLog[]> { return this.http.get<AuditLog[]>(`${this.base}/audit/logs`); }
}
