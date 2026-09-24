import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, CircularProgress, Dialog,
  DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { ExpandMore, History } from '@mui/icons-material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  ConfigRelease, SOURCE_LABELS, fetchReleaseDiff, fetchReleases, rollbackRelease, settingLabel,
} from '../../services/configApi';

const HEX = /^#[0-9a-fA-F]{6,8}$/;

/** A value in the diff: colours get a swatch, absent reads as "inherited". */
const Value: React.FC<{ value: unknown }> = ({ value }) => {
  if (value === null || value === undefined) {
    return <Typography variant="body2" color="text.secondary" component="span">inherited</Typography>;
  }
  const text = String(value);
  return (
    <Stack direction="row" spacing={0.75} alignItems="center" component="span">
      {HEX.test(text) && (
        <Box component="span" sx={{ width: 14, height: 14, borderRadius: '4px', bgcolor: text,
          border: 1, borderColor: 'divider', flexShrink: 0 }} />
      )}
      <Typography variant="body2" component="span" sx={{ wordBreak: 'break-all' }}>{text}</Typography>
    </Stack>
  );
};

const ReleaseChanges: React.FC<{ releaseId: number }> = ({ releaseId }) => {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['config-diff', releaseId],
    queryFn: () => fetchReleaseDiff(releaseId),
  });
  if (isLoading) return <CircularProgress size={20} />;
  if (isError) return <Alert severity="error">{apiErrorMessage(error, 'Could not load the changes.')}</Alert>;
  if (!data?.changes.length) return <Typography variant="body2" color="text.secondary">No setting changed.</Typography>;
  return (
    <Table size="small" data-testid={`release-diff-${releaseId}`}>
      <TableHead>
        <TableRow><TableCell>Setting</TableCell><TableCell>Before</TableCell><TableCell>After</TableCell></TableRow>
      </TableHead>
      <TableBody>
        {data.changes.map((c) => (
          <TableRow key={`${c.document}.${c.key}`}>
            <TableCell>{settingLabel(c.key)}</TableCell>
            <TableCell><Value value={c.before} /></TableCell>
            <TableCell><Value value={c.after} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};

/**
 * The published history of one scope's theme, newest first. Each entry shows what it changed; any
 * entry but the live one can be made live again, which publishes its content as a new release.
 */
const ThemeHistory: React.FC<{ scope: string; onRolledBack: () => void }> = ({ scope, onRolledBack }) => {
  const { formatDateTime } = useDateTime();
  const [target, setTarget] = useState<ConfigRelease | null>(null);
  const [reason, setReason] = useState('');
  const releases = useQuery({ queryKey: ['config-releases', scope], queryFn: () => fetchReleases(scope) });

  const rollback = useMutation({
    mutationFn: () => rollbackRelease(target!.id, reason.trim()),
    onSuccess: () => {
      setTarget(null);
      setReason('');
      releases.refetch();
      onRolledBack();
    },
  });

  return (
    <Box sx={{ mt: 4 }} data-testid="theme-history">
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <History color="action" />
        <Typography variant="h6">Version history</Typography>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Every save is kept. Rolling back publishes an earlier version again as a new entry, so
        nothing is lost and the change can itself be undone.
      </Typography>

      {releases.isLoading && <CircularProgress size={24} />}
      {releases.isError && (
        <Alert severity="error">{apiErrorMessage(releases.error, 'Could not load the history.')}</Alert>
      )}
      {releases.data?.length === 0 && (
        <Typography variant="body2" color="text.secondary">Nothing has been published for this scope yet.</Typography>
      )}

      {releases.data?.map((r) => (
        <Accordion key={r.id} disableGutters TransitionProps={{ unmountOnExit: true }} data-testid={`release-${r.id}`}>
          <AccordionSummary expandIcon={<ExpandMore />}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5, pr: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>#{r.id}</Typography>
              <Typography variant="body2">{formatDateTime(r.createdAt)}</Typography>
              <Chip size="small" label={SOURCE_LABELS[r.source] ?? r.source} variant="outlined" />
              {r.live && <Chip size="small" color="success" label="Live" />}
              <Typography variant="body2" color="text.secondary">{r.documents.join(', ')}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            {r.changeNote && <Typography variant="body2" sx={{ mb: 1 }}>{r.changeNote}</Typography>}
            <ReleaseChanges releaseId={r.id} />
            {!r.live && (
              <Button size="small" variant="outlined" sx={{ mt: 2 }} onClick={() => setTarget(r)}>
                Roll back to this version
              </Button>
            )}
          </AccordionDetails>
        </Accordion>
      ))}

      <Dialog open={!!target} onClose={() => setTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>Roll back to version #{target?.id}?</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            The look that was live after #{target?.id} is checked against today's rules and
            published again for everyone in this scope.
          </DialogContentText>
          {rollback.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {apiErrorMessage(rollback.error, 'The rollback was refused.')}
            </Alert>
          )}
          <TextField fullWidth label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)}
            inputProps={{ maxLength: 300 }} />
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setTarget(null)}>Cancel</Button>
          <Button variant="contained" disabled={rollback.isPending} onClick={() => rollback.mutate()}>
            {rollback.isPending ? 'Rolling back…' : 'Roll back'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ThemeHistory;
