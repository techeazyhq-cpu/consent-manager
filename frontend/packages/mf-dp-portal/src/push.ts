import type { FirebaseApp, FirebaseOptions } from "@firebase/app";
import type { MessagePayload } from "@firebase/messaging";

/**
 * Web push for the DP portal. With a Firebase project configured (VITE_FIREBASE_*), the browser gets a real
 * Firebase Cloud Messaging token that notification-service can push to. Without one, a dev build registers a
 * stand-in token instead, so the whole register → notify → catcher flow still works locally; a production build
 * without Firebase hides the feature.
 */
const env = import.meta.env;

const FIREBASE: FirebaseOptions = {
  apiKey: env.VITE_FIREBASE_API_KEY,
  projectId: env.VITE_FIREBASE_PROJECT_ID,
  messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: env.VITE_FIREBASE_APP_ID,
};
const VAPID_KEY: string | undefined = env.VITE_FIREBASE_VAPID_KEY;

export type PushMode = "fcm" | "dev" | "off";

export const pushMode: PushMode =
  FIREBASE.apiKey && FIREBASE.projectId && FIREBASE.messagingSenderId && FIREBASE.appId && VAPID_KEY
    ? "fcm"
    : env.DEV
      ? "dev"
      : "off";

const DEVICE_ID_KEY = "cm.push.dvcId";
const DEV_TOKEN_KEY = "cm.push.devToken";

/** The browser said no to notifications; only the user can undo that, in the site settings. */
export class PushBlockedError extends Error {
  constructor() {
    super("Notifications are blocked for this site in your browser settings.");
  }
}

export function pushSupported(): boolean {
  if (pushMode === "dev") return true;
  return pushMode === "fcm" && "serviceWorker" in navigator && "Notification" in window && "PushManager" in window;
}

export function notificationsBlocked(): boolean {
  return pushMode === "fcm" && "Notification" in window && Notification.permission === "denied";
}

// Per-browser memory of which registered device this browser is. Storage can be unavailable (private mode,
// blocked site data): then the toggle just starts "off" on each visit.
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // see read()
  }
}

export const storedDeviceId = () => read(DEVICE_ID_KEY);
export const storeDeviceId = (id: string | null) => write(DEVICE_ID_KEY, id);

let appPromise: Promise<FirebaseApp> | null = null;

function firebaseApp(): Promise<FirebaseApp> {
  appPromise ??= import("@firebase/app").then(({ getApps, initializeApp }) => getApps()[0] ?? initializeApp(FIREBASE));
  return appPromise;
}

/** Asks for permission if needed and returns this browser's push token. */
export async function obtainToken(): Promise<string> {
  if (pushMode === "dev") {
    let token = read(DEV_TOKEN_KEY);
    if (!token) {
      token = `dev-web-${crypto.randomUUID()}`;
      write(DEV_TOKEN_KEY, token);
    }
    return token;
  }
  if ((await Notification.requestPermission()) !== "granted") throw new PushBlockedError();
  const [{ getMessaging, getToken }, app] = await Promise.all([import("@firebase/messaging"), firebaseApp()]);
  // The service worker can't read Vite's env, so it gets the (public) web config in its URL.
  const config = new URLSearchParams(FIREBASE as Record<string, string>);
  const registration = await navigator.serviceWorker.register(`/firebase-messaging-sw.js?${config}`);
  return getToken(getMessaging(app), { vapidKey: VAPID_KEY, serviceWorkerRegistration: registration });
}

/** Stops Firebase delivering to this browser; the device row is removed separately through the API. */
export async function releaseToken(): Promise<void> {
  if (pushMode !== "fcm") return;
  const [{ getMessaging, deleteToken }, app] = await Promise.all([import("@firebase/messaging"), firebaseApp()]);
  await deleteToken(getMessaging(app));
}

/** Pushes that arrive while the portal is open and focused (the browser shows the others itself). */
export async function onForegroundPush(callback: (payload: MessagePayload) => void): Promise<() => void> {
  if (pushMode !== "fcm" || Notification.permission !== "granted") return () => {};
  const [{ getMessaging, onMessage }, app] = await Promise.all([import("@firebase/messaging"), firebaseApp()]);
  return onMessage(getMessaging(app), callback);
}
