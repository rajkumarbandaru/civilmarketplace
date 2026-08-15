import { createPaymentOrder, verifyPayment, Payment } from './paymentApi';

const CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Checkout's handler payload. Razorpay names these fields in snake_case; the backend expects
 * camelCase, so the mapping happens in one place here rather than at each call site.
 */
interface RazorpayHandlerResponse {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

interface RazorpayInstance {
  open: () => void;
  on: (event: string, handler: (payload: { error?: { description?: string } }) => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

/**
 * Loads checkout.js on first use rather than from index.html, so every page load does not pay for
 * a third-party script that only the booking flow needs. Resolves immediately once loaded.
 */
const loadCheckoutScript = (): Promise<void> =>
  new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${CHECKOUT_SCRIPT}"]`);
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('Could not load Razorpay Checkout.')));
      return;
    }
    const script = document.createElement('script');
    script.src = CHECKOUT_SCRIPT;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Razorpay Checkout.'));
    document.body.appendChild(script);
  });

export interface CheckoutOptions {
  bookingId: number;
  /** Rupees, not paise. */
  amount: number;
  /** Prefills the Checkout form; all optional. */
  customer?: { name?: string; email?: string; contact?: string };
  description?: string;
}

export type CheckoutOutcome =
  | { status: 'paid'; payment: Payment }
  | { status: 'cancelled' }
  | { status: 'failed'; message: string };

/**
 * Runs the full Standard Checkout flow: create an order, open the modal, verify the signature.
 *
 * Resolves rather than rejects for the two outcomes that are not faults — the customer closing the
 * modal, and Razorpay reporting a declined payment — so callers can tell "nothing happened" apart
 * from "something broke" without inspecting error strings.
 */
export const payWithRazorpay = async (options: CheckoutOptions): Promise<CheckoutOutcome> => {
  const keyId = import.meta.env.VITE_RAZORPAY_KEY_ID;
  if (!keyId) {
    return { status: 'failed', message: 'Payments are not configured. Please contact support.' };
  }

  await loadCheckoutScript();

  const order = await createPaymentOrder(options.bookingId, options.amount);
  // payment-service records a FAILED row instead of throwing when Razorpay is unreachable, so the
  // missing order id — not an exception — is what says the order was never created.
  if (!order.razorpayOrderId) {
    return {
      status: 'failed',
      message: order.failureReason || 'Could not start the payment. Please try again.',
    };
  }

  return new Promise<CheckoutOutcome>((resolve, reject) => {
    // Guards the dismiss handler: Razorpay fires ondismiss after a successful payment too, which
    // would otherwise report a completed payment as cancelled.
    let settled = false;
    const settle = (outcome: CheckoutOutcome) => {
      if (!settled) {
        settled = true;
        resolve(outcome);
      }
    };

    const razorpay = new window.Razorpay!({
      key: keyId,
      order_id: order.razorpayOrderId,
      // Read back from the order rather than the caller's argument, so the modal can never show a
      // different figure from the one the order was created for.
      amount: Math.round(Number(order.totalAmount) * 100),
      currency: order.currency || 'INR',
      name: 'Civil Engineering Marketplace',
      description: options.description || `Booking #${options.bookingId}`,
      prefill: {
        name: options.customer?.name || '',
        email: options.customer?.email || '',
        contact: options.customer?.contact || '',
      },
      handler: async (response: RazorpayHandlerResponse) => {
        try {
          const payment = await verifyPayment({
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          });
          settle({ status: 'paid', payment });
        } catch (error) {
          // The money may well have been taken — the signature check is what failed — so this is
          // surfaced as an error for the caller to escalate, never as a silent cancellation.
          settled = true;
          reject(error);
        }
      },
      modal: {
        ondismiss: () => settle({ status: 'cancelled' }),
      },
    });

    razorpay.on('payment.failed', (payload) => {
      settle({
        status: 'failed',
        message: payload.error?.description || 'The payment could not be completed.',
      });
    });

    razorpay.open();
  });
};
