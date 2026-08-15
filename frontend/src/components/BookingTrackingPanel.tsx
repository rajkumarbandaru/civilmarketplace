import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { DirectionsCar, LocationOn, MyLocation, NearMe, Refresh } from '@mui/icons-material';
import ShareMyLocationToggle from './ShareMyLocationToggle';
import RouteDirections from './RouteDirections';
import { RoadRoute, fetchRoute } from '../services/routingApi';
import { TrackingSnapshot, directionsUrl, fetchTracking, mapUrl } from '../services/trackingApi';

/**
 * How often the page asks for a new fix.
 *
 * Ten seconds is roughly how often a moving vehicle changes position meaningfully at city speeds;
 * polling faster mostly redraws the same pixel. Slower than about thirty and the marker visibly
 * jumps, which reads as a broken map rather than a slow one.
 */
const POLL_MS = 10_000;

/**
 * A minimal two-point map drawn as SVG.
 *
 * Deliberately not Google Maps: an embedded map needs a working billable API key and a network
 * round trip, and when either is missing the customer gets a grey box where the answer should be.
 * This always renders — it shows the worker, the destination, the line between them and the
 * distance, which is the actual question ("how far away are they, and are they getting closer").
 * The link out to a real maps app is there for anyone who needs streets.
 */
const RelativeMap: React.FC<{
  workerLat: number;
  workerLng: number;
  destLat: number;
  destLng: number;
  headingDeg: number | null;
  stale: boolean;
  /** Road geometry, when routing succeeded. Absent falls back to the straight dashed line. */
  route: [number, number][] | null;
}> = ({ workerLat, workerLng, destLat, destLng, headingDeg, stale, route }) => {
  const WIDTH = 520;
  const HEIGHT = 260;
  const PAD = 44;

  // Fit both points into the box with padding. A degenerate span (the two points identical, or on
  // the same line of latitude) would divide by zero, so the span has a floor.
  // The route swings wide of the straight line between the two points, so the extent has to be
  // computed over the path as well — fitting only the endpoints would clip the road off the edge.
  const lats = [workerLat, destLat, ...(route ?? []).map(([lat]) => lat)];
  const lngs = [workerLng, destLng, ...(route ?? []).map(([, lng]) => lng)];
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const latSpan = Math.max(maxLat - minLat, 0.0005);
  const lngSpan = Math.max(maxLng - minLng, 0.0005);

  const x = (lng: number) => PAD + ((lng - minLng) / lngSpan) * (WIDTH - PAD * 2);
  // Screen y grows downward while latitude grows north, so this axis is inverted.
  const y = (lat: number) => HEIGHT - PAD - ((lat - minLat) / latSpan) * (HEIGHT - PAD * 2);

  const wx = x(workerLng);
  const wy = y(workerLat);
  const dx = x(destLng);
  const dy = y(destLat);

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      sx={{ width: '100%', height: 'auto', borderRadius: 2, bgcolor: 'action.hover' }}
      role="img"
      aria-label="Relative position of the worker and the destination"
    >
      <defs>
        <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
          <path d="M0,0 L0,6 L7,3 z" fill="currentColor" opacity="0.45" />
        </marker>
      </defs>

      {route && route.length > 1 ? (
        <polyline
          points={route.map(([lat, lng]) => `${x(lng)},${y(lat)}`).join(' ')}
          fill="none"
          stroke="#2563eb"
          strokeOpacity="0.55"
          strokeWidth="3.5"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      ) : (
        /* No route: the dashed straight line is honest about being a direction, not a path. */
        <line
          x1={wx}
          y1={wy}
          x2={dx}
          y2={dy}
          stroke="currentColor"
          strokeOpacity="0.35"
          strokeWidth="2"
          strokeDasharray="6 5"
          markerEnd="url(#arrow)"
        />
      )}

      {/* Destination */}
      <circle cx={dx} cy={dy} r="9" fill="#ef4444" />
      <circle cx={dx} cy={dy} r="16" fill="#ef4444" fillOpacity="0.18" />
      <text x={dx} y={dy - 22} textAnchor="middle" fontSize="12" fill="currentColor">
        Destination
      </text>

      {/* Worker. A stale fix is drawn hollow so "we lost them" never looks like "they are here". */}
      <circle
        cx={wx}
        cy={wy}
        r="9"
        fill={stale ? 'none' : '#2563eb'}
        stroke="#2563eb"
        strokeWidth="2.5"
      />
      {headingDeg !== null && (
        <g transform={`translate(${wx} ${wy}) rotate(${headingDeg})`}>
          <path d="M0,-20 L5,-11 L-5,-11 z" fill="#2563eb" />
        </g>
      )}
      <text x={wx} y={wy + 28} textAnchor="middle" fontSize="12" fill="currentColor">
        {stale ? 'Last seen' : 'On the way'}
      </text>
    </Box>
  );
};

/** The live view for one booking. Shared by the member page and the admin console. */
export const BookingTrackingPanel: React.FC<{ bookingId: number }> = ({ bookingId }) => {
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [route, setRoute] = useState<RoadRoute | null>(null);
  const [routeLoading, setRouteLoading] = useState(false);

  const { data, isLoading, isError, refetch, dataUpdatedAt } = useQuery<TrackingSnapshot>({
    queryKey: ['tracking', bookingId],
    queryFn: () => fetchTracking(bookingId),
    // Stops polling when the tab is hidden as well as when the user turns it off — a page left
    // open in a background tab has nobody watching the marker move.
    refetchInterval: autoRefresh ? POLL_MS : false,
    refetchIntervalInBackground: false,
  });

  // Named separately so the route effect below can depend on it without tripping the rule that
  // hooks run before the early returns for loading and error.
  const dataForRoute = data;

  const position = dataForRoute?.tracking ?? null;

  // Refetched only when the worker has actually moved ~50m or more. Re-routing on every 10s poll
  // would hammer a shared public router to redraw a line that has not visibly changed — and OSRM's
  // demo server is rate-limited, so a screen left open would eventually be throttled out of
  // directions entirely.
  const routeKey =
    position && dataForRoute?.destinationLat != null && dataForRoute?.destinationLng != null
      ? [
          position.lat.toFixed(3),
          position.lng.toFixed(3),
          dataForRoute.destinationLat.toFixed(4),
          dataForRoute.destinationLng.toFixed(4),
        ].join(',')
      : null;

  useEffect(() => {
    if (!routeKey) {
      setRoute(null);
      return undefined;
    }
    const controller = new AbortController();
    setRouteLoading(true);
    const [wLat, wLng, dLat, dLng] = routeKey.split(',').map(Number);
    fetchRoute(wLat, wLng, dLat, dLng, controller.signal).then((result) => {
      // A late response from a previous position must not overwrite a newer one; aborting on
      // cleanup makes that resolve to null and the guard below drops it.
      if (!controller.signal.aborted) {
        setRoute(result);
        setRouteLoading(false);
      }
    });
    return () => controller.abort();
  }, [routeKey]);

  if (isLoading) return <CircularProgress />;
  if (isError || !data) {
    return <Alert severity="error">Could not load tracking for this booking.</Alert>;
  }

  const hasDestination = data.destinationLat !== null && data.destinationLng !== null;

  return (
    <Card sx={{ borderRadius: 3 }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }} flexWrap="wrap">
          <Typography variant="h6" sx={{ fontWeight: 700, flexGrow: 1 }}>
            Booking #{data.bookingId}
          </Typography>
          <Chip size="small" label={data.bookingStatus} />
          <Button
            size="small"
            startIcon={<Refresh />}
            onClick={() => refetch()}
            sx={{ textTransform: 'none' }}
          >
            Refresh
          </Button>
          <Button
            size="small"
            onClick={() => setAutoRefresh((value) => !value)}
            sx={{ textTransform: 'none' }}
          >
            {autoRefresh ? 'Pause live updates' : 'Resume live updates'}
          </Button>
        </Stack>

        {data.addressLine && (
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
            <LocationOn fontSize="inherit" /> {data.addressLine}
          </Typography>
        )}

        {/* Only the assigned worker is offered the control — everyone else is watching, not
            broadcasting, and the endpoint refuses them anyway. */}
        {data.viewerIsWorker && (
          <ShareMyLocationToggle bookingId={bookingId} onPing={() => refetch()} />
        )}

        {!position && (
          <Alert severity="info">
            {data.message ?? 'The worker has not started sharing their location yet.'}
          </Alert>
        )}

        {position && !hasDestination && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            This booking has no destination coordinates saved, so distance and direction cannot be
            worked out. The last reported position is still shown below.
          </Alert>
        )}

        {position && (
          <>
            {position.stale && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                No update for a few minutes — this is the last known position, not a live one.
              </Alert>
            )}

            {hasDestination && (
              <Box sx={{ mb: 2 }}>
                <RelativeMap
                  workerLat={position.lat}
                  workerLng={position.lng}
                  destLat={data.destinationLat as number}
                  destLng={data.destinationLng as number}
                  headingDeg={position.headingDeg}
                  stale={position.stale}
                  route={route?.geometry ?? null}
                />
              </Box>
            )}

            <Grid container spacing={2} sx={{ mb: 2 }}>
              <Grid item xs={6} sm={3}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Direct distance
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {position.distanceKm !== null ? `${position.distanceKm} km` : '—'}
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Rough ETA
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {position.etaMinutes !== null ? `${position.etaMinutes} min` : '—'}
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Speed
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {position.speedKph !== null ? `${position.speedKph} km/h` : '—'}
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Last update
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {new Date(position.updatedAt).toLocaleTimeString()}
                </Typography>
              </Grid>
            </Grid>

            {position.note && (
              <Alert severity="info" icon={<DirectionsCar />} sx={{ mb: 2 }}>
                {position.note}
              </Alert>
            )}

            <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', mb: 2 }}>
              Distance is straight-line, not by road, and the ETA assumes city traffic. Use the
              directions link for an accurate route.
            </Typography>

            {hasDestination && (
              <RouteDirections
                route={route}
                loading={routeLoading}
                directDistanceKm={position.distanceKm}
                forWorker={data.viewerIsWorker}
              />
            )}

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 2 }}>
              <Button
                size="small"
                variant="outlined"
                startIcon={<MyLocation />}
                href={mapUrl(position.lat, position.lng)}
                target="_blank"
                rel="noopener noreferrer"
                sx={{ textTransform: 'none' }}
              >
                Worker on map
              </Button>
              {hasDestination && (
                <Button
                  size="small"
                  variant="contained"
                  startIcon={<NearMe />}
                  href={directionsUrl(
                    position.lat,
                    position.lng,
                    data.destinationLat as number,
                    data.destinationLng as number
                  )}
                  target="_blank"
                  rel="noopener noreferrer"
                  sx={{ textTransform: 'none' }}
                >
                  Directions
                </Button>
              )}
            </Stack>
          </>
        )}

        <Divider sx={{ my: 2 }} />
        <Typography variant="caption" sx={{ color: 'text.disabled' }}>
          {autoRefresh ? `Updating every ${POLL_MS / 1000}s · ` : 'Live updates paused · '}
          checked {new Date(dataUpdatedAt).toLocaleTimeString()}
        </Typography>
      </CardContent>
    </Card>
  );
};


export default BookingTrackingPanel;
