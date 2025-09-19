import { WebPlugin } from '@capacitor/core';

import type { BackgroundLocationPlugin, LocationData, PermissionStatus } from './definitions';

export class BackgroundLocationWeb extends WebPlugin implements BackgroundLocationPlugin {
  private locations: LocationData[] = [];
  private tracking = false;
  private interval: any = null;
  private reference = 'my-tracking';

  constructor() {
    super();
  }

  // New permission methods for web
  async checkPermissions(): Promise<PermissionStatus> {
    // Web platform permissions simulation
    return {
      location: 'granted',
      backgroundLocation: 'granted', 
      foregroundService: 'granted'
    };
  }

  async requestPermissions(): Promise<PermissionStatus> {
    // Web platform doesn't need actual permission requests
    return this.checkPermissions();
  }

  async isLocationServiceEnabled(): Promise<{ enabled: boolean }> {
    // Web platform location services simulation
    return { enabled: true };
  }

  async openLocationSettings(): Promise<void> {
    // Web platform doesn't have location settings
    console.warn('Location settings not available on web platform');
    return Promise.resolve();
  }

  startLocationStatusTracking(): Promise<void> {
    setTimeout(() => {
      this.notifyListeners('locationStatus', { enabled: true });
    }, 10);
    return Promise.resolve();
  }
  
  stopLocationStatusTracking(): Promise<void> {
    return Promise.resolve();
  }

  async startTracking({ reference }: { reference: string }): Promise<void> {
    this.reference = reference;
    this.tracking = true;
    this.simulateLocationTracking();
  }

  async stopTracking(): Promise<void> {
    this.tracking = false;
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }

  async getStoredLocations({ reference }: { reference: string }): Promise<{ locations: LocationData[] }> {
    return { locations: this.locations.filter((location) => location.reference === reference) };
  }

  async getCurrentLocation(): Promise<{
    latitude: number;
    longitude: number;
    accuracy: number;
    altitude?: number;
    speed?: number;
    heading?: number;
    timestamp: number;
  }> {
    return {
      latitude: 37.7749,
      longitude: -122.4194,
      accuracy: 5,
      altitude: 10,
      speed: 0,
      heading: 0,
      timestamp: Date.now(),
    };
  }

  async clearStoredLocations(): Promise<void> {
    this.locations = [];
  }

  async getLastLocation({ reference }: { reference: string }): Promise<void> {
    const locations = this.locations.filter((location) => location.reference === reference);
    if (locations.length > 0) {
      const lastLocation = locations[locations.length - 1];
      this.notifyListeners('locationUpdate', lastLocation);
    }
  }

  private simulateLocationTracking(): void {
    if (!this.tracking) return;

    // Simulate adding a new location every second
    let latitude = 37.7749; // Starting latitude
    let longitude = -122.4194; // Starting longitude
    const direction = 0.001; // Change in position

    if (this.interval) {
      clearInterval(this.interval); // ✅ Properly clear the previous interval
    }
    this.interval = setInterval(() => {
      if (this.tracking) {
        latitude += direction * (Math.random() > 0.5 ? 1 : -1);
        longitude += direction * (Math.random() > 0.5 ? 1 : -1);

        const newLocation: LocationData = {
          reference: this.reference,
          index: this.locations.length,
          latitude,
          longitude,
          timestamp: Date.now(),
          accuracy: Math.random() * 10,
          speed: Math.random() * 30,
        };
        this.locations.push(newLocation);
        this.notifyListeners('locationUpdate', newLocation);
      }
    }, 2000);
  }

  // Work Hour Tracking methods (web stubs)
  async startWorkHourTracking(options: {
    engineerId: string;
    uploadInterval?: number;
    serverUrl: string;
    authToken?: string;
    enableOfflineQueue?: boolean;
  }): Promise<void> {
    console.warn('Work hour tracking not available on web platform', options);
    return Promise.resolve();
  }

  async stopWorkHourTracking(): Promise<void> {
    console.warn('Work hour tracking not available on web platform');
    return Promise.resolve();
  }

  async isWorkHourTrackingActive(): Promise<{ active: boolean }> {
    console.warn('Work hour tracking not available on web platform');
    return { active: false };
  }

  async getQueuedWorkHourLocations(): Promise<{ locations: any[] }> {
    console.warn('Work hour tracking not available on web platform');
    return { locations: [] };
  }

  async clearQueuedWorkHourLocations(): Promise<void> {
    console.warn('Work hour tracking not available on web platform');
    return Promise.resolve();
  }
}
