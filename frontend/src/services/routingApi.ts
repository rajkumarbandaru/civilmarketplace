/**
 * Road routing — the *navigation* half of tracking.
 *
 * Tracking answers "where is the worker now" from GPS pings. This answers "what road route gets
 * them there", which is a different question and needs a routing engine: GPS gives you a point,
 * not a path. The two are kept apart deliberately — a stale GPS fix must not invalidate a route,
 * and a routing outage must not stop the live marker from moving.
 *
 * OSRM is used because it needs no API key and no billing. The public demo server is rate-limited
 * and explicitly not for production; when that matters, self-host OSRM (or swap in another
 * provider) and change {@link ROUTING_BASE} — everything above this module talks in `RoadRoute`
 * and does not know which engine produced it.
 */

const ROUTING_BASE =
  import.meta.env.VITE_ROUTING_BASE_URL || 'https://router.project-osrm.org';

/** One instruction in the route. */
export interface RouteStep {
  /** "turn", "depart", "arrive", "roundabout"… */
  type: string;
  /** "left", "right", "slight left"… absent on depart/arrive. */
  modifier: string | null;
  /** Street name, empty for unnamed roads. */
  road: string;
  distanceMeters: number;
  durationSeconds: number;
}

export interface RoadRoute {
  /** By road, not straight-line — the number the ETA is actually based on. */
  distanceKm: number;
  durationMinutes: number;
  /** [lat, lng] pairs, already flipped from OSRM's [lng, lat] order. */
  geometry: [number, number][];
  steps: RouteStep[];
}

/**
 * Fetches the driving route between two points.
 *
 * Returns null rather than throwing on any failure. A missing route degrades the screen to plain
 * tracking, which is still useful; making the whole panel error out because a third-party router
 * was slow would take the live position down with it.
 */
export const fetchRoute = async (
  fromLat: number,
  fromLng: number,
  toLat: number,
  toLng: number,
  signal?: AbortSignal
): Promise<RoadRoute | null> => {
  // OSRM takes lng,lat — the reverse of how coordinates are written everywhere else in this app.
  const coords = `${fromLng},${fromLat};${toLng},${toLat}`;
  const url =
    `${ROUTING_BASE}/route/v1/driving/${coords}` +
    `?overview=full&geometries=geojson&steps=true&alternatives=false`;

  try {
    const response = await fetch(url, { signal });
    if (!response.ok) return null;

    const payload = await response.json();
    if (payload?.code !== 'Ok' || !payload?.routes?.length) return null;

    const route = payload.routes[0];
    const steps = (route.legs?.[0]?.steps ?? []).map(
      (step: Record<string, any>): RouteStep => ({
        type: step.maneuver?.type ?? 'continue',
        modifier: step.maneuver?.modifier ?? null,
        road: step.name ?? '',
        distanceMeters: Math.round(step.distance ?? 0),
        durationSeconds: Math.round(step.duration ?? 0),
      })
    );

    return {
      distanceKm: Math.round((route.distance / 1000) * 100) / 100,
      durationMinutes: Math.round(route.duration / 60),
      geometry: (route.geometry?.coordinates ?? []).map(
        ([lng, lat]: [number, number]) => [lat, lng] as [number, number]
      ),
      steps,
    };
  } catch {
    // Includes the abort that fires when the component unmounts mid-request, which is expected
    // rather than exceptional.
    return null;
  }
};

/** "turn" + "left" → "Turn left". Kept out of the component so both panels phrase it identically. */
export const describeStep = (step: RouteStep): string => {
  const direction = step.modifier ? ` ${step.modifier}` : '';
  const onto = step.road ? ` onto ${step.road}` : '';

  switch (step.type) {
    case 'depart':
      return `Head off${step.road ? ` on ${step.road}` : ''}`;
    case 'arrive':
      return 'Arrive at the destination';
    case 'roundabout':
    case 'rotary':
      return `Take the roundabout${onto}`;
    case 'merge':
      return `Merge${direction}${onto}`;
    case 'fork':
      return `Keep${direction}${onto}`;
    case 'new name':
      return `Continue${onto}`;
    case 'end of road':
      return `At the end of the road, turn${direction}${onto}`;
    default:
      return `${step.type.charAt(0).toUpperCase()}${step.type.slice(1)}${direction}${onto}`;
  }
};

export const formatDistance = (meters: number): string =>
  meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${meters} m`;
