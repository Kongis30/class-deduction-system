import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface TransportDriver {
  id: EntityId;
  driverName: string;
  displayOrder: number;
}

export interface TransportDriverAssignment {
  assignmentKey: string;
  driverId?: EntityId;
  driverName?: string;
  transportType?: 'PICKUP' | 'DROPOFF' | 'BOTH';
}

export interface TransportDriverSavePayload {
  driverName: string;
  displayOrder?: number;
}

export interface TransportDriverAssignmentPayload {
  orderId: EntityId;
  orderItemId?: EntityId;
  studentId: EntityId;
  participantIndex?: number;
  transportType: 'PICKUP' | 'DROPOFF';
  driverId?: EntityId | null;
}

export function fetchTransportDrivers() {
  return http.get<ApiResponse<TransportDriver[]>>('/v1/transport/drivers');
}

export function createTransportDriver(data: TransportDriverSavePayload) {
  return http.post<ApiResponse<EntityId>>('/v1/transport/drivers', data);
}

export function updateTransportDriver(id: EntityId, data: TransportDriverSavePayload) {
  return http.put<ApiResponse<void>>(`/v1/transport/drivers/${id}`, data);
}

export function deleteTransportDriver(id: EntityId) {
  return http.delete<ApiResponse<void>>(`/v1/transport/drivers/${id}`);
}

export function fetchTransportDriverAssignments() {
  return http.get<ApiResponse<TransportDriverAssignment[]>>('/v1/transport/assignments');
}

export function saveTransportDriverAssignment(data: TransportDriverAssignmentPayload) {
  return http.put<ApiResponse<void>>('/v1/transport/assignments', data);
}
