import React, { useMemo } from 'react';
import { Box, LinearProgress, Typography } from '@mui/material';

export interface PasswordStrength {
  /** 0 (empty) to 4 (strong). */
  score: number;
  label: string;
  color: 'error' | 'warning' | 'info' | 'success';
  /** The single most useful thing the user could do next. */
  hint?: string;
}

const RULES: Array<{ test: (p: string) => boolean; hint: string }> = [
  { test: (p) => p.length >= 8, hint: 'Use at least 8 characters' },
  { test: (p) => /[a-z]/.test(p) && /[A-Z]/.test(p), hint: 'Mix upper and lower case' },
  { test: (p) => /\d/.test(p), hint: 'Add a number' },
  { test: (p) => /[^A-Za-z0-9]/.test(p), hint: 'Add a special character' },
];

/**
 * Scores a password against the same rules the signup schema enforces, plus a
 * length bonus, so the meter and the validation message never disagree.
 */
export const scorePassword = (password: string): PasswordStrength => {
  if (!password) {
    return { score: 0, label: 'Enter a password', color: 'error' };
  }

  const failed = RULES.filter((r) => !r.test(password));
  let score = RULES.length - failed.length;

  // A long passphrase is genuinely strong even without symbol gymnastics.
  if (password.length >= 14 && score >= 2) score = Math.min(4, score + 1);
  // Anything trivially short can never read as more than weak.
  if (password.length < 8) score = Math.min(score, 1);

  const meta: Record<number, { label: string; color: PasswordStrength['color'] }> = {
    0: { label: 'Very weak', color: 'error' },
    1: { label: 'Weak', color: 'error' },
    2: { label: 'Fair', color: 'warning' },
    3: { label: 'Good', color: 'info' },
    4: { label: 'Strong', color: 'success' },
  };

  return { score, ...meta[score], hint: failed[0]?.hint };
};

const PasswordStrengthMeter: React.FC<{ password: string }> = ({ password }) => {
  const strength = useMemo(() => scorePassword(password), [password]);

  if (!password) return null;

  return (
    <Box sx={{ mt: -1, mb: 2 }}>
      <LinearProgress
        variant="determinate"
        value={(strength.score / 4) * 100}
        color={strength.color}
        sx={{ height: 6, borderRadius: 3 }}
      />
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5, gap: 2 }}>
        <Typography variant="caption" color={`${strength.color}.main`} sx={{ fontWeight: 600 }}>
          {strength.label}
        </Typography>
        {strength.hint && (
          <Typography variant="caption" sx={{ color: '#64748b', textAlign: 'right' }}>
            {strength.hint}
          </Typography>
        )}
      </Box>
    </Box>
  );
};

export default PasswordStrengthMeter;
