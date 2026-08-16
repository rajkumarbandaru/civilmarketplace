import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { GpsFixed, GpsOff } from '@mui/icons-material';
import { reportPosition } from '../services/trackingApi';
import { useDateTime } from '../providers/UiConfigProvider';

/**
 * Smallest gap between pings sent to the server.
 *
 * `watchPosition` can fire several times a second while the GPS settles, and forwarding every one
 * of those would be a write per fix for no visible benefit — the customer's view polls every ten
 * seconds regardless. Throttling here rather than debouncing keeps the *first* fix immediate,
 * which is what makes the toggle feel like it did something.
 */
const MIN_PING_INTERVAL_MS = 8_000;

type Status = 'idle' | 'starting' | 'sharing' | 'denied' | 'unavailable' | 'error';

/**
 * Lets the assigned worker broadcast their position for one booking.
 *
 * Off by default and never started implicitly: this is continuous location sharing, and it begins
 * only when the worker deliberately turns it on. Turning it off stops the watch immediately, and
 * leaving the page does the same — nothing keeps reporting once the screen is gone, which is the
 * behaviour a worker assumes when they close the tab.
 *
 * The last fix stays on the server after sharing stops. That is intentional: the customer seeing
 * "last seen two minutes ago near X" is more useful than the marker vanishing at the moment the
 * worker parks and puts their phone away.
 */
const ShareMyLocationToggle: React.FC<{ bookingId: number; onPing?: () => void }> = ({
  bookingId,
  onPing,
}) => {
  const { formatTime } = useDateTime();
  const [sharing, setSharing] = useState(false);
  const [status, setStatus] = useState<Status>('idle');
  const [message, setMessage] = useState<string | null>(null);
  const [lastSent, setLastSent] = useState<Date | null>(null);
  const [note, setNote] = useState('');

  const watchId = useRef<number | null>(null);
  const lastPingAt = useRef(0);
  // Read inside the geolocation callback, which closes over its first render otherwise and would
  // keep sending the note as it was when sharing started.
  const noteRef = useRef(note);
  useEffect(() => {
    noteRef.current = note;
  }, [note]);

  const stop = useCallback(() => {
    if (watchId.current !== null) {
      navigator.geolocation.clearWatch(watchId.current);
      watchId.current = null;
    }
    setSharing(false);
    setStatus('idle');
  }, []);

  // Stop on unmount. Without this the watch outlives the page and the worker keeps transmitting
  // from a screen they have navigated away from.
  useEffect(() => stop, [stop]);

  const start = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setStatus('unavailable');
      setMessage('This browser cannot report location.');
      return;
    }

    setStatus('starting');
    setMessage(null);

    watchId.current = navigator.geolocation.watchPosition(
      async (fix) => {
        setStatus('sharing');

        const now = Date.now();
        if (now - lastPingAt.current < MIN_PING_INTERVAL_MS) return;
        lastPingAt.current = now;

        try {
          await reportPosition(bookingId, {
            lat: fix.coords.latitude,
            lng: fix.coords.longitude,
            // The API takes whole degrees; the device reports a float, and NaN when stationary.
            headingDeg:
              fix.coords.heading !== null && !Number.isNaN(fix.coords.heading)
                ? Math.round(fix.coords.heading)
                : undefined,
            // m/s from the device, km/h on the wire.
            speedKph:
              fix.coords.speed !== null && !Number.isNaN(fix.coords.speed)
                ? Math.round(fix.coords.speed * 3.6 * 10) / 10
                : undefined,
            note: noteRef.current.trim() || undefined,
          });
          setLastSent(new Date());
          setMessage(null);
          onPing?.();
        } catch {
          // A failed ping does not stop the watch: a dropped signal in a lift or a basement is
          // normal on a delivery, and killing the session for it would mean the worker has to
          // notice and restart sharing every time they lose bars.
          setMessage('Could not reach the server — still trying.');
        }
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          setStatus('denied');
          setMessage('Location permission was refused. Allow it in the browser to share.');
        } else {
          setStatus('error');
          setMessage(error.message || 'Could not get a location fix.');
        }
        setSharing(false);
        watchId.current = null;
      },
      // High accuracy because street-level is the whole point here; the timeout keeps a phone
      // that cannot get a fix from hanging on "starting" indefinitely.
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 20_000 }
    );
  }, [bookingId, onPing]);

  const toggle = (next: boolean) => {
    setSharing(next);
    if (next) start();
    else stop();
  };

  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mb: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" useFlexGap>
        <FormControlLabel
          control={<Switch checked={sharing} onChange={(e) => toggle(e.target.checked)} />}
          label={
            <Stack direction="row" alignItems="center" spacing={0.75}>
              {sharing ? <GpsFixed fontSize="small" /> : <GpsOff fontSize="small" />}
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                Share my location
              </Typography>
            </Stack>
          }
        />
        {status === 'starting' && <CircularProgress size={16} />}
        {status === 'sharing' && <Chip size="small" color="success" label="Live" />}
        {lastSent && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            last sent {formatTime(lastSent)}
          </Typography>
        )}
      </Stack>

      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
        The customer sees your position while this is on. It stops when you switch it off or leave
        this page.
      </Typography>

      <Box sx={{ mt: 1 }}>
        <TextField
          size="small"
          fullWidth
          label="Status note (optional)"
          placeholder="On the way · Collecting materials"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          inputProps={{ maxLength: 120 }}
          helperText="Shown to the customer with your position"
        />
      </Box>

      {message && (
        <Alert
          severity={status === 'denied' || status === 'unavailable' ? 'warning' : 'info'}
          sx={{ mt: 1.5 }}
        >
          {message}
        </Alert>
      )}
    </Paper>
  );
};

export default ShareMyLocationToggle;
