import React, { useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Stack,
  Typography,
  Button,
} from '@mui/material';
import { ArrowBack, DirectionsCar } from '@mui/icons-material';
import api from '../../services/api';
import BookingTrackingPanel from '../../components/BookingTrackingPanel';

/** Statuses where someone is actually travelling — anything else has nothing to track. */
const TRACKABLE = ['CONFIRMED', 'ASSIGNED', 'IN_PROGRESS'];

interface BookingSummary {
  id: number;
  bookingCode: string;
  serviceName: string | null;
  serviceCategory: string | null;
  status: string;
  addressLine: string | null;
}

/**
 * Live tracking for the customer's active bookings — the vehicle, plumber or electrician on the
 * way to them.
 *
 * With no booking in the URL it lists what can be tracked, because "track my booking" is only a
 * useful destination if it can tell you which bookings those are.
 */
const LiveTrackingPage: React.FC = () => {
  const navigate = useNavigate();
  const { bookingId } = useParams<{ bookingId?: string }>();

  const bookings = useQuery({
    queryKey: ['bookings', 'trackable'],
    queryFn: async () => {
      // Both sides of the platform land on this page: a customer waiting for someone, and a worker
      // who needs to open the booking to start sharing. Asking for both and merging means neither
      // has to be told which menu entry is "theirs" — a worker with no customer bookings simply
      // sees their jobs. A failure on one side does not blank the other.
      const [asCustomer, asWorker] = await Promise.allSettled([
        api.get('/bookings/customer', { params: { page: 0, size: 50 } }),
        api.get('/bookings/worker', { params: { page: 0, size: 50 } }),
      ]);

      // The endpoints have been seen returning both a bare array and a wrapped page, so this reads
      // defensively rather than crashing the page on the shape it did not expect.
      const unwrap = (result: PromiseSettledResult<{ data: unknown }>): BookingSummary[] => {
        if (result.status !== 'fulfilled') return [];
        const payload = result.value.data as
          | BookingSummary[]
          | { data?: BookingSummary[]; content?: BookingSummary[] };
        if (Array.isArray(payload)) return payload;
        return payload?.data ?? payload?.content ?? [];
      };

      const merged = [...unwrap(asCustomer), ...unwrap(asWorker)];
      // A booking where you are somehow both parties would otherwise appear twice.
      return Array.from(new Map(merged.map((booking) => [booking.id, booking])).values());
    },
    enabled: !bookingId,
  });

  const trackable = useMemo(
    () => (bookings.data ?? []).filter((booking) => TRACKABLE.includes(booking.status)),
    [bookings.data]
  );

  // Scroll to the top when opening a booking — the list can be long and the detail otherwise
  // renders below the fold.
  useEffect(() => {
    if (bookingId) window.scrollTo({ top: 0 });
  }, [bookingId]);

  if (bookingId) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <Button
          startIcon={<ArrowBack />}
          onClick={() => navigate('/track')}
          sx={{ mb: 2, textTransform: 'none' }}
        >
          All active bookings
        </Button>
        <BookingTrackingPanel bookingId={Number(bookingId)} />
      </Container>
    );
  }

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        Live Tracking
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
        Follow the vehicle or professional on their way to you
      </Typography>

      {bookings.isLoading && <CircularProgress />}
      {bookings.isError && <Alert severity="error">Could not load your bookings.</Alert>}

      {bookings.data && trackable.length === 0 && (
        <Card sx={{ borderRadius: 3 }}>
          <CardContent sx={{ textAlign: 'center', py: 6 }}>
            <DirectionsCar sx={{ fontSize: 44, color: 'text.disabled' }} />
            <Typography variant="subtitle1" sx={{ mt: 1, fontWeight: 600 }}>
              Nothing to track right now
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Tracking turns on once a booking is confirmed and someone is assigned to it.
            </Typography>
          </CardContent>
        </Card>
      )}

      <Stack spacing={1.5}>
        {trackable.map((booking) => (
          <Card
            key={booking.id}
            onClick={() => navigate(`/track/${booking.id}`)}
            sx={{ borderRadius: 3, cursor: 'pointer', '&:hover': { boxShadow: 4 } }}
          >
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
                  {booking.serviceName || booking.serviceCategory || 'Booking'} ·{' '}
                  {booking.bookingCode}
                </Typography>
                <Chip size="small" label={booking.status} />
              </Stack>
              {booking.addressLine && (
                <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
                  {booking.addressLine}
                </Typography>
              )}
            </CardContent>
          </Card>
        ))}
      </Stack>
    </Container>
  );
};

export default LiveTrackingPage;
