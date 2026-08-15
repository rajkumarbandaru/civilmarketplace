import api from './api';

/**
 * Client for booking-service's live tracking endpoints.
 *
 * Polled rather than streamed — see `BookingTrackingController` for why. The poll interval lives
 * with the page that does the polling, not here.
 */

export interface TrackingPosition {
  lat: number;
  lng: number;
  headingDeg: number | null;
  speedKph: number | null;
  note: string | null;
  updatedAt: string;
  /** The last fix is older than the service's freshness window — show it as lost, not as parked. */
  stale: boolean;
  /** Straight-line, not road distance. Labelled as such wherever it is shown. */
  distanceKm: number | null;
  etaMinutes: number | null;
}

export interface TrackingSnapshot {
  success: boolean;
  bookingId: number;
  bookingStatus: string;
  customerId: number | null;
  workerId: number | null;
  /** The caller is the assigned worker, so the page may offer the sharing control. */
  viewerIsWorker: boolean;
  destinationLat: number | null;
  destinationLng: number | null;
  addressLine: string | null;
  /** Null before the worker starts sharing — the normal first state, not an error. */
  tracking: TrackingPosition | null;
  message?: string;
}

export const fetchTracking = async (bookingId: number): Promise<TrackingSnapshot> => {
  const { data } = await api.get<TrackingSnapshot>(`/bookings/${bookingId}/tracking`);
  return data;
};

export interface TrackingPing {
  lat: number;
  lng: number;
  headingDeg?: number;
  speedKph?: number;
  note?: string;
}

/** Only the assigned worker (or an admin) may call this; the service enforces it. */
export const reportPosition = async (
  bookingId: number,
  ping: TrackingPing
): Promise<TrackingPosition> => {
  const { data } = await api.put(`/bookings/${bookingId}/tracking`, ping);
  return data;
};

/**
 * Turn-by-turn hand-off to a real navigation app.
 *
 * The in-app view answers "where are they"; it deliberately does not try to be a satnav. A worker
 * who needs to *drive* there gets the platform's own maps app, which knows the roads.
 */
export const directionsUrl = (
  fromLat: number,
  fromLng: number,
  toLat: number,
  toLng: number
): string =>
  `https://www.google.com/maps/dir/?api=1&origin=${fromLat},${fromLng}&destination=${toLat},${toLng}&travelmode=driving`;

export const mapUrl = (lat: number, lng: number): string =>
  `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
