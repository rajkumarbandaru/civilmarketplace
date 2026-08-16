import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Container, Rating, TextField,
  Typography,
} from '@mui/material';
import { CheckCircle, Star } from '@mui/icons-material';
import { useAppDispatch } from '../../hooks';
import { showSnackbar } from '../../store/slices/uiSlice';
import { apiErrorMessage } from '../../services/apiError';
import { submitReview } from '../../services/reviewApi';

/**
 * Where the "Rate this service" link in the completion email lands.
 *
 * One screen, one decision: the stars are the only required field, because a rating form that
 * demands a written review is a rating form most people close. The tags are one tap each and give
 * the useful detail that free text usually does not.
 */

/** Offered as quick taps; sent as the review's comma-separated categoryTags. */
const TAGS = [
  'On time',
  'Professional',
  'Good quality work',
  'Fair price',
  'Clean and tidy',
  'Clear communication',
];

const BookingReviewPage: React.FC = () => {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const [rating, setRating] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const toggleTag = (tag: string) =>
    setTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]));

  const handleSubmit = async () => {
    if (!rating || !bookingId) return;
    setSaving(true);
    try {
      await submitReview(Number(bookingId), {
        rating,
        comment: comment.trim() || undefined,
        categoryTags: tags.length ? tags.join(',') : undefined,
      });
      setDone(true);
    } catch (error) {
      // The commonest failure is a second review for the same booking, and the server says so —
      // which is more use than a generic "could not submit".
      dispatch(showSnackbar({
        message: apiErrorMessage(error, 'Could not submit your rating. Please try again.'),
        severity: 'error',
      }));
    } finally {
      setSaving(false);
    }
  };

  if (done) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Card sx={{ borderRadius: 3, textAlign: 'center', p: 4 }}>
          <CheckCircle sx={{ fontSize: 56, color: 'success.main', mb: 2 }} />
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
            Thank you for the feedback
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748b', mb: 3 }}>
            Your rating helps other customers choose, and helps good professionals get more work.
          </Typography>
          <Button variant="contained" onClick={() => navigate('/dashboard')} sx={{ borderRadius: 3 }}>
            Back to my bookings
          </Button>
        </Card>
      </Container>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Card sx={{ borderRadius: 3 }}>
        <CardContent sx={{ p: 4 }}>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
            How did we do?
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748b', mb: 3 }}>
            Booking #{bookingId} — it takes about a minute.
          </Typography>

          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Rating
              value={rating}
              onChange={(_, value) => setRating(value)}
              size="large"
              icon={<Star fontSize="inherit" />}
              sx={{ fontSize: '3rem' }}
            />
            <Typography variant="body2" sx={{ color: '#64748b', mt: 1 }}>
              {rating ? `${rating} out of 5` : 'Tap a star to rate'}
            </Typography>
          </Box>

          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
            What stood out? (optional)
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 3 }}>
            {TAGS.map((tag) => (
              <Chip
                key={tag}
                label={tag}
                onClick={() => toggleTag(tag)}
                color={tags.includes(tag) ? 'primary' : 'default'}
                variant={tags.includes(tag) ? 'filled' : 'outlined'}
              />
            ))}
          </Box>

          <TextField
            fullWidth
            multiline
            rows={4}
            label="Anything else you'd like to add? (optional)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            sx={{ mb: 3 }}
          />

          {!rating && (
            <Alert severity="info" sx={{ mb: 2, borderRadius: 2 }}>
              Choose a star rating to submit.
            </Alert>
          )}

          <Button
            fullWidth
            variant="contained"
            size="large"
            disabled={!rating || saving}
            onClick={handleSubmit}
            sx={{ py: 1.5, borderRadius: 3 }}
          >
            {saving ? <CircularProgress size={22} sx={{ color: '#fff' }} /> : 'Submit rating'}
          </Button>
        </CardContent>
      </Card>
    </Container>
  );
};

export default BookingReviewPage;
