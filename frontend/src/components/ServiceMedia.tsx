import React from 'react';
import { Box } from '@mui/material';

interface ServiceMediaProps {
  mediaUrl?: string | null;
  mediaType?: 'IMAGE' | 'VIDEO' | 'ANIMATION' | null;
  title: string;
  height?: number;
}

/**
 * The optional photo, video or animation an admin attached to a catalogue item.
 *
 * Renders nothing at all when there is no media, rather than a grey placeholder: most items carry
 * only an icon, and a hundred empty bands would push every card's actual content below the fold.
 *
 * Videos and animations autoplay muted, inline and looping — a card is a thumbnail, so a play
 * button that navigates away from the booking flow when tapped is worse than a silent loop, and
 * muted+playsInline is what browsers require for autoplay to be allowed at all.
 */
const ServiceMedia: React.FC<ServiceMediaProps> = ({ mediaUrl, mediaType, title, height = 140 }) => {
  if (!mediaUrl) return null;

  const isVideo = mediaType === 'VIDEO';

  return (
    <Box
      sx={{
        height,
        mb: 2,
        borderRadius: 2,
        overflow: 'hidden',
        bgcolor: 'action.hover',
      }}
    >
      {isVideo ? (
        <Box
          component="video"
          src={mediaUrl}
          autoPlay
          muted
          loop
          playsInline
          // Not `controls`: the card itself is the click target, and native controls would swallow
          // the tap that is meant to open the booking page.
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : (
        <Box
          component="img"
          src={mediaUrl}
          alt={title}
          loading="lazy"
          // A broken link hides the band instead of leaving a torn-image icon on the card.
          onError={(event: React.SyntheticEvent<HTMLImageElement>) => {
            event.currentTarget.style.display = 'none';
          }}
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      )}
    </Box>
  );
};

export default ServiceMedia;
