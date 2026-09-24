import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { post: vi.fn(), get: vi.fn() } }));

import api from './api';
import { describeRules, uploadFile, validateFile } from './mediaApi';

const post = vi.mocked(api.post);
const file = (type: string, size: number, name = 'f') => new File([new Uint8Array(size)], name, { type });

/** A stand-in for the browser's XHR, recording what was sent to storage. */
class FakeXhr {
  static last: FakeXhr;
  static status = 204;
  method = '';
  url = '';
  body: FormData | null = null;
  upload: { onprogress?: (e: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  status = 0;
  open(method: string, url: string) { this.method = method; this.url = url; }
  send(body: FormData) {
    FakeXhr.last = this;
    this.body = body;
    this.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 100 });
    this.status = FakeXhr.status;
    setTimeout(() => (this.status ? this.onload?.() : this.onerror?.()), 0);
  }
}

describe('mediaApi', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    FakeXhr.status = 204;
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('describes and enforces each purpose\'s rules before sending anything', () => {
    expect(describeRules('KYC_DOCUMENT')).toBe('JPG, PNG, WebP or PDF, up to 10 MB');
    expect(validateFile(file('image/png', 10), 'AVATAR')).toBeNull();
    expect(validateFile(file('image/svg+xml', 10), 'AVATAR')).toMatch(/not allowed/);
    expect(validateFile(file('application/pdf', 10), 'AVATAR')).toMatch(/not allowed/);
    expect(validateFile(file('image/png', 6 * 1024 * 1024), 'AVATAR')).toMatch(/limit is 5 MB/);
    expect(validateFile(file('image/png', 0), 'AVATAR')).toMatch(/empty/);
  });

  it('asks for a slot, posts the signed form with the file last, then completes', async () => {
    post
      .mockResolvedValueOnce({ data: { mediaId: 'm1', uploadUrl: 'http://storage/b', fields: { key: 'k', policy: 'p' },
        maxBytes: 1, expiresAt: '' } })
      .mockResolvedValueOnce({ data: { id: 'm1', url: 'http://storage/b/k', status: 'READY' } });
    const progress = vi.fn();

    const media = await uploadFile(file('image/png', 10, 'me.png'), 'AVATAR', progress);

    expect(post).toHaveBeenNthCalledWith(1, '/media/uploads',
      { purpose: 'AVATAR', filename: 'me.png', contentType: 'image/png', sizeBytes: 10 });
    expect(FakeXhr.last.method).toBe('POST');
    expect(FakeXhr.last.url).toBe('http://storage/b');
    expect([...FakeXhr.last.body!.keys()]).toEqual(['key', 'policy', 'file']);
    expect(progress).toHaveBeenCalledWith(50);
    expect(post).toHaveBeenNthCalledWith(2, '/media/m1/complete');
    expect(media.url).toBe('http://storage/b/k');
  });

  it('a refused storage upload is reported and never completed', async () => {
    FakeXhr.status = 403;
    post.mockResolvedValueOnce({ data: { mediaId: 'm1', uploadUrl: 'http://storage/b', fields: {}, maxBytes: 1, expiresAt: '' } });
    await expect(uploadFile(file('image/png', 10), 'AVATAR')).rejects.toThrow(/refused the upload/);
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('an invalid file never reaches the server', async () => {
    await expect(uploadFile(file('text/html', 10), 'AVATAR')).rejects.toThrow(/not allowed/);
    expect(post).not.toHaveBeenCalled();
  });
});
