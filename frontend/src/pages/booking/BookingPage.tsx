import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Container,
  Grid,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Chip,
  Stepper,
  Step,
  StepLabel,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
  Alert,
  CircularProgress,
  Avatar,
} from '@mui/material';
import {
  ArrowBack,
  CalendarToday,
  LocationOn,
  Payment,
  CheckCircle,
  Engineering,
} from '@mui/icons-material';
import { motion } from 'framer-motion';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { createBooking } from '../../store/slices/bookingSlice';
import { showSnackbar } from '../../store/slices/uiSlice';
import { payWithRazorpay } from '../../services/razorpayCheckout';
import { apiErrorMessage } from '../../services/apiError';

const steps = ['Service Details', 'Location', 'Schedule', 'Confirm & Pay'];

const schema = yup.object({
  serviceCategory: yup.string().required('Select a category'),
  serviceName: yup.string().required('Service name is required'),
  description: yup.string().max(2000, 'Max 2000 characters'),
  city: yup.string().required('City is required'),
  addressLine: yup.string().required('Address is required'),
  scheduledDate: yup.string(),
  bookingType: yup.string().required('Booking type is required'),
});

const BookingPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { loading } = useAppSelector((state) => state.booking);
  const { user } = useAppSelector((state) => state.auth);
  const [activeStep, setActiveStep] = useState(0);
  const [bookingType, setBookingType] = useState('INSTANT');
  const [selectedDate, setSelectedDate] = useState('');
  const [paying, setPaying] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: yupResolver(schema) });

  const onSubmit = async (data: any) => {
    if (activeStep < 3) {
      setActiveStep((prev) => prev + 1);
      return;
    }

    const result = await dispatch(createBooking({
      ...data,
      bookingType,
      scheduledDate: selectedDate || undefined,
    }));

    if (result.meta.requestStatus === 'fulfilled') {
      dispatch(showSnackbar({ message: 'Booking created successfully!', severity: 'success' }));
      navigate('/dashboard');
    }
  };

  /**
   * Pays for the booking with Razorpay Checkout.
   *
   * The booking is created first and only then paid for: an order needs a bookingId to attach the
   * payment to, and taking money for a booking that failed to save would leave the customer with a
   * charge and nothing to show for it.
   */
  const handlePayWithRazorpay = handleSubmit(async (data: any) => {
    setPaying(true);
    try {
      const created = await dispatch(createBooking({
        ...data,
        bookingType,
        scheduledDate: selectedDate || undefined,
      }));

      if (created.meta.requestStatus !== 'fulfilled') {
        return; // createBooking already surfaced why.
      }

      const booking = created.payload as { id: number; totalAmount?: number };
      const outcome = await payWithRazorpay({
        bookingId: booking.id,
        amount: Number(booking.totalAmount ?? 0),
        customer: { name: user?.name, email: user?.email, contact: user?.phone },
      });

      if (outcome.status === 'paid') {
        dispatch(showSnackbar({ message: 'Payment successful!', severity: 'success' }));
        navigate('/dashboard');
      } else if (outcome.status === 'cancelled') {
        // The booking is saved and unpaid, which is a state the customer can return to — so this
        // is information, not an error.
        dispatch(showSnackbar({
          message: 'Payment cancelled. Your booking is saved and can be paid from your dashboard.',
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
  });

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      {/* Back button */}
      <Button
        startIcon={<ArrowBack />}
        onClick={() => navigate(-1)}
        sx={{ mb: 3, color: '#64748b' }}
      >
        Back
      </Button>

      <Grid container spacing={4}>
        {/* Main Form */}
        <Grid item xs={12} md={8}>
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.4 }}
          >
            <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
              <Box sx={{ p: 3, borderBottom: '1px solid #e2e8f0' }}>
                <Stepper activeStep={activeStep} alternativeLabel>
                  {steps.map((label) => (
                    <Step key={label}>
                      <StepLabel>{label}</StepLabel>
                    </Step>
                  ))}
                </Stepper>
              </Box>

              <CardContent sx={{ p: 4 }}>
                <form onSubmit={handleSubmit(onSubmit)}>
                  {activeStep === 0 && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                        Service Details
                      </Typography>

                      <FormControl fullWidth sx={{ mb: 3 }}>
                        <InputLabel>Service Category</InputLabel>
                        <Select
                          label="Service Category"
                          {...register('serviceCategory')}
                          error={!!errors.serviceCategory}
                          defaultValue=""
                        >
                          <MenuItem value="House Planning">House Planning</MenuItem>
                          <MenuItem value="Architecture">Architecture</MenuItem>
                          <MenuItem value="Structural Engineering">Structural Engineering</MenuItem>
                          <MenuItem value="Survey">Survey Services</MenuItem>
                          <MenuItem value="Interior Design">Interior Design</MenuItem>
                          <MenuItem value="Construction">Construction</MenuItem>
                        </Select>
                      </FormControl>

                      <TextField
                        fullWidth
                        label="Service Name"
                        {...register('serviceName')}
                        error={!!errors.serviceName}
                        helperText={errors.serviceName?.message}
                        sx={{ mb: 3 }}
                      />

                      <TextField
                        fullWidth
                        label="Describe your project"
                        multiline
                        rows={4}
                        {...register('description')}
                        error={!!errors.description}
                        helperText={errors.description?.message}
                        sx={{ mb: 3 }}
                      />

                      <FormControl fullWidth sx={{ mb: 3 }}>
                        <InputLabel>Booking Type</InputLabel>
                        <Select
                          value={bookingType}
                          label="Booking Type"
                          onChange={(e) => setBookingType(e.target.value)}
                        >
                          <MenuItem value="INSTANT">Instant Booking</MenuItem>
                          <MenuItem value="SCHEDULED">Scheduled Booking</MenuItem>
                          <MenuItem value="EMERGENCY">Emergency Booking</MenuItem>
                          <MenuItem value="QUOTATION">Request Quotation</MenuItem>
                        </Select>
                      </FormControl>
                    </motion.div>
                  )}

                  {activeStep === 1 && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                        Location Details
                      </Typography>

                      <TextField
                        fullWidth
                        label="City"
                        {...register('city')}
                        error={!!errors.city}
                        helperText={errors.city?.message}
                        sx={{ mb: 3 }}
                        InputProps={{ startAdornment: <LocationOn sx={{ mr: 1, color: 'primary.main' }} /> }}
                      />

                      <TextField
                        fullWidth
                        label="Address"
                        multiline
                        rows={3}
                        {...register('addressLine')}
                        error={!!errors.addressLine}
                        helperText={errors.addressLine?.message}
                        sx={{ mb: 3 }}
                      />

                      <Grid container spacing={2}>
                        <Grid item xs={6}>
                          <TextField fullWidth label="Landmark" sx={{ mb: 2 }} />
                        </Grid>
                        <Grid item xs={6}>
                          <TextField fullWidth label="Pincode" sx={{ mb: 2 }} />
                        </Grid>
                      </Grid>
                    </motion.div>
                  )}

                  {activeStep === 2 && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                        Schedule
                      </Typography>

                      <TextField
                        fullWidth
                        label="Preferred Date & Time"
                        type="datetime-local"
                        value={selectedDate}
                        onChange={(e) => setSelectedDate(e.target.value)}
                        InputLabelProps={{ shrink: true }}
                        sx={{ mb: 3 }}
                        InputProps={{ startAdornment: <CalendarToday sx={{ mr: 1, color: 'primary.main' }} /> }}
                      />

                      <Typography variant="body2" sx={{ color: '#64748b', mb: 2 }}>
                        Estimated Duration: 2-3 hours
                      </Typography>

                      <Alert severity="info" sx={{ borderRadius: 2 }}>
                        You can also book instantly - we'll match you with an available professional right away.
                      </Alert>
                    </motion.div>
                  )}

                  {activeStep === 3 && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                        Confirm & Pay
                      </Typography>

                      <Box sx={{ bgcolor: 'action.hover', p: 3, borderRadius: 3, mb: 4 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>
                          Booking Summary
                        </Typography>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Service:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>House Planning</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Type:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{bookingType}</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Estimated Cost:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>₹500 - ₹2000</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Platform Fee:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>₹25 - ₹100</Typography>
                        </Box>
                        <Divider sx={{ my: 2 }} />
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography variant="h6" sx={{ fontWeight: 700 }}>Total</Typography>
                          <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
                            ₹525 - ₹2,100
                          </Typography>
                        </Box>
                      </Box>

                      <Box sx={{ display: 'flex', gap: 2 }}>
                        <Button
                          variant="outlined"
                          startIcon={paying ? <CircularProgress size={18} /> : <Payment />}
                          fullWidth
                          disabled={paying || loading}
                          onClick={handlePayWithRazorpay}
                          sx={{ py: 1.5, borderRadius: 3 }}
                        >
                          {paying ? 'Opening Razorpay…' : 'Pay with Razorpay'}
                        </Button>
                      </Box>
                    </motion.div>
                  )}

                  {/* Navigation buttons */}
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
                    <Button
                      onClick={() => setActiveStep((prev) => Math.max(0, prev - 1))}
                      disabled={activeStep === 0}
                      sx={{ color: '#64748b' }}
                    >
                      Previous
                    </Button>
                    <Button
                      type="submit"
                      variant="contained"
                      disabled={loading}
                      endIcon={activeStep === 3 ? <CheckCircle /> : null}
                      sx={{ px: 5, borderRadius: 3 }}
                    >
                      {loading ? <CircularProgress size={24} sx={{ color: '#fff' }} /> :
                       activeStep === 3 ? 'Confirm Booking' : 'Continue'}
                    </Button>
                  </Box>
                </form>
              </CardContent>
            </Card>
          </motion.div>
        </Grid>

        {/* Sidebar */}
        <Grid item xs={12} md={4}>
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.4, delay: 0.2 }}
          >
            <Card sx={{ borderRadius: 3, position: 'sticky', top: 80 }}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
                  <Avatar sx={{ background: (t) => `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})`, width: 48, height: 48 }}>
                    <Engineering />
                  </Avatar>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Need Help?
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#64748b' }}>
                      Our team is here 24/7
                    </Typography>
                  </Box>
                </Box>

                <Typography variant="body2" sx={{ color: '#64748b', mb: 2 }}>
                  Have questions about the service or need assistance with booking?
                  Contact our support team.
                </Typography>

                <Button variant="outlined" fullWidth sx={{ mb: 2, borderRadius: 2 }}>
                  Chat with Support
                </Button>

                <Divider sx={{ my: 3 }} />

                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 2 }}>
                  Why book with us?
                </Typography>
                {['Verified professionals', 'Secure payments', 'Free cancellation', 'Real-time tracking'].map((feature) => (
                  <Box key={feature} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <CheckCircle sx={{ color: '#10b981', fontSize: 18 }} />
                    <Typography variant="body2" sx={{ color: '#64748b' }}>{feature}</Typography>
                  </Box>
                ))}
              </CardContent>
            </Card>
          </motion.div>
        </Grid>
      </Grid>
    </Container>
  );
};

export default BookingPage;
