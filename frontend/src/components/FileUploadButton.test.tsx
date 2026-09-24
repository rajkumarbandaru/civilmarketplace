import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

vi.mock('../services/mediaApi', async (orig) => ({
  ...(await orig<typeof import('../services/mediaApi')>()),
  uploadFile: vi.fn(),
}));

import { uploadFile } from '../services/mediaApi';
import FileUploadButton from './FileUploadButton';

const upload = vi.mocked(uploadFile);
const pick = (type: string) =>
  fireEvent.change(screen.getByTestId('upload-input-AVATAR'), {
    target: { files: [new File(['x'], 'me.png', { type })] },
  });

describe('FileUploadButton', () => {
  beforeEach(() => vi.resetAllMocks());

  it('shows the rules and limits the picker to allowed types', () => {
    render(<FileUploadButton purpose="AVATAR" onUploaded={vi.fn()} />);
    expect(screen.getByText('JPG, PNG, WebP or GIF, up to 5 MB')).toBeInTheDocument();
    expect(screen.getByTestId('upload-input-AVATAR')).toHaveAttribute('accept', 'image/jpeg,image/png,image/webp,image/gif');
  });

  it('hands the verified file to the caller', async () => {
    const media = { id: 'm1', url: 'http://cdn/me.png' };
    upload.mockResolvedValue(media as never);
    const onUploaded = vi.fn();
    render(<FileUploadButton purpose="AVATAR" onUploaded={onUploaded} />);
    pick('image/png');
    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith(media));
    expect(upload).toHaveBeenCalledWith(expect.any(File), 'AVATAR', expect.any(Function));
  });

  it('reports a failure in words, including one from the caller\'s own step', async () => {
    upload.mockRejectedValue(new Error('This file type is not allowed. Use JPG.'));
    render(<FileUploadButton purpose="AVATAR" onUploaded={vi.fn()} />);
    pick('text/html');
    expect(await screen.findByRole('alert')).toHaveTextContent('This file type is not allowed');

    upload.mockResolvedValue({ id: 'm2' } as never);
    const failing = vi.fn().mockRejectedValue(new Error('Could not save the photo'));
    render(<FileUploadButton purpose="AVATAR" onUploaded={failing} label="Again" />);
    fireEvent.change(screen.getAllByTestId('upload-input-AVATAR')[1], {
      target: { files: [new File(['x'], 'me.png', { type: 'image/png' })] },
    });
    expect(await screen.findByText('Could not save the photo')).toBeInTheDocument();
  });

  it('renders a custom trigger', () => {
    render(<FileUploadButton purpose="AVATAR" onUploaded={vi.fn()} showHint={false}
      renderTrigger={(open) => <button onClick={open}>Change photo</button>} />);
    expect(screen.getByRole('button', { name: 'Change photo' })).toBeInTheDocument();
    expect(screen.queryByText(/up to 5 MB/)).not.toBeInTheDocument();
  });
});
