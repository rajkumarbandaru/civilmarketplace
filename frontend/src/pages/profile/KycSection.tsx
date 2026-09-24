import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { Description, OpenInNew } from '@mui/icons-material';
import FileUploadButton from '../../components/FileUploadButton';
import { apiErrorMessage } from '../../services/apiError';
import { Media } from '../../services/mediaApi';
import {
  KYC_DOCUMENT_TYPES, KycDocumentType, KycStatus, fetchMyKycDocuments, fetchMyKycFile, kycTypeLabel,
  openFileLink, submitKycDocument,
} from '../../services/profileApi';

const STATUS_COLOR: Record<KycStatus, 'warning' | 'success' | 'error'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
};

/**
 * Identity and business documents for verification. Files are private: only the owner and staff
 * can open them, each time through a link that expires within minutes.
 */
const KycSection: React.FC = () => {
  const queryClient = useQueryClient();
  const [type, setType] = useState<KycDocumentType>('AADHAAR');
  const [number, setNumber] = useState('');
  const [file, setFile] = useState<Media | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);

  const documents = useQuery({ queryKey: ['my-kyc'], queryFn: fetchMyKycDocuments, retry: false });
  const submit = useMutation({
    mutationFn: () => submitKycDocument({ documentType: type, documentNumber: number || undefined, mediaId: file!.id }),
    onSuccess: () => {
      setFile(null);
      setNumber('');
      queryClient.invalidateQueries({ queryKey: ['my-kyc'] });
    },
  });

  const open = (id: number) => {
    setOpenError(null);
    openFileLink(() => fetchMyKycFile(id)).catch((e) =>
      setOpenError(apiErrorMessage(e, 'The document could not be opened.')));
  };

  return (
    <Box data-testid="kyc-section">
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>Verification documents</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Upload an ID or business document to get your profile verified. Only you and our review team
        can open these files.
      </Typography>

      <Stack spacing={2} sx={{ maxWidth: 480, mb: 4 }}>
        {submit.isError && (
          <Alert severity="error">{apiErrorMessage(submit.error, 'The document could not be submitted.')}</Alert>
        )}
        <TextField select label="Document type" value={type} onChange={(e) => setType(e.target.value as KycDocumentType)}>
          {KYC_DOCUMENT_TYPES.map((t) => <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>)}
        </TextField>
        <TextField
          label="Document number (optional)"
          value={number}
          onChange={(e) => setNumber(e.target.value)}
          inputProps={{ maxLength: 100 }}
        />
        {file ? (
          <Stack direction="row" spacing={1} alignItems="center">
            <Description color="action" />
            <Typography variant="body2" sx={{ flex: 1, wordBreak: 'break-all' }}>{file.originalFilename}</Typography>
            <Button size="small" color="inherit" onClick={() => setFile(null)}>Change</Button>
          </Stack>
        ) : (
          <FileUploadButton purpose="KYC_DOCUMENT" label="Choose document" onUploaded={setFile} />
        )}
        <Box>
          <Button variant="contained" disabled={!file || submit.isPending} onClick={() => submit.mutate()}>
            {submit.isPending ? 'Submitting…' : 'Submit for verification'}
          </Button>
        </Box>
      </Stack>

      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Submitted</Typography>
      {openError && <Alert severity="error" sx={{ mb: 2 }}>{openError}</Alert>}
      {documents.isLoading && <CircularProgress size={24} />}
      {documents.isError && (
        <Alert severity="error">{apiErrorMessage(documents.error, 'Could not load your documents.')}</Alert>
      )}
      {documents.data?.length === 0 && (
        <Typography variant="body2" color="text.secondary">Nothing submitted yet.</Typography>
      )}
      <Stack spacing={1}>
        {documents.data?.map((doc) => (
          <Box
            key={doc.id}
            data-testid={`kyc-doc-${doc.id}`}
            sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}
          >
            <Box sx={{ flex: 1, minWidth: 180 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {kycTypeLabel(doc.documentType)}{doc.documentNumber ? ` · ${doc.documentNumber}` : ''}
              </Typography>
              {doc.status === 'REJECTED' && doc.rejectionReason && (
                <Typography variant="caption" color="error">Reason: {doc.rejectionReason}</Typography>
              )}
            </Box>
            <Chip size="small" label={doc.status} color={STATUS_COLOR[doc.status]} />
            <Button size="small" startIcon={<OpenInNew />} onClick={() => open(doc.id)}>View</Button>
          </Box>
        ))}
      </Stack>
    </Box>
  );
};

export default KycSection;
