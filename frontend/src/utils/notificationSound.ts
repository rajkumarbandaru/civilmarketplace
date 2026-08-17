/**
 * The chime that plays when a notification arrives.
 *
 * Synthesised with the Web Audio API rather than shipped as an mp3: it is two sine tones and an
 * envelope, which costs a few hundred bytes of code instead of a binary in the repo, needs no
 * network request at the moment it has to play, and cannot be blocked by the artifact CSP the way
 * a remote asset would.
 *
 * Nothing here throws. A browser that refuses to play audio is a browser where the badge still
 * updates silently, which is the pre-existing behaviour — never a broken bell.
 */

const STORAGE_KEY = 'notificationSoundMuted';

/**
 * One context for the app's lifetime, created lazily.
 *
 * Constructing an AudioContext per chime leaks them — browsers cap the number a page may hold
 * (Chrome at six) and simply refuse to create more, so the sound dies after a handful of
 * notifications.
 */
let context: AudioContext | null = null;

/** Guards against a double chime when two shells briefly mount the bell at once. */
let lastPlayedAt = 0;

const getContext = (): AudioContext | null => {
  if (context) return context;
  const Ctor = window.AudioContext
    || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  try {
    context = new Ctor();
    return context;
  } catch {
    return null;
  }
};

export const isNotificationSoundMuted = (): boolean => {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    // Private mode and locked-down profiles throw on localStorage. Audible is the useful default.
    return false;
  }
};

export const setNotificationSoundMuted = (muted: boolean): void => {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(muted));
  } catch {
    // A preference that cannot be saved is not worth breaking the click over.
  }
};

/**
 * A soft two-note chime — a fifth apart, the second landing while the first is still fading, which
 * reads as a doorbell rather than an alarm. This fires for routine booking updates as well as
 * platform alerts, so it has to be something an admin can hear all day.
 */
export const playNotificationSound = (): void => {
  if (isNotificationSoundMuted()) return;

  const now = Date.now();
  if (now - lastPlayedAt < 1000) return;
  lastPlayedAt = now;

  const ctx = getContext();
  if (!ctx) return;

  // Autoplay policy parks the context until the page has been interacted with. By the time a
  // notification arrives the user has almost always clicked something; if not, this resolves
  // later and only the current chime is lost.
  if (ctx.state === 'suspended') {
    ctx.resume().catch(() => undefined);
  }

  try {
    const start = ctx.currentTime;
    // E6 then B6. Ascending, so it reads as "something arrived" rather than "something failed".
    [
      { freq: 1318.5, at: 0 },
      { freq: 1975.5, at: 0.12 },
    ].forEach(({ freq, at }) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;

      // An abrupt start or stop on a sine wave clicks audibly. Ramping the gain instead of
      // switching it gives the note its shape and removes the click at both ends.
      const t = start + at;
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(0.12, t + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(t);
      osc.stop(t + 0.4);
    });
  } catch {
    // Nothing to recover: the badge has already updated, the sound is the garnish.
  }
};
