import api from './api';

/** Payment row as payment-service returns it. Nulls mean "not reached that stage yet". */
export interface Payment {
  id: number;
  paymentCode: string;
  bookingId: number;
  amount: number;
  totalAmount: number;
  currency: string;
  paymentStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  failureReason: string | null;
}

export interface VerifyPaymentCommand {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

/**
 * Creates the Razorpay order to open Checkout against. The amount is in rupees — payment-service
 * converts to paise, so sending paise here would overcharge by 100x.
 */
export const createPaymentOrder = async (
  bookingId: number,
  amount: number
): Promise<Payment> => {
  const { data } = await api.post<Payment>('/payments/create-order', { bookingId, amount });
  return data;
};

/**
 * Hands the Checkout handler's three fields back for signature verification. Only the server can
 * do this — it needs the key secret — so a payment is not settled until this resolves.
 */
export const verifyPayment = async (command: VerifyPaymentCommand): Promise<Payment> => {
  const { data } = await api.post<Payment>('/payments/verify', command);
  return data;
};
