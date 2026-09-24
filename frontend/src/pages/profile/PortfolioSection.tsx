import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, CardMedia, CircularProgress, Grid, IconButton, Stack,
  TextField, Tooltip, Typography,
} from '@mui/material';
import { Delete } from '@mui/icons-material';
import FileUploadButton from '../../components/FileUploadButton';
import { apiErrorMessage } from '../../services/apiError';
import { Media } from '../../services/mediaApi';
import { addPortfolioItem, deletePortfolioItem, fetchMyPortfolio } from '../../services/profileApi';

/** Photos of past work, shown to customers on the worker's profile. */
const PortfolioSection: React.FC = () => {
  const queryClient = useQueryClient();
  const [photo, setPhoto] = useState<Media | null>(null);
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');

  const items = useQuery({ queryKey: ['my-portfolio'], queryFn: fetchMyPortfolio, retry: false });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['my-portfolio'] });
  const add = useMutation({
    mutationFn: () => addPortfolioItem({
      title: title.trim(), category: category.trim() || undefined,
      description: description.trim() || undefined, mediaId: photo!.id,
    }),
    onSuccess: () => {
      setPhoto(null);
      setTitle('');
      setCategory('');
      setDescription('');
      refresh();
    },
  });
  const remove = useMutation({ mutationFn: deletePortfolioItem, onSuccess: refresh });

  return (
    <Box data-testid="portfolio-section">
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>Portfolio</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Photos of work you have completed. Customers see these on your profile.
      </Typography>

      <Stack spacing={2} sx={{ maxWidth: 480, mb: 4 }}>
        {add.isError && <Alert severity="error">{apiErrorMessage(add.error, 'The photo could not be added.')}</Alert>}
        {photo?.url ? (
          <Box component="img" src={photo.url} alt="New portfolio photo" sx={{ maxHeight: 180, borderRadius: 2, objectFit: 'cover' }} />
        ) : (
          <FileUploadButton purpose="PORTFOLIO" label="Choose photo" onUploaded={setPhoto} />
        )}
        <TextField label="Title" required value={title} onChange={(e) => setTitle(e.target.value)} inputProps={{ maxLength: 255 }} />
        <TextField label="Category (optional)" value={category} onChange={(e) => setCategory(e.target.value)} inputProps={{ maxLength: 100 }} />
        <TextField label="Description (optional)" multiline rows={2} value={description}
          onChange={(e) => setDescription(e.target.value)} inputProps={{ maxLength: 2000 }} />
        <Box>
          <Button variant="contained" disabled={!photo || !title.trim() || add.isPending} onClick={() => add.mutate()}>
            {add.isPending ? 'Adding…' : 'Add to portfolio'}
          </Button>
          {photo && <Button color="inherit" sx={{ ml: 1 }} onClick={() => setPhoto(null)}>Discard photo</Button>}
        </Box>
      </Stack>

      {items.isLoading && <CircularProgress size={24} />}
      {items.isError && <Alert severity="error">{apiErrorMessage(items.error, 'Could not load your portfolio.')}</Alert>}
      {remove.isError && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(remove.error, 'Could not remove that item.')}</Alert>}
      {items.data?.length === 0 && <Typography variant="body2" color="text.secondary">No photos yet.</Typography>}
      <Grid container spacing={2}>
        {items.data?.map((item) => (
          <Grid item xs={12} sm={6} md={4} key={item.id}>
            <Card variant="outlined" data-testid={`portfolio-item-${item.id}`}>
              <CardMedia component="img" height="160" image={item.imageUrl} alt={item.title} />
              <CardContent sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.title}</Typography>
                  {item.category && <Typography variant="caption" color="text.secondary">{item.category}</Typography>}
                </Box>
                <Tooltip title="Remove">
                  <IconButton size="small" aria-label={`Remove ${item.title}`} onClick={() => remove.mutate(item.id)}>
                    <Delete fontSize="small" />
                  </IconButton>
                </Tooltip>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default PortfolioSection;
