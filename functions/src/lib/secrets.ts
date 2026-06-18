import { SecretManagerServiceClient } from "@google-cloud/secret-manager";
import { logger } from "firebase-functions/v2";

const PROJECT_ID = "crumbs-a4fdb";

const OAUTH_STATE_SECRET = "crumb-oauth-state-secret";
const X_CLIENT_ID_SECRET = "crumb-x-client-id";
const X_CLIENT_SECRET_SECRET = "crumb-x-client-secret";

const REFRESH_TOKEN_PREFIX = "crumb-x-refresh-token-";

let _client: SecretManagerServiceClient | null = null;
function client(): SecretManagerServiceClient {
  if (_client) return _client;
  _client = new SecretManagerServiceClient();
  return _client;
}

let _stateKey: Uint8Array | null = null;
let _xClient: { clientId: string; clientSecret: string } | null = null;

export class SecretNotFoundError extends Error {
  constructor(name: string) {
    super(`secret_not_found:${name}`);
    this.name = "SecretNotFoundError";
  }
}

function isNotFound(err: unknown): boolean {
  return typeof err === "object" && err !== null && "code" in err && (err as { code: number }).code === 5;
}

function refreshTokenSecretName(uid: string): string {
  return `${REFRESH_TOKEN_PREFIX}${uid}`;
}

function fullSecretPath(secret: string, version = "latest"): string {
  return `projects/${PROJECT_ID}/secrets/${secret}/versions/${version}`;
}

export async function getOAuthStateSecret(): Promise<Uint8Array> {
  if (_stateKey) return _stateKey;
  const [response] = await client().accessSecretVersion({ name: fullSecretPath(OAUTH_STATE_SECRET) });
  const payload = response.payload?.data;
  if (!payload) throw new SecretNotFoundError(OAUTH_STATE_SECRET);
  _stateKey = payload instanceof Uint8Array ? payload : new Uint8Array(Buffer.from(payload));
  return _stateKey;
}

export async function getXClientCredentials(): Promise<{ clientId: string; clientSecret: string }> {
  if (_xClient) return _xClient;
  const [idResp, secretResp] = await Promise.all([
    client().accessSecretVersion({ name: fullSecretPath(X_CLIENT_ID_SECRET) }),
    client().accessSecretVersion({ name: fullSecretPath(X_CLIENT_SECRET_SECRET) }),
  ]);
  const clientId = idResp[0].payload?.data?.toString();
  const clientSecret = secretResp[0].payload?.data?.toString();
  if (!clientId || !clientSecret) {
    throw new SecretNotFoundError(`${X_CLIENT_ID_SECRET}|${X_CLIENT_SECRET_SECRET}`);
  }
  _xClient = { clientId, clientSecret };
  return _xClient;
}

export async function setRefreshToken(uid: string, token: string): Promise<void> {
  const secretId = refreshTokenSecretName(uid);
  const parent = `projects/${PROJECT_ID}`;
  const secretPath = `${parent}/secrets/${secretId}`;

  let previousVersionName: string | null = null;
  try {
    const [prev] = await client().accessSecretVersion({ name: `${secretPath}/versions/latest` });
    previousVersionName = prev.name ?? null;
  } catch (err) {
    if (!isNotFound(err)) throw err;
    await client().createSecret({
      parent,
      secretId,
      secret: { replication: { automatic: {} } },
    });
  }

  await client().addSecretVersion({
    parent: secretPath,
    payload: { data: Buffer.from(token, "utf8") },
  });

  if (previousVersionName) {
    try {
      await client().disableSecretVersion({ name: previousVersionName });
    } catch (err) {
      logger.warn("secret_disable_previous_failed", {
        secret: secretId,
        code: (err as { code?: number }).code,
      });
    }
  }
}

export async function getRefreshToken(uid: string): Promise<string | null> {
  const name = fullSecretPath(refreshTokenSecretName(uid));
  try {
    const [response] = await client().accessSecretVersion({ name });
    const data = response.payload?.data;
    if (!data) return null;
    return data.toString();
  } catch (err) {
    if (isNotFound(err)) return null;
    throw err;
  }
}

// One call removes the secret + every version atomically. NOT_FOUND (code 5) is
// swallowed so the disconnect flow stays idempotent when the user never linked
// or has already disconnected.
export async function deleteRefreshToken(uid: string): Promise<void> {
  const secretId = refreshTokenSecretName(uid);
  const name = `projects/${PROJECT_ID}/secrets/${secretId}`;
  try {
    await client().deleteSecret({ name });
  } catch (err) {
    if (isNotFound(err)) return;
    throw err;
  }
}
