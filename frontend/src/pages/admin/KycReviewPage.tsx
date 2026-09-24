import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import { OpenInNew } from '@mui/icons-material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  KycDocument, approveKyc, fetchKycFileForReview, fetchPendingKyc, kycTypeLabel, openFileLink, rejectKyc,
} from '../../services/profileApi';

/**
 * The queue of KYC documents waiting for review, oldest first. Opening a document is audited, and
 * each link expires within minutes, so a copied URL does not stay a way into someone's ID.
 */
const KycReviewPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { formatDateTime } = useDateTime();
  const [rejecting, setRejecting] = useState<KycDocument | null>(null);
  const [reason, setReason] = useState('');
  const [openError, setOpenError] = useState<string | null>(null);

  const pending = useQuery({ queryKey: ['kyc-pending'], queryFn: () => fetchPendingKyc(), retry: false });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['kyc-pending'] });

  const approve = useMutation({ mutationFn: approveKyc, onSuccess: refresh });
  const reject = useMutation({
    mutationFn: () => rejectKyc(rejecting!.id, reason.trim()),
    onSuccess: () => {
      setRejecting(null);
      setReason('');
      refresh();
    },
  });

  const open = (id: number) => {
    setOpenError(null);
    openFileLink(() => fetchKycFileForReview(id)).catch((e) =>
      setOpenError(apiErrorMessage(e, 'The document could not be opened.')));
  };

  const documents = pending.data?.data ?? [];

  return (
    <Box>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>KYC review</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Documents members submitted for verification, oldest first. Approving one marks the member verified.
      </Typography>

      {pending.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(pending.error, 'Could not load the queue.')}</Alert>
      )}
      {(approve.isError || openError) && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {openError ?? apiErrorMessage(approve.error, 'The document could not be approved.')}
        </Alert>
      )}
      {pending.isLoading && <CircularProgress size={28} />}
      {pending.isSuccess && documents.length === 0 && (
        <Card><CardContent><Typography color="text.secondary">Nothing waiting for review.</Typography></CardContent></Card>
      )}

      <Stack spacing={1.5}>
        {documents.map((doc) => (
          <Card key={doc.id} data-testid={`kyc-review-${doc.id}`}>
            <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <Typography sx={{ fontWeight: 600 }}>
                  {kycTypeLabel(doc.documentType)}{doc.documentNumber ? ` · ${doc.documentNumber}` : ''}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  User #{doc.userId} · submitted {formatDateTime(doc.createdAt)}
                </Typography>
              </Box>
              <Button size="small" startIcon={<OpenInNew />} onClick={() => open(doc.id)}>View document</Button>
              <Button size="small" variant="contained" color="success"
                disabled={approve.isPending} onClick={() => approve.mutate(doc.id)}>
                Approve
              </Button>
              <Button size="small" variant="outlined" color="error" onClick={() => setRejecting(doc)}>Reject</Button>
            </CardContent>
          </Card>
        ))}
      </Stack>

      <Dialog open={!!rejecting} onClose={() => setRejecting(null)} fullWidth maxWidth="xs">
        <DialogTitle>Reject {rejecting && kycTypeLabel(rejecting.documentType)}?</DialogTitle>
        <DialogContent>
          {reject.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(reject.error, 'The document could not be rejected.')}</Alert>
          )}
          <TextField
            autoFocus fullWidth multiline rows={2} sx={{ mt: 1 }}
            label="Reason (shown to the member)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            inputProps={{ maxLength: 500 }}
          />
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setRejecting(null)}>Cancel</Button>
          <Button variant="contained" color="error" disabled={!reason.trim() || reject.isPending} onClick={() => reject.mutate()}>
            {reject.isPending ? 'Rejecting…' : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default KycReviewPage;
