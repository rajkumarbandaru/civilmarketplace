import React from 'react';
import * as MuiIcons from '@mui/icons-material';
import { SvgIconProps } from '@mui/material';

/**
 * Renders the Material-UI icon named in the menu catalogue.
 *
 * The catalogue stores an icon *name* rather than a glyph, so the console can offer a picker and
 * so a renamed icon fails visibly here instead of silently painting a box. An unknown name falls
 * back to a neutral bullet rather than throwing — one bad catalogue row must not blank the whole
 * sidebar.
 */
const DynamicIcon: React.FC<{ name: string } & SvgIconProps> = ({ name, ...props }) => {
  const Icon = (MuiIcons as Record<string, React.ComponentType<SvgIconProps>>)[name];
  if (!Icon) {
    return <MuiIcons.FiberManualRecord fontSize="small" {...props} />;
  }
  return <Icon {...props} />;
};

export default DynamicIcon;
