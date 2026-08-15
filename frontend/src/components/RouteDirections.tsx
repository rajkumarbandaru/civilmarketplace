import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { AltRoute, ExpandLess, ExpandMore, Flag, Navigation } from '@mui/icons-material';
import { RoadRoute, describeStep, formatDistance } from '../services/routingApi';

/** Collapsed to the next few turns; the full list is a tap away. */
const PREVIEW_STEPS = 3;

/**
 * The turn-by-turn half of the tracking screen.
 *
 * Distinct from the GPS panel above it on purpose: that one says where the worker *is*, this one
 * says how they *get there*. They come from different sources and fail independently — the route
 * can be unavailable while the marker keeps moving, and the marker can go stale while the route
 * stays perfectly valid — so they are never merged into one status.
 */
const RouteDirections: React.FC<{
  route: RoadRoute | null;
  loading: boolean;
  /** Straight-line distance from the tracking panel, for the contrast note. */
  directDistanceKm?: number | null;
  /** The worker sees instructions to follow; everyone else is reading the same route as context. */
  forWorker?: boolean;
}> = ({ route, loading, directDistanceKm, forWorker = false }) => {
  const [expanded, setExpanded] = useState(false);

  if (loading) {
    return (
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mt: 2 }}>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          Working out the road route…
        </Typography>
      </Paper>
    );
  }

  if (!route) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        Road directions are unavailable right now. The position and straight-line distance above are
        still live.
      </Alert>
    );
  }

  const steps = expanded ? route.steps : route.steps.slice(0, PREVIEW_STEPS);
  // Worth showing when the road route is meaningfully longer than the crow-flies figure, because
  // that gap is exactly why the two numbers disagree and why the first one looked optimistic.
  const detour =
    directDistanceKm != null && directDistanceKm > 0
      ? Math.round((route.distanceKm / directDistanceKm) * 10) / 10
      : null;

  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mt: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }} flexWrap="wrap">
        <Navigation fontSize="small" color="primary" />
        <Typography variant="subtitle2" sx={{ fontWeight: 700, flexGrow: 1 }}>
          {forWorker ? 'Directions to the customer' : 'Road route'}
        </Typography>
        <Chip size="small" icon={<AltRoute />} label={`${route.distanceKm} km by road`} />
        <Chip size="small" color="primary" label={`${route.durationMinutes} min`} />
      </Stack>

      {detour !== null && detour >= 1.3 && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
          The road route is {detour}× the straight-line distance ({directDistanceKm} km) — the
          direct figure above is not a driving distance.
        </Typography>
      )}

      <Divider sx={{ my: 1 }} />

      <List dense disablePadding>
        {steps.map((step, index) => (
          <ListItem key={`${index}-${step.road}`} disableGutters sx={{ alignItems: 'flex-start' }}>
            <ListItemText
              primary={
                <Typography variant="body2" sx={{ fontWeight: index === 0 ? 600 : 400 }}>
                  {describeStep(step)}
                </Typography>
              }
              secondary={
                step.distanceMeters > 0 ? formatDistance(step.distanceMeters) : undefined
              }
            />
          </ListItem>
        ))}
      </List>

      {route.steps.length > PREVIEW_STEPS && (
        <Button
          size="small"
          onClick={() => setExpanded((value) => !value)}
          endIcon={expanded ? <ExpandLess /> : <ExpandMore />}
          sx={{ textTransform: 'none', mt: 0.5 }}
        >
          {expanded ? 'Show fewer' : `All ${route.steps.length} steps`}
        </Button>
      )}

      <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <Flag fontSize="inherit" sx={{ color: 'text.disabled' }} />
        <Typography variant="caption" sx={{ color: 'text.disabled' }}>
          Route by OSRM. Live traffic is not accounted for.
        </Typography>
      </Box>
    </Paper>
  );
};

export default RouteDirections;
