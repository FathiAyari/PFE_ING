import { Injectable, OnDestroy, inject } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { IMessage } from '@stomp/stompjs';
import { EMPTY, Observable, map } from 'rxjs';
import * as SockJSImport from 'sockjs-client';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { AzureResource, ResourceChangeMessage } from './models';

// sockjs-client is published as CommonJS; depending on the bundler the default
// export can be either the constructor itself or `{ default: <ctor> }`.
const SockJS: any = (SockJSImport as any).default ?? SockJSImport;

/**
 * Wraps a single STOMP connection over SockJS to the backend `/ws` endpoint.
 * Exposes typed Observables for the topics the live infra UI subscribes to.
 *
 * Designed to fail soft: if SockJS / STOMP cannot connect (backend down,
 * firewalled, etc.) the page still renders, the API still works, and the
 * Observables simply never emit instead of throwing.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService implements OnDestroy {
  private auth = inject(AuthService);
  private stomp: RxStomp | null = null;
  private started = false;
  private failed = false;

  start(): void {
    if (this.started || this.failed) return;
    this.started = true;
    try {
      this.stomp = new RxStomp();
      const cfg: RxStompConfig = {
        webSocketFactory: () => new SockJS(environment.wsUrl),
        connectHeaders: this.buildHeaders(),
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        reconnectDelay: 4_000
      };
      this.stomp.configure(cfg);
      this.stomp.activate();
    } catch (err) {
      console.warn('[Realtime] WebSocket disabled:', err);
      this.failed = true;
      this.stomp = null;
    }
  }

  stop(): void {
    if (this.started && this.stomp) {
      try { this.stomp.deactivate(); } catch { /* noop */ }
    }
    this.started = false;
  }

  /** Stream of every resource change broadcast by the backend. */
  resourceChanges$(): Observable<ResourceChangeMessage> {
    this.start();
    if (!this.stomp) return EMPTY;
    return (this.stomp.watch('/topic/resources') as Observable<IMessage>).pipe(
      map(msg => JSON.parse(msg.body) as ResourceChangeMessage)
    );
  }

  /** VM-only stream: every power-state transition. */
  vmStatus$(): Observable<AzureResource> {
    this.start();
    if (!this.stomp) return EMPTY;
    return (this.stomp.watch('/topic/vm-status') as Observable<IMessage>).pipe(
      map(msg => JSON.parse(msg.body) as AzureResource)
    );
  }

  private buildHeaders(): Record<string, string> {
    const token = this.auth.token();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  ngOnDestroy(): void { this.stop(); }
}
