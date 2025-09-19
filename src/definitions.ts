import type { PluginListenerHandle } from "@capacitor/core";

export interface LocationData {
  reference: string;
  index: number;
  latitude: number;
  longitude: number;
  altitude?: number;
  speed?: number;
  heading?: number;
  accuracy: number;
  altitudeAccuracy?: number;
  totalDistance?: number;
  timestamp: number;
}

export interface WorkHourLocationData {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude?: number;
  speed?: number;
  heading?: number;
  timestamp: number;
  engineerId?: string;
  uploadAttempts?: number;
}

export interface PermissionStatus {
  location: 'prompt' | 'denied' | 'granted';
  backgroundLocation: 'prompt' | 'denied' | 'granted';
  foregroundService: 'prompt' | 'denied' | 'granted';
}

export interface WorkHourTrackingOptions {
  engineerId: string;
  uploadInterval: number; // in minutes, default 5
  serverUrl: string;
  authToken?: string;
  enableOfflineQueue?: boolean; // queue locations when offline
}

export interface BackgroundLocationPlugin {

  // Permission methods
  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<PermissionStatus>;
  isLocationServiceEnabled(): Promise<{ enabled: boolean }>;
  openLocationSettings(): Promise<void>;

  startTracking({reference, highAccuracy, minDistance, interval }: {reference: string, highAccuracy: boolean, minDistance: number, interval: number}): Promise<void>;

  stopTracking(): Promise<void>;

  getStoredLocations({reference}: {reference: string}): Promise<{ locations: LocationData[] }>;

  getCurrentLocation(): Promise<{
    latitude: number;
    longitude: number;
    accuracy: number;
    altitude?: number;
    speed?: number;
    heading?: number;
    timestamp: number;
  }>;

  clearStoredLocations(): Promise<void>;

  addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void): Promise<PluginListenerHandle>;

  addListener(eventName: 'locationStatus', listenerFunc: (status: { enabled: boolean }) => void): Promise<PluginListenerHandle>;

  addListener(eventName: 'workHourLocationUpdate', listenerFunc: (data: WorkHourLocationData) => void): Promise<PluginListenerHandle>;

  addListener(eventName: 'workHourLocationUploaded', listenerFunc: (data: { success: boolean, location: WorkHourLocationData, error?: string }) => void): Promise<PluginListenerHandle>;

  getLastLocation({reference}: {reference: string}): Promise<void>;

  startLocationStatusTracking(): Promise<void>;

  stopLocationStatusTracking(): Promise<void>;

  // Work hour tracking methods
  startWorkHourTracking(options: WorkHourTrackingOptions): Promise<void>;
  
  stopWorkHourTracking(): Promise<void>;
  
  isWorkHourTrackingActive(): Promise<{ active: boolean }>;
  
  getQueuedWorkHourLocations(): Promise<{ locations: WorkHourLocationData[] }>;
  
  clearQueuedWorkHourLocations(): Promise<void>;

}