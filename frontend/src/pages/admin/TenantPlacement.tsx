import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,
  MenuItem, Stack, Step, StepLabel, Stepper, TextField, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  MOVE_RUNNING, MoveStep, Tenant, TenantMove, dropMoveSource, fetchClusters, fetchMoves, fetchPlacement, rollbackMove,
  startMove,
} from '../../services/tenantApi';

const STEPS: { step: MoveStep; label: string }[] = [
  { step: 'COPY', label: 'Copy' },
  { step: 'FREEZE', label: 'Pause writes' },
  { step: 'SYNC', label: 'Catch up' },
  { step: 'VERIFY', label: 'Verify' },
  { step: 'FLIP', label: 'Switch' },
  { step: 'AWAIT_ACKS', label: 'Services switched' },
  { step: 'RESUME', label: 'Resume' },
];
const indexOf = (m: TenantMove) => (m.step === 'DONE' ? STEPS.length : Math.max(0, STEPS.findIndex((s) => s.step === m.step)));

/** One move: where it is, what it copied, how long writes were paused, what it is waiting for. */
const MoveProgress: React.FC<{ move: TenantMove; tenantKey: string; onChanged: () => void; currentCluster: string }> = ({
  move, tenantKey, onChanged, currentCluster,
}) => {
  const { formatDateTime } = useDateTime();
  const [confirmDrop, setConfirmDrop] = useState(false);
  const rollback = useMutation({ mutationFn: () => rollbackMove(tenantKey, move.id), onSuccess: onChanged });
  const drop = useMutation({ mutationFn: () => dropMoveSource(tenantKey, move.id), onSuccess: () => { setConfirmDrop(false); onChanged(); } });
  const running = MOVE_RUNNING.includes(move.step);
  const flipped = move.step === 'FAILED' && (move.acknowledged.length > 0 || move.lastError?.startsWith('AWAIT_ACKS')
    || move.lastError?.startsWith('Not routing'));
  return (
    <Box data-testid={`move-${move.id}`} sx={{ mt: 2 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography sx={{ fontWeight: 600 }}>{move.sourceClusterId} → {move.targetClusterId}</Typography>
        <Chip size="small" label={move.step.toLowerCase().replace('_', ' ')}
          color={move.step === 'DONE' ? 'success' : move.step === 'FAILED' ? 'error' : running ? 'info' : 'default'} />
        <Typography variant="caption" color="text.secondary">{formatDateTime(move.startedAt)}</Typography>
      </Stack>
      {(running || move.step === 'DONE') && (
        <Stepper activeStep={indexOf(move)} alternativeLabel sx={{ mb: 1 }}>
          {STEPS.map((s) => <Step key={s.step}><StepLabel>{s.label}</StepLabel></Step>)}
        </Stepper>
      )}
      <Typography variant="body2" color="text.secondary" data-testid={`move-stats-${move.id}`}>
        {move.schemasCopied} schemas · {move.tablesCopied} tables · {move.rowsCopied.toLocaleString()} rows copied
        {move.tablesResynced > 0 && ` · ${move.tablesResynced} re-copied after the pause`}
        {move.report?.tablesVerified != null && ` · ${move.report.tablesVerified} tables verified`}
        {move.freezeMillis != null && ` · writes paused ${(move.freezeMillis / 1000).toFixed(1)} s`}
      </Typography>
      {move.waitingFor.length > 0 && (
        <Typography variant="body2" sx={{ mt: 0.5 }}>Waiting for: {move.waitingFor.join(', ')}</Typography>
      )}
      {move.lastError && <Alert severity="error" sx={{ mt: 1 }}>{move.lastError}</Alert>}
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        {flipped && (
          <Button size="small" color="warning" variant="outlined" disabled={rollback.isPending} onClick={() => rollback.mutate()}>
            Roll back to {move.sourceClusterId}
          </Button>
        )}
        {move.step === 'DONE' && !move.sourceDroppedAt && currentCluster === move.targetClusterId && (
          <Button size="small" color="error" onClick={() => setConfirmDrop(true)}>Drop old copy on {move.sourceClusterId}</Button>
        )}
        {move.sourceDroppedAt && <Chip size="small" variant="outlined" label={`Old copy dropped ${formatDateTime(move.sourceDroppedAt)}`} />}
      </Stack>
      {(rollback.isError || drop.isError) && (
        <Alert severity="error" sx={{ mt: 1 }}>{apiErrorMessage(rollback.error || drop.error, 'That did not work.')}</Alert>
      )}
      <Dialog open={confirmDrop} onClose={() => setConfirmDrop(false)}>
        <DialogTitle>Drop the old copy?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            The tenant's schemas on {move.sourceClusterId} are no longer used — its data is on {move.targetClusterId}. Dropping them
            cannot be undone; keep them until you are satisfied with the move.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setConfirmDrop(false)}>Keep</Button>
          <Button color="error" variant="contained" disabled={drop.isPending} onClick={() => drop.mutate()}>Drop</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

/**
 * Which MySQL cluster a tenant's data is on, and moving it to another: copied while live, then
 * a few seconds read-only while the last changes are caught up, verified and switched.
 */
const PlacementCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [target, setTarget] = useState('');
  const [confirming, setConfirming] = useState(false);
  const placement = useQuery({
    queryKey: ['placement', tenant.tenantKey],
    queryFn: () => fetchPlacement(tenant.tenantKey),
    retry: false,
    refetchInterval: (q) => (q.state.data?.currentMove && MOVE_RUNNING.includes(q.state.data.currentMove.step) ? 1500 : false),
  });
  const clusters = useQuery({ queryKey: ['clusters'], queryFn: fetchClusters, retry: false });
  const history = useQuery({ queryKey: ['moves', tenant.tenantKey], queryFn: () => fetchMoves(tenant.tenantKey), retry: false });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['placement', tenant.tenantKey] });
    queryClient.invalidateQueries({ queryKey: ['moves', tenant.tenantKey] });
    queryClient.invalidateQueries({ queryKey: ['clusters'] });
    queryClient.invalidateQueries({ queryKey: ['tenants'] });
  };
  const move = useMutation({
    mutationFn: () => startMove(tenant.tenantKey, target),
    onSuccess: () => { setConfirming(false); setTarget(''); refresh(); },
  });

  if (tenant.tenantKey === 'platform' || !placement.data) return null;
  const p = placement.data;
  const current = p.currentMove;
  const busy = current != null && MOVE_RUNNING.includes(current.step);
  const choices = (clusters.data ?? []).filter((c) => c.clusterId !== p.clusterId && c.status === 'ACTIVE');
  const older = (history.data ?? []).filter((m) => m.id !== current?.id);

  return (
    <Card sx={{ mb: 3 }} data-testid="placement-card">
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="h6">Data placement</Typography>
          <Chip label={p.clusterId} color="primary" data-testid="placement-cluster" />
          <Chip size="small" variant="outlined" label={p.tier === 'DEDICATED_DB' ? 'Dedicated database' : 'Shared cluster'} />
          <Chip size="small" variant="outlined" label={p.cell} />
          {p.status === 'MAINTENANCE' && <Chip size="small" color="warning" label="Writes paused" />}
        </Stack>
        {!busy && p.status === 'ACTIVE' && choices.length > 0 && (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
            <TextField select size="small" label="Move to" value={target} onChange={(e) => setTarget(e.target.value)} sx={{ minWidth: 260 }}>
              {choices.map((c) => (
                <MenuItem key={c.clusterId} value={c.clusterId}
                  disabled={(c.kind === 'DEDICATED' && c.tenants > 0) || c.tenants >= c.capacity}>
                  {c.clusterId} · {c.kind === 'DEDICATED' ? 'dedicated' : 'shared'} · {c.tenants}/{c.capacity} tenants
                </MenuItem>
              ))}
            </TextField>
            <Button variant="outlined" disabled={!target} onClick={() => setConfirming(true)}>Move</Button>
          </Stack>
        )}
        {current && <MoveProgress move={current} tenantKey={tenant.tenantKey} onChanged={refresh} currentCluster={p.clusterId} />}
        {older.length > 0 && (
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 2 }}>
            Earlier: {older.map((m) => `${m.sourceClusterId}→${m.targetClusterId} (${m.step.toLowerCase()})`).join(', ')}
          </Typography>
        )}
        {move.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(move.error, 'The move could not start.')}</Alert>}
      </CardContent>
      <Dialog open={confirming} onClose={() => setConfirming(false)}>
        <DialogTitle>Move {tenant.name} to {target}?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Its data is copied while it keeps working. Then, for a few seconds, it is read-only while the last changes are caught
            up and every service switches over — people can still browse, but saving waits. The old copy is kept on {p.clusterId}
            until you drop it.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setConfirming(false)}>Cancel</Button>
          <Button variant="contained" disabled={move.isPending} onClick={() => move.mutate()}>Start move</Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

export default PlacementCard;
