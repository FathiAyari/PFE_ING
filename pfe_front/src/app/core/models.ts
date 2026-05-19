export type ImageStatus = 'SAFE' | 'UNSAFE';

export interface DockerImage {
  id: number;
  name: string;
  tag: string;
  registry: string;
  status: ImageStatus;
  reason?: string;
  vulnerabilityCount: number;
  riskScore: number;
  scannedAt: string;
  digest?: string;
  sizeBytes?: number;
}

export type PipelineStatus = 'SUCCESS' | 'FAILED' | 'RUNNING' | 'CANCELLED';

export interface PipelineRun {
  id: number;
  name: string;
  branch: string;
  commitSha?: string;
  triggeredBy?: string;
  status: PipelineStatus;
  startedAt: string;
  finishedAt?: string;
  durationSeconds?: number;
  failureStage?: string;
}

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface SecurityAlert {
  id: number;
  title: string;
  description?: string;
  severity: AlertSeverity;
  source?: string;
  cveId?: string;
  affectedImage?: string;
  acknowledged: boolean;
  createdAt: string;
}

export interface SystemNode {
  id: number;
  name: string;
  type: string;
  status: string;
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  host?: string;
  ipAddress?: string;
  lastCheck: string;
}

export interface Deployment {
  id: number;
  image: DockerImage;
  environment: string;
  status: string;
  triggeredBy?: string;
  notes?: string;
  deployedAt: string;
}

export interface AuditLog {
  id: number;
  action: string;
  actor: string;
  target: string;
  details?: string;
  result: string;
  timestamp: string;
}

export interface DashboardStats {
  totalImages: number;
  safeImages: number;
  unsafeImages: number;
  openAlerts: number;
  pipelinesSuccess: number;
  pipelinesFailed: number;
  deployments: number;
  alertsBySeverity: Record<string, number>;
}

export interface DeployRequest {
  environment: string;
  triggeredBy?: string;
  notes?: string;
}

export interface IaCResource {
  address: string;
  name: string;
  type: string;
  category: 'compute' | 'network' | 'security' | 'registry' | string;
  icon: string;
  description: string;
  status: 'PLANNED' | 'APPLIED' | string;
  properties: Record<string, string>;
}

export interface IaCSummary {
  project: string;
  provider: string;
  location: string;
  stateApplied: boolean;
  totalResources: number;
  appliedResources: number;
  resources: IaCResource[];
}
