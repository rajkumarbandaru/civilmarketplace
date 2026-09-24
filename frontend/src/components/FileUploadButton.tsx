import React, { useRef, useState } from 'react';
import { Box, Button, ButtonProps, LinearProgress, Typography } from '@mui/material';
import { CloudUpload } from '@mui/icons-material';
import { apiErrorMessage } from '../services/apiError';
import { Media, MediaPurpose, PURPOSE_RULES, describeRules, uploadFile } from '../services/mediaApi';

export interface FileUploadButtonProps {
  purpose: MediaPurpose;
  /** Called with the verified file once the upload has finished. */
  onUploaded: (media: Media) => void | Promise<void>;
  label?: string;
  /** Show "JPG, PNG … up to 5 MB" under the button. */
  showHint?: boolean;
  disabled?: boolean;
  buttonProps?: ButtonProps;
  /** Render a custom trigger instead of the default button (e.g. the avatar camera icon). */
  renderTrigger?: (open: () => void, busy: boolean) => React.ReactNode;
}

/**
 * The one way files get uploaded in the app. Checks type and size before sending (the server
 * checks again, and reads the file's real bytes), shows progress and reports failures in words.
 */
const FileUploadButton: React.FC<FileUploadButtonProps> = ({
  purpose,
  onUploaded,
  label = 'Upload',
  showHint = true,
  disabled,
  buttonProps,
  renderTrigger,
}) => {
  const input = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const busy = progress !== null;

  const open = () => {
    if (!busy && !disabled) input.current?.click();
  };

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    setProgress(0);
    try {
      const media = await uploadFile(file, purpose, setProgress);
      await onUploaded(media);
    } catch (e) {
      setError(apiErrorMessage(e, e instanceof Error ? e.message : 'The upload failed.'));
    } finally {
      setProgress(null);
      if (input.current) input.current.value = '';
    }
  };

  return (
    <Box>
      <input
        ref={input}
        type="file"
        hidden
        accept={PURPOSE_RULES[purpose].types.join(',')}
        data-testid={`upload-input-${purpose}`}
        onChange={(e) => onFile(e.target.files?.[0])}
      />
      {renderTrigger ? (
        renderTrigger(open, busy)
      ) : (
        <Button
          variant="outlined"
          size="small"
          startIcon={<CloudUpload />}
          onClick={open}
          disabled={busy || disabled}
          {...buttonProps}
        >
          {busy ? 'Uploading…' : label}
        </Button>
      )}
      {busy && (
        <LinearProgress
          variant="determinate"
          value={progress ?? 0}
          sx={{ mt: 1, maxWidth: 240 }}
          aria-label="Upload progress"
        />
      )}
      {showHint && !error && !busy && (
        <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>
          {describeRules(purpose)}
        </Typography>
      )}
      {error && (
        <Typography variant="caption" color="error" component="div" role="alert" sx={{ mt: 0.5 }}>
          {error}
        </Typography>
      )}
    </Box>
  );
};

export default FileUploadButton;
