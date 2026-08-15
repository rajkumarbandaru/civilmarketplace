import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { ArrowBack, DirectionsCar, Search } from '@mui/icons-material';
import BookingTrackingPanel from '../../components/BookingTrackingPanel';
import { AdminBooking, bookingApi } from '../../services/adminApi';

/**
 * Statuses worth tracking. Anything earlier has nobody travelling yet, anything later has already
 * arrived — showing those would bury the handful of jobs actually in motion.
 */
const TRACKABLE = ['CONFIRMED', 'ASSIGNED', 'IN_PROGRESS'];

/**
 * Live tracking across the whole platform, for staff.
 *
 * The member-facing page answers "where is *my* plumber". This answers "where is the plumber for
 * booking #482", which is the question support is actually asked — and they have no way to reach
 * the customer's own screen to find out. The panel underneath is the same component the customer
 * sees, so staff and caller are looking at identical information while talking to each other.
 */
const AdminTrackingPage: React.FC = () => {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'tracking', 'bookings', status],
    queryFn: async () => {
      const response = await bookingApi.getBookings({
        page: 0,
        size: 100,
        ...(status ? { status } : {}),
      });
      return response.data?.data ?? [];
    },
    // Bookings move between statuses while staff watch this list; a minute-old roster would show
    // jobs that finished and hide ones that just started.
    refetchInterval: 30_000,
  });

  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return (data ?? [])
      .filter((booking: AdminBooking) => TRACKABLE.includes(booking.status))
      .filter(
        (booking: AdminBooking) =>
          !query ||
          [booking.bookingCode, booking.customerName, booking.workerName, booking.serviceName, booking.city]
            .filter(Boolean)
            .some((field) => String(field).toLowerCase().includes(query))
      );
  }, [data, search]);

  if (selectedId !== null) {
    return (
      <Box sx={{ p: 3 }}>
        <Button
          startIcon={<ArrowBack />}
          onClick={() => setSelectedId(null)}
          sx={{ mb: 2, textTransform: 'none' }}
        >
          Back to active jobs
        </Button>
        <Box sx={{ maxWidth: 760 }}>
          <BookingTrackingPanel bookingId={selectedId} />
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        Live Tracking
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
        Every job currently in motion across the platform
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <TextField
          size="small"
          placeholder="Booking code, customer, worker, city…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          InputProps={{ startAdornment: <Search fontSize="small" sx={{ mr: 1 }} /> }}
          sx={{ minWidth: 300 }}
        />
        <TextField
          size="small"
          select
          label="Status"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All active</MenuItem>
          {TRACKABLE.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {isLoading && <CircularProgress />}
      {isError && <Alert severity="error">Could not load bookings.</Alert>}

      {data && (
        <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Booking</TableCell>
                  <TableCell>Service</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Worker</TableCell>
                  <TableCell>City</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((booking: AdminBooking) => (
                  <TableRow key={booking.id} hover>
                    <TableCell>{booking.bookingCode}</TableCell>
                    <TableCell>{booking.serviceName}</TableCell>
                    <TableCell>{booking.customerName}</TableCell>
                    <TableCell>
                      {/* No worker means nobody is travelling, so there is nothing to open. */}
                      {booking.workerName || <em style={{ opacity: 0.6 }}>unassigned</em>}
                    </TableCell>
                    <TableCell>{booking.city}</TableCell>
                    <TableCell>
                      <Chip size="small" label={booking.status} />
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<DirectionsCar />}
                        onClick={() => setSelectedId(booking.id)}
                        sx={{ textTransform: 'none' }}
                      >
                        Track
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                      No jobs are in motion right now.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
        </Paper>
      )}
    </Box>
  );
};

export default AdminTrackingPage;
