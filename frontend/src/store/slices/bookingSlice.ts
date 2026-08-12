import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../services/api';

interface Booking {
  id: number;
  bookingCode: string;
  customerId: number;
  workerId: number | null;
  serviceCategory: string;
  serviceName: string;
  bookingType: string;
  status: string;
  description: string;
  city: string;
  scheduledDate: string;
  estimatedCost: number;
  finalCost: number;
  totalAmount: number;
  paymentStatus: string;
  isEmergency: boolean;
  createdAt: string;
}

interface BookingState {
  bookings: Booking[];
  currentBooking: Booking | null;
  loading: boolean;
  error: string | null;
  totalPages: number;
}

const initialState: BookingState = {
  bookings: [],
  currentBooking: null,
  loading: false,
  error: null,
  totalPages: 0,
};

export const createBooking = createAsyncThunk(
  'booking/create',
  async (bookingData: any, { rejectWithValue }) => {
    try {
      const response = await api.post('/bookings', bookingData);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to create booking');
    }
  }
);

export const fetchCustomerBookings = createAsyncThunk(
  'booking/fetchCustomer',
  async (params: { page?: number; size?: number }, { rejectWithValue }) => {
    try {
      const response = await api.get('/bookings/customer', { params });
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch bookings');
    }
  }
);

export const fetchBookingById = createAsyncThunk(
  'booking/fetchById',
  async (bookingId: number, { rejectWithValue }) => {
    try {
      const response = await api.get(`/bookings/${bookingId}`);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Booking not found');
    }
  }
);

const bookingSlice = createSlice({
  name: 'booking',
  initialState,
  reducers: {
    clearCurrentBooking(state) {
      state.currentBooking = null;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(createBooking.pending, (state) => { state.loading = true; });
    builder.addCase(createBooking.fulfilled, (state, action) => {
      state.loading = false;
      state.bookings.unshift(action.payload);
      state.currentBooking = action.payload;
    });
    builder.addCase(createBooking.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    builder.addCase(fetchCustomerBookings.pending, (state) => { state.loading = true; });
    builder.addCase(fetchCustomerBookings.fulfilled, (state, action) => {
      state.loading = false;
      state.bookings = action.payload.content || action.payload;
      state.totalPages = action.payload.totalPages || 0;
    });
    builder.addCase(fetchCustomerBookings.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    builder.addCase(fetchBookingById.fulfilled, (state, action) => {
      state.currentBooking = action.payload;
    });
  },
});

export const { clearCurrentBooking } = bookingSlice.actions;
export default bookingSlice.reducer;
