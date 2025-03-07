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

export interface BackgroundLocationPlugin {

  startTracking({reference, highAccuracy, minDistance, interval }: {reference: string, highAccuracy: boolean, minDistance: number, interval: number}): Promise<void>;

  stopTracking(): Promise<void>;

  getStoredLocations({reference}: {reference: string}): Promise<{ locations: LocationData[] }>;

  clearStoredLocations(): Promise<void>;

  addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void): Promise<PluginListenerHandle>;

  addListener(eventName: 'locationStatus', listenerFunc: (status: { enabled: boolean }) => void): Promise<PluginListenerHandle>;

  getLastLocation({reference}: {reference: string}): Promise<void>;

  startLocationStatusTracking(): Promise<void>;

  stopLocationStatusTracking(): Promise<void>;

}