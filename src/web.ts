import { WebPlugin } from '@capacitor/core';

import type { BackgroundLocationPlugin, LocationData } from './definitions';

export class BackgroundLocationWeb extends WebPlugin implements BackgroundLocationPlugin {
  private locations: LocationData[] = [];
  private tracking = false;
  private interval: any = null;
  private reference = 'my-tracking';

  constructor() {
    super();
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
}
