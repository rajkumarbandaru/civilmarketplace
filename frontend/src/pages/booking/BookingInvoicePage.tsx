import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, CircularProgress, Container, Divider, Typography,
} from '@mui/material';
import { CheckCircle, Payment as PaymentIcon } from '@mui/icons-material';
import api from '../../services/api';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { showSnackbar } from '../../store/slices/uiSlice';
import { apiErrorMessage } from '../../services/apiError';
import { payWithRazorpay } from '../../services/razorpayCheckout';
import { formatRupees } from '../../utils/bookingPricing';

/**
 * The invoice for a pay-later booking — where the "Pay now" link in the completion email lands.
 *
 * The amount is read from the booking rather than passed in the link: a total in a URL is a total
 * the recipient can edit.
 */

interface BookingSummary {
  id: number;
  bookingCode: string;
  serviceName: string;
  status: string;
  paymentStatus: string;
  paymentPreference?: string;
  finalCost?: number | null;
  platformFee?: number | null;
  gstAmount?: number | null;
  totalAmount?: number | null;
  scheduledDate?: string | null;
  addressLine?: string | null;
}

const BookingInvoicePage: React.FC = () => {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);

  const [booking, setBooking] = useState<BookingSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [paid, setPaid] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const { data } = await api.get<BookingSummary>(`/bookings/${bookingId}`);
        if (active) setBooking(data);
      } catch (error) {
        if (active) {
          dispatch(showSnackbar({
            message: apiErrorMessage(error, 'Could not load this invoice.'),
            severity: 'error',
          }));
        }
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [bookingId, dispatch]);

  const handlePay = async () => {
    if (!booking?.totalAmount) return;
    setPaying(true);
    try {
      const outcome = await payWithRazorpay({
        bookingId: booking.id,
        amount: Number(booking.totalAmount),
        customer: { name: user?.name, email: user?.email, contact: user?.phone },
        description: `${booking.serviceName} — ${booking.bookingCode}`,
      });

      if (outcome.status === 'paid') {
        setPaid(true);
      } else if (outcome.status === 'cancelled') {
        dispatch(showSnackbar({
          message: 'Payment cancelled. The invoice stays open — you can pay any time.',
          severity: 'info',
        }));
      } else {
        dispatch(showSnackbar({ message: outcome.message, severity: 'error' }));
      }
    } catch (error) {
      dispatch(showSnackbar({
        message: apiErrorMessage(error, 'Payment could not be confirmed. Please contact support.'),
        severity: 'error',
      }));
    } finally {
      setPaying(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!booking) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">This invoice could not be found.</Alert>
      </Container>
    );
  }

  // Already settled — either paid before the work, or paid from this page a moment ago.
  const settled = paid || booking.paymentStatus === 'PAID';

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Card sx={{ borderRadius: 3 }}>
        <CardContent sx={{ p: 4 }}>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
            {settled ? 'Invoice paid' : 'Your invoice'}
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748b', mb: 3 }}>
            {booking.serviceName} — {booking.bookingCode}
          </Typography>

          {settled ? (
            <Box sx={{ textAlign: 'center', py: 3 }}>
              <CheckCircle sx={{ fontSize: 56, color: 'success.main', mb: 2 }} />
              <Typography variant="body1" sx={{ mb: 3 }}>
                Thank you — this booking is fully paid.
              </Typography>
              <Button variant="contained" onClick={() => navigate('/dashboard')} sx={{ borderRadius: 3 }}>
                Back to my bookings
              </Button>
            </Box>
          ) : (
            <>
              <Box sx={{ bgcolor: 'action.hover', borderRadius: 2, p: 2.5, mb: 3 }}>
                {booking.finalCost != null && (
                  <Row label="Work done" value={formatRupees(Number(booking.finalCost))} />
                )}
                {booking.platformFee != null && (
                  <Row label="Platform fee" value={formatRupees(Number(booking.platformFee))} />
                )}
                {booking.gstAmount != null && (
                  <Row label="GST" value={formatRupees(Number(booking.gstAmount))} />
                )}
                <Divider sx={{ my: 1.5 }} />
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>Amount due</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
                    {booking.totalAmount != null ? formatRupees(Number(booking.totalAmount)) : '—'}
                  </Typography>
                </Box>
              </Box>

              <Alert severity="info" sx={{ borderRadius: 2, mb: 3 }}>
                Pay by card, UPI, net banking or wallet — all handled on the next screen.
              </Alert>

              <Button
                fullWidth
                variant="contained"
                size="large"
                startIcon={paying ? <CircularProgress size={18} /> : <PaymentIcon />}
                disabled={paying || !booking.totalAmount}
                onClick={handlePay}
                sx={{ py: 1.5, borderRadius: 3 }}
              >
                {paying ? 'Opening payment…' : `Pay ${booking.totalAmount != null ? formatRupees(Number(booking.totalAmount)) : ''}`}
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </Container>
  );
};

const Row: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
    <Typography variant="body2" sx={{ color: '#64748b' }}>{label}</Typography>
    <Typography variant="body2" sx={{ fontWeight: 600 }}>{value}</Typography>
  </Box>
);

export default BookingInvoicePage;
