import * as jose from 'jose';

export function createAuthMiddleware(options = {}) {
  const {
    jwkSetUri,
    issuerUris = [],
    jwks = null,
    testVerify = null
  } = options;

  let remoteJWKSet = jwks;
  if (!remoteJWKSet && jwkSetUri) {
    try {
      remoteJWKSet = jose.createRemoteJWKSet(new URL(jwkSetUri));
    } catch (e) {
      console.warn('[Auth] Error initializing remote JWKS:', e.message);
    }
  }

  const allowedIssuers = Array.from(new Set(issuerUris)).filter(Boolean);

  return async function authMiddleware(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        error: 'UNAUTHORIZED',
        message: 'Missing or malformed Bearer token'
      });
    }

    const token = authHeader.substring(7).trim();
    if (!token) {
      return res.status(401).json({
        error: 'UNAUTHORIZED',
        message: 'Missing Bearer token'
      });
    }

    try {
      let payload;
      if (testVerify) {
        payload = await testVerify(token);
      } else {
        const verifyOptions = {};
        if (allowedIssuers.length > 0) {
          verifyOptions.issuer = allowedIssuers;
        }
        const verifyResult = await jose.jwtVerify(token, remoteJWKSet, verifyOptions);
        payload = verifyResult.payload;
      }

      if (!payload || !payload.sub) {
        return res.status(401).json({
          error: 'UNAUTHORIZED',
          message: 'Token has no valid subject claim'
        });
      }

      req.user = {
        sub: payload.sub,
        email: payload.email || payload.preferred_username || null,
        roles: payload.realm_access?.roles || []
      };

      next();
    } catch (err) {
      return res.status(401).json({
        error: 'UNAUTHORIZED',
        message: err.message || 'Invalid or expired token'
      });
    }
  };
}
