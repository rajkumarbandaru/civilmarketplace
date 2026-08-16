import api from './api';

/**
 * Ratings for a completed booking.
 *
 * The reviewee is not sent: review-service resolves the counterparty from the booking itself, so a
 * customer cannot aim a one-star review at somebody who was never on the job.
 */

export interface SubmitReviewRequest {
  rating: number;
  comment?: string;
  categoryTags?: string;
}

export interface Review {
  id: number;
  bookingId: number;
  reviewerId: number;
  revieweeId: number;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export const submitReview = async (
  bookingId: number,
  request: SubmitReviewRequest
): Promise<Review> => {
  const { data } = await api.post<Review>(`/bookings/${bookingId}/reviews`, request);
  return data;
};
