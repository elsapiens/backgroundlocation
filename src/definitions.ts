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

  startTracking({reference}: {reference: string}): Promise<void>;

  stopTracking(): Promise<void>;

  getStoredLocations({reference}: {reference: string}): Promise<{ locations: LocationData[] }>;

  clearStoredLocations(): Promise<void>;

  addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void): Promise<PluginListenerHandle>;

  getLastLocation({reference}: {reference: string}): Promise<void>;

}