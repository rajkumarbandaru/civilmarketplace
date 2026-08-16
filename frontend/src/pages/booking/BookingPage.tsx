import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
  Radio,
  RadioGroup,
  FormControlLabel,
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
import { openSupportChat, showSnackbar } from '../../store/slices/uiSlice';
import { serviceBySlug, slugify } from '../../constants/serviceCatalogue';
import { useCatalogue } from '../../hooks/useCatalogue';
import { payWithRazorpay } from '../../services/razorpayCheckout';
import { apiErrorMessage } from '../../services/apiError';
import {
  GST_PERCENT,
  PLATFORM_FEE_PERCENT,
  durationMinutes,
  formatRupees,
  parseRate,
  priceBreakdown,
  quantityLabel,
} from '../../utils/bookingPricing';
import DynamicIcon from '../../components/DynamicIcon';
import AddressSelect, { AddressValue } from '../../components/AddressSelect';
import {
  isScheduleAllowed,
  minScheduleDateTime,
  scheduleHint,
} from '../../utils/bookingSchedule';

const steps = ['Service Details', 'Location', 'Schedule', 'Confirm & Pay'];

const schema = yup.object({
  serviceCategory: yup.string().required('Select a category'),
  serviceName: yup.string().required('Service name is required'),
  description: yup.string().max(2000, 'Max 2000 characters'),
  // `city` is not here: it comes from the address picker, not a registered input, and a required
  // rule on a field react-hook-form never sees fails validation forever.
  addressLine: yup.string().required('Address is required'),
  scheduledDate: yup.string(),
  // `bookingType` is deliberately absent: it is held in component state and merged in at submit,
  // so it is never a form field. Requiring it here made the whole form fail validation on every
  // click — the value the resolver saw was always undefined — which silently disabled Continue,
  // Confirm Booking and Pay all at once.
});

/**
 * Which fields each step actually shows.
 *
 * Continue validates only these, because the resolver runs against the whole schema: on step 1
 * that meant City and Address (which render on step 2) failed every time, and their error
 * messages were painted onto a step the customer could not see — so the button appeared dead.
 */
const STEP_FIELDS: ('serviceCategory' | 'serviceName' | 'description' | 'addressLine')[][] = [
  ['serviceCategory', 'serviceName', 'description'],
  ['addressLine'],
  [],
  [],
];

const BookingPage: React.FC = () => {
  const navigate = useNavigate();
  // `/book/:serviceId` carries the catalogue slug. The page previously ignored it entirely — every
  // card linked to `/book/1` — so whichever service you clicked, you landed on a blank form and had
  // to type its name back in. Unknown slugs resolve to undefined and the form stays blank, which is
  // the old behaviour rather than an error page.
  const { serviceId } = useParams<{ serviceId?: string }>();
  const { services, categories } = useCatalogue();
  const service = serviceBySlug(serviceId, services);
  const dispatch = useAppDispatch();
  const { loading } = useAppSelector((state) => state.booking);
  const { user } = useAppSelector((state) => state.auth);
  const [activeStep, setActiveStep] = useState(0);
  const [bookingType, setBookingType] = useState('INSTANT');
  const [selectedDate, setSelectedDate] = useState('');
  const [paying, setPaying] = useState(false);
  const [quantity, setQuantity] = useState('1');
  /**
   * PREPAID — pay now, and the booking is only confirmed once payment succeeds.
   * POSTPAID — book now, work first, and an invoice with a pay link arrives when the job is done.
   */
  const [paymentPreference, setPaymentPreference] = useState<'PREPAID' | 'POSTPAID'>('PREPAID');
  const [address, setAddress] = useState<AddressValue>({ country: '', state: '', city: '' });
  const [pincode, setPincode] = useState('');
  const [landmark, setLandmark] = useState('');
  /** Set when Continue is pressed on the address step with no city chosen. */
  const [cityError, setCityError] = useState('');

  /**
   * What this booking costs, and therefore whether it can be paid for at all.
   *
   * A catalogue price is only sometimes a rate: 34 of the 116 entries are priced "Quote", and the
   * rest are per hour, day, sqft, bag, ton… A booking is payable upfront only when the price parses
   * into a rate and the customer has given a quantity — otherwise the amount is unknowable now, and
   * the booking has to go out as a quotation request instead of a payment.
   */
  const parsedRate = parseRate(service?.price);
  const quantityValue = Number(quantity);
  const hasQuantity = Number.isFinite(quantityValue) && quantityValue > 0;
  const estimate = parsedRate && hasQuantity ? priceBreakdown(parsedRate.rate, quantityValue) : null;
  // A quotation is a request for a price, so there is nothing to charge for it yet — and a
  // pay-later booking is deliberately confirmed without money, to be invoiced on completion.
  const payable =
    estimate !== null && bookingType !== 'QUOTATION' && paymentPreference === 'PREPAID';
  /** Whether the customer even gets the choice: with no price, pay-later is the only option. */
  const canChoosePayment = estimate !== null && bookingType !== 'QUOTATION';

  /**
   * A scheduled booking is for a future day, so today and everything before it is out of range.
   *
   * Same-day work is what Instant and Emergency are for — they dispatch against today rather than
   * holding a slot — so offering today here would create a "scheduled" booking that nobody is
   * organised to staff. Instant and Emergency keep now as their floor.
   */
  const scheduleFloor = minScheduleDateTime(bookingType);

  const {
    register,
    handleSubmit,
    trigger,
    formState: { errors },
  } = useForm({ resolver: yupResolver(schema) });

  /**
   * Advances one step, validating only what the current step shows.
   *
   * Separate from {@link onSubmit} so an incomplete later step cannot block an earlier one, and so
   * a failure lands on a field the customer is looking at.
   */
  const handleContinue = async () => {
    const valid = await trigger(STEP_FIELDS[activeStep]);
    // The address step's city comes from the picker rather than the form, so it is checked here.
    if (activeStep === 1 && !address.city.trim()) {
      setCityError('Choose a city');
      return;
    }
    setCityError('');
    // Same rule the input's `min` declares, enforced rather than suggested.
    if (activeStep === 2 && !isScheduleAllowed(bookingType, selectedDate)) {
      dispatch(showSnackbar({ message: scheduleHint(bookingType), severity: 'warning' }));
      return;
    }
    if (valid) setActiveStep((prev) => Math.min(3, prev + 1));
  };

  /**
   * The fields derived from the price, sent with every booking.
   *
   * Without `estimatedCost` the backend leaves `totalAmount` null — which is how the pay button
   * came to ask Razorpay for ₹0.
   */
  const pricingFields = () =>
    estimate && parsedRate
      ? {
          estimatedCost: estimate.subtotal,
          estimatedDurationMinutes: durationMinutes(parsedRate.unit, quantityValue),
        }
      : {};

  /** Pay-now or pay-later, as chosen on the last step. */
  const paymentFields = () => ({ paymentPreference });

  /**
   * The address, assembled from the picker and the free-text lines.
   *
   * `city` is sent as its own field because bookings are matched and reported on by city, and the
   * full address line is kept human-readable for whoever actually has to find the place.
   */
  const addressFields = (rawAddressLine: string) => ({
    city: address.city.trim(),
    addressLine: [
      rawAddressLine?.trim(),
      landmark.trim() && `Landmark: ${landmark.trim()}`,
      address.city.trim(),
      address.state,
      pincode.trim(),
      address.countryName,
    ]
      .filter(Boolean)
      .join(', '),
  });

  /**
   * Submits a booking that has no payable amount yet — a quotation request.
   *
   * Reachable only when {@link payable} is false, which {@link handleFormSubmit} is what enforces.
   * A priced booking cannot take this path: it used to sit behind the step's primary button, so the
   * obvious thing to click created the booking and skipped payment entirely.
   */
  const onSubmit = async (data: any) => {
    const result = await dispatch(createBooking({
      ...data,
      ...pricingFields(),
      ...paymentFields(),
      // After ...data so the assembled address wins over the raw form field.
      ...addressFields(data.addressLine),
      bookingType,
      scheduledDate: selectedDate || undefined,
    }));

    if (result.meta.requestStatus === 'fulfilled') {
      dispatch(showSnackbar({
        // The two pay-free paths mean different things, and telling a pay-later customer their
        // price is still coming — or a quote customer that they owe money — is how a booking ends
        // in a support ticket.
        message: paymentPreference === 'POSTPAID' && canChoosePayment
          ? 'Booked. You will receive an invoice to pay once the work is complete.'
          : 'Request sent. A professional will send you a quote — nothing is charged until you accept it.',
        severity: 'success',
      }));
      navigate('/dashboard');
    }
  };

  /**
   * The only route from a native form submission to {@link onSubmit}.
   *
   * Placing a booking is always a deliberate click, never an implicit submit. Enter submits a form
   * by itself whenever nothing on screen blocks it, and on "Confirm & Pay" nothing does: the step
   * holds a radio group (radios do not block implicit submission) and, once the booking is payable,
   * no submit button at all. One Enter keypress there — the natural thing to press after choosing a
   * payment option by keyboard — created the booking and navigated to the dashboard, so the
   * customer left with an order placed and Razorpay never opened.
   *
   * Earlier steps are gated for the same reason: Enter on the Schedule step submitted the whole
   * form and skipped the payment step outright.
   */
  const handleFormSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (activeStep !== 3 || payable) return;
    void handleSubmit(onSubmit)(event);
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
        ...pricingFields(),
        ...paymentFields(),
        ...addressFields(data.addressLine),
        bookingType,
        scheduledDate: selectedDate || undefined,
      }));

      if (created.meta.requestStatus !== 'fulfilled') {
        return; // createBooking already surfaced why.
      }

      const booking = created.payload as { id: number; totalAmount?: number };
      // The server's own total, not the on-screen estimate — the estimate is a preview of this
      // number and must never be the one charged.
      const amount = Number(booking.totalAmount ?? 0);
      if (!(amount > 0)) {
        // Reaching Checkout with nothing to charge produced a ₹0 order that could never be paid,
        // leaving the booking stuck. Better to say so and leave it as a quotation.
        dispatch(showSnackbar({
          message: 'This booking has no payable amount yet — a professional will send you a quote.',
          severity: 'info',
        }));
        navigate('/dashboard');
        return;
      }

      const outcome = await payWithRazorpay({
        bookingId: booking.id,
        amount,
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
              {/* Names what is being booked. Without it the form is identical for all 116
                  catalogue entries, and nothing on screen confirms the right card was clicked. */}
              {service && (
                <Box
                  sx={{
                    px: 3,
                    py: 2,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    color: 'primary.contrastText',
                    background: (t) =>
                      `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})`,
                  }}
                >
                  <Avatar sx={{ bgcolor: 'rgba(255,255,255,0.22)' }}>
                    <DynamicIcon name={service.icon} />
                  </Avatar>
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                      {service.title}
                    </Typography>
                    <Typography variant="caption" sx={{ opacity: 0.9 }}>
                      {service.category} · {service.price}
                    </Typography>
                  </Box>
                  <Chip
                    size="small"
                    label="Change"
                    onClick={() => navigate(`/services/${slugify(service.category)}`)}
                    sx={{ bgcolor: 'rgba(255,255,255,0.22)', color: 'inherit', fontWeight: 600 }}
                  />
                </Box>
              )}
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
                <form onSubmit={handleFormSubmit}>
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
                          // Driven by the catalogue rather than a hand-written list, which had
                          // drifted to six options and could not express Materials, Equipment or
                          // Vehicles at all — a materials booking had no category to sit under.
                          defaultValue={service?.category ?? ''}
                        >
                          {categories.map((category) => (
                            <MenuItem key={category} value={category}>
                              {category}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>

                      <TextField
                        fullWidth
                        label="Service Name"
                        {...register('serviceName')}
                        error={!!errors.serviceName}
                        helperText={errors.serviceName?.message}
                        // Prefilled from the card that was clicked, and still editable — the
                        // catalogue title is a starting point, not the final scope of the job.
                        defaultValue={service?.title ?? ''}
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

                      {/* Picked rather than typed, so the same city is spelled the same way on
                          every booking — free text produced "hyd", "Hyd." and "Hyderabad" as three
                          different cities to every search and report. */}
                      <AddressSelect value={address} onChange={setAddress} cityError={cityError} />

                      <TextField
                        fullWidth
                        label="Address"
                        multiline
                        rows={3}
                        {...register('addressLine')}
                        error={!!errors.addressLine}
                        helperText={errors.addressLine?.message ?? 'House/flat number, street and area'}
                        sx={{ mb: 3, mt: 1 }}
                        InputProps={{ startAdornment: <LocationOn sx={{ mr: 1, mt: -3, color: 'primary.main' }} /> }}
                      />

                      <Grid container spacing={2}>
                        <Grid item xs={6}>
                          {/* Both were decorative before — unregistered, so whatever was typed here
                              never reached the booking. They are part of the address now. */}
                          <TextField
                            fullWidth label="Landmark" value={landmark}
                            onChange={(e) => setLandmark(e.target.value)} sx={{ mb: 2 }}
                          />
                        </Grid>
                        <Grid item xs={6}>
                          <TextField
                            fullWidth label="Pincode" value={pincode}
                            onChange={(e) => setPincode(e.target.value)} sx={{ mb: 2 }}
                          />
                        </Grid>
                      </Grid>
                    </motion.div>
                  )}

                  {activeStep === 2 && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
                        Schedule
                      </Typography>

                      {bookingType === 'SCHEDULED' && (
                        <Alert severity="info" sx={{ borderRadius: 2, mb: 2 }}>
                          Scheduled bookings start from tomorrow. Need someone today? Go back and
                          choose <strong>Instant</strong> or <strong>Emergency</strong> booking.
                        </Alert>
                      )}

                      <TextField
                        fullWidth
                        label="Preferred Date & Time"
                        type="datetime-local"
                        value={selectedDate}
                        onChange={(e) => setSelectedDate(e.target.value)}
                        InputLabelProps={{ shrink: true }}
                        // `min` greys out the disallowed days in the picker; the same rule is
                        // re-checked on Continue, because min is advisory and a pasted value
                        // ignores it entirely.
                        inputProps={{ min: scheduleFloor }}
                        error={!isScheduleAllowed(bookingType, selectedDate)}
                        helperText={
                          !isScheduleAllowed(bookingType, selectedDate)
                            ? scheduleHint(bookingType)
                            : scheduleHint(bookingType)
                        }
                        sx={{ mb: 3 }}
                        InputProps={{ startAdornment: <CalendarToday sx={{ mr: 1, color: 'primary.main' }} /> }}
                      />

                      {/* How much of the service is wanted. Without it there is no amount to
                          charge — the rate alone does not say how many hours, days or sq ft. */}
                      {parsedRate ? (
                        <TextField
                          fullWidth
                          type="number"
                          label={quantityLabel(parsedRate.unit)}
                          value={quantity}
                          onChange={(e) => setQuantity(e.target.value)}
                          inputProps={{ min: 1, step: parsedRate.unit === 'sqft' ? 10 : 1 }}
                          helperText={
                            estimate
                              ? `${formatRupees(parsedRate.rate)}/${parsedRate.unit} × ${quantityValue} = ${formatRupees(estimate.subtotal)}`
                              : 'Enter how much you need so we can price the job'
                          }
                          error={!hasQuantity}
                          sx={{ mb: 3 }}
                        />
                      ) : (
                        <Alert severity="info" sx={{ borderRadius: 2, mb: 3 }}>
                          This service is priced on request. Send the details and a professional
                          will quote you — nothing is charged now.
                        </Alert>
                      )}

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
                        {/* Every line here used to be a literal — "House Planning", "₹500 - ₹2000",
                            "₹525 - ₹2,100" — shown whichever of the 116 services was being booked,
                            so the summary named the wrong service at the wrong price. */}
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Service:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {service?.title ?? 'Selected service'}
                          </Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2" sx={{ color: '#64748b' }}>Type:</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{bookingType}</Typography>
                        </Box>

                        {estimate && parsedRate ? (
                          <>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                              <Typography variant="body2" sx={{ color: '#64748b' }}>
                                {formatRupees(parsedRate.rate)}/{parsedRate.unit} × {quantityValue}:
                              </Typography>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {formatRupees(estimate.subtotal)}
                              </Typography>
                            </Box>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                              <Typography variant="body2" sx={{ color: '#64748b' }}>
                                Platform Fee ({PLATFORM_FEE_PERCENT}%):
                              </Typography>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {formatRupees(estimate.platformFee)}
                              </Typography>
                            </Box>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                              <Typography variant="body2" sx={{ color: '#64748b' }}>
                                GST ({GST_PERCENT}%):
                              </Typography>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {formatRupees(estimate.gst)}
                              </Typography>
                            </Box>
                            <Divider sx={{ my: 2 }} />
                            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                              <Typography variant="h6" sx={{ fontWeight: 700 }}>Total</Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
                                {formatRupees(estimate.total)}
                              </Typography>
                            </Box>
                          </>
                        ) : (
                          <>
                            <Divider sx={{ my: 2 }} />
                            <Typography variant="body2" sx={{ color: '#64748b' }}>
                              {parsedRate
                                ? 'Enter a quantity on the Schedule step to see the price.'
                                : 'This service is priced on request — a professional will send you a quote.'}
                            </Typography>
                          </>
                        )}
                      </Box>

                      {/* When and how the customer pays. Offered only where an amount is known —
                          a "Quote" service has nothing to pay now and nothing to invoice later
                          until a professional has priced it. */}
                      {canChoosePayment && (
                        <Box sx={{ mb: 3 }}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
                            How would you like to pay?
                          </Typography>
                          <RadioGroup
                            value={paymentPreference}
                            onChange={(e) => setPaymentPreference(e.target.value as 'PREPAID' | 'POSTPAID')}
                          >
                            <Card
                              variant="outlined"
                              sx={{ mb: 1.5, p: 1.5, borderColor: paymentPreference === 'PREPAID' ? 'primary.main' : undefined }}
                            >
                              <FormControlLabel
                                value="PREPAID"
                                control={<Radio />}
                                sx={{ alignItems: 'flex-start', m: 0 }}
                                label={
                                  <Box>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      Pay now — {estimate ? formatRupees(estimate.total) : ''}
                                    </Typography>
                                    <Typography variant="caption" sx={{ color: '#64748b' }}>
                                      Your booking is confirmed as soon as the payment succeeds.
                                    </Typography>
                                  </Box>
                                }
                              />
                            </Card>
                            <Card
                              variant="outlined"
                              sx={{ p: 1.5, borderColor: paymentPreference === 'POSTPAID' ? 'primary.main' : undefined }}
                            >
                              <FormControlLabel
                                value="POSTPAID"
                                control={<Radio />}
                                sx={{ alignItems: 'flex-start', m: 0 }}
                                label={
                                  <Box>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      Pay later — after the work is done
                                    </Typography>
                                    <Typography variant="caption" sx={{ color: '#64748b' }}>
                                      We book it now and email you an invoice when the job is
                                      complete. Pay by card, UPI, net banking or wallet.
                                    </Typography>
                                  </Box>
                                }
                              />
                            </Card>
                          </RadioGroup>
                        </Box>
                      )}

                      {payable && (
                        <Alert severity="info" sx={{ borderRadius: 2, mb: 2 }}>
                          Your booking is confirmed once payment succeeds.
                        </Alert>
                      )}

                      {/* Paying *is* the confirmation for a priced booking. There used to be a
                          separate "Confirm Booking" primary button beside Previous which created
                          the booking outright, so the most obvious control on a step called
                          "Confirm & Pay" was the one that skipped paying. */}
                      {payable && estimate && (
                        <Box sx={{ display: 'flex', gap: 2 }}>
                          <Button
                            variant="contained"
                            startIcon={paying ? <CircularProgress size={18} /> : <Payment />}
                            fullWidth
                            disabled={paying || loading}
                            onClick={handlePayWithRazorpay}
                            sx={{ py: 1.5, borderRadius: 3 }}
                          >
                            {paying ? 'Opening Razorpay…' : `Pay ${formatRupees(estimate.total)} & Confirm`}
                          </Button>
                        </Box>
                      )}
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
                    {/* On the last step this submits only when there is nothing to pay — a
                        quotation request. A priced booking is completed by the Pay button above,
                        so there is no pay-free way to place one. */}
                    {!(activeStep === 3 && payable) && (
                      <Button
                        // Only the last step submits the booking; the earlier steps just advance,
                        // so a type="submit" on all four ran the full-schema validation every time.
                        type={activeStep === 3 ? 'submit' : 'button'}
                        onClick={activeStep === 3 ? undefined : handleContinue}
                        variant="contained"
                        disabled={loading || paying}
                        endIcon={activeStep === 3 ? <CheckCircle /> : null}
                        sx={{ px: 5, borderRadius: 3 }}
                      >
                        {loading ? <CircularProgress size={24} sx={{ color: '#fff' }} /> :
                         activeStep === 3
                           ? (canChoosePayment ? 'Confirm Booking — Pay Later' : 'Submit Request')
                           : 'Continue'}
                      </Button>
                    )}
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

                {/* Opens the assistant in the app shell rather than a second chat surface here. */}
                <Button
                  variant="outlined"
                  fullWidth
                  onClick={() => dispatch(openSupportChat())}
                  sx={{ mb: 2, borderRadius: 2 }}
                >
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
