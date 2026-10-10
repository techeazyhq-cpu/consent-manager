/* Background web push for the DP portal (see src/push.ts). These are the compat builds of Firebase 10.14.1, the
   release @firebase/app 0.10.13 and @firebase/messaging 0.12.12 belong to: keep them in step with package.json. */
/* global importScripts, firebase, clients */
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js");

const params = new URL(self.location.href).searchParams;
firebase.initializeApp({
  apiKey: params.get("apiKey"),
  projectId: params.get("projectId"),
  messagingSenderId: params.get("messagingSenderId"),
  appId: params.get("appId"),
});

// A push with a "notification" part is shown by Firebase itself; a data-only push is shown here.
firebase.messaging().onBackgroundMessage((payload) => {
  if (payload.notification) return;
  const data = payload.data || {};
  self.registration.showNotification(data.title || "New consent request", { body: data.body || "" });
});

// Clicking the notification brings the portal forward, or opens it.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      const open = windows.find((w) => "focus" in w);
      return open ? open.focus() : clients.openWindow("/");
    }),
  );
});
