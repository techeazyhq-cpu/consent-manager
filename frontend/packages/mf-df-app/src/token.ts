import { ensureFreshToken, getKeycloak, type AuthInfo } from "auth-kit";

/** A token valid for at least the next 30 seconds, refreshed if need be. */
export async function freshToken(auth: AuthInfo): Promise<string> {
  const kc = getKeycloak();
  if (!kc) return auth.token;
  await ensureFreshToken(kc);
  return kc.token ?? auth.token;
}
