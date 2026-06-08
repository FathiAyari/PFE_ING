import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AzureResource, AzureResourcePage, SyncRun } from './models';

@Injectable({ providedIn: 'root' })
export class InfraService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(opts: {
    type?: string; rg?: string; state?: string; q?: string;
    page?: number; size?: number;
  } = {}): Observable<AzureResourcePage> {
    let params = new HttpParams();
    if (opts.type)  params = params.set('type', opts.type);
    if (opts.rg)    params = params.set('rg', opts.rg);
    if (opts.state) params = params.set('state', opts.state);
    if (opts.q)     params = params.set('q', opts.q);
    if (opts.page != null) params = params.set('page', opts.page);
    if (opts.size != null) params = params.set('size', opts.size);
    return this.http.get<AzureResourcePage>(`${this.base}/infra/resources`, { params });
  }

  get(id: number): Observable<AzureResource> {
    return this.http.get<AzureResource>(`${this.base}/infra/resources/${id}`);
  }

  history(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/infra/resources/${id}/history`);
  }

  syncRuns(): Observable<SyncRun[]> {
    return this.http.get<SyncRun[]>(`${this.base}/infra/sync/runs`);
  }
}
