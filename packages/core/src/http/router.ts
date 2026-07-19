import {
  DecodeError,
  approvedEnrollResponse,
  decodeEnrollRequest,
  decodeEnrollStatusRequest,
  deniedEnrollResponse,
  enrollRejection,
  pendingEnrollResponse,
} from '@luno/protocol';
import type { CoreContext } from '../context';
import type { PairingService } from '../services/pairing';

/**
 * The request shape the router needs, declared structurally rather than as the
 * DOM `Request`. A real `Request` satisfies it on every runtime, and so does a
 * three-line shim over an Express request — which is what keeps this usable on
 * platforms that have no fetch objects at all.
 */
export interface HttpRequest {
  readonly method: string;
  readonly url: string;
  json(): Promise<unknown>;
}

export interface HttpResult {
  status: number;
  headers: Record<string, string>;
  body: string;
}

const json = (status: number, body: unknown): HttpResult => ({
  status,
  headers: { 'content-type': 'application/json; charset=utf-8' },
  body: JSON.stringify(body),
});

/** Extracts the path without `URL`, which is not in the runtime intersection. */
function pathnameOf(url: string): string {
  const withoutOrigin = url.replace(/^[a-z][a-z0-9+.-]*:\/\/[^/]*/i, '');
  const path = withoutOrigin.split('?')[0]?.split('#')[0] ?? '';
  return path || '/';
}

export function httpRouter(context: CoreContext, pairing: PairingService) {
  async function readBody(request: HttpRequest): Promise<unknown> {
    try {
      return await request.json();
    } catch {
      throw new DecodeError('body is not valid JSON');
    }
  }

  async function enroll(request: HttpRequest): Promise<HttpResult> {
    const parsed = decodeEnrollRequest(await readBody(request));
    const outcome = await pairing.enroll(parsed);

    switch (outcome.status) {
      case 'approved':
        return json(
          200,
          approvedEnrollResponse({
            deviceId: outcome.deviceId,
            credential: outcome.credential,
            ...(outcome.wsUrl ? { wsUrl: outcome.wsUrl } : {}),
          }),
        );
      case 'pending':
        return json(200, pendingEnrollResponse(outcome));
      case 'denied':
        // 2xx on purpose: the node reads `status` from the body, and a 4xx would
        // be classified as a rejected *code* rather than a refused device.
        return json(200, deniedEnrollResponse());
      case 'rejected': {
        const rejection = enrollRejection(outcome.code, outcome.message);
        return json(rejection.status, rejection.body);
      }
    }
  }

  async function enrollStatus(request: HttpRequest): Promise<HttpResult> {
    const parsed = decodeEnrollStatusRequest(await readBody(request));
    const outcome = await pairing.enrollStatus(parsed);

    switch (outcome.status) {
      case 'approved':
        return json(
          200,
          approvedEnrollResponse({
            deviceId: outcome.deviceId,
            credential: outcome.credential,
            ...(outcome.wsUrl ? { wsUrl: outcome.wsUrl } : {}),
          }),
        );
      case 'pending':
        return json(200, pendingEnrollResponse(outcome));
      case 'denied':
        return json(200, deniedEnrollResponse());
      case 'rejected': {
        const rejection = enrollRejection(outcome.code, outcome.message);
        return json(rejection.status, rejection.body);
      }
    }
  }

  return {
    /** Handles the node-facing REST surface: `POST /enroll` and `POST /enroll/status`. */
    async handle(request: HttpRequest): Promise<HttpResult> {
      const path = pathnameOf(request.url);
      const isEnrollStatus = path.endsWith('/enroll/status');
      const isEnroll = path.endsWith('/enroll');
      if (!isEnroll && !isEnrollStatus) return json(404, { error: 'not_found' });

      if (request.method.toUpperCase() !== 'POST') {
        return { ...json(405, { error: 'method_not_allowed' }), headers: { allow: 'POST' } };
      }

      try {
        return isEnrollStatus ? await enrollStatus(request) : await enroll(request);
      } catch (error) {
        if (error instanceof DecodeError) {
          return json(400, { error: 'invalid_request', message: error.message });
        }
        context.logger.log('error', 'enrolment failed', { error: String(error) });
        return json(500, { error: 'internal', message: 'enrolment failed' });
      }
    },
  };
}

export type HttpRouter = ReturnType<typeof httpRouter>;

/**
 * Wraps the router as a fetch-style handler. The Response constructor is passed
 * in rather than reached for globally, which keeps the core free of platform
 * globals while still giving fetch-native adapters a one-line integration.
 */
export function toFetchHandler<TResponse>(
  router: HttpRouter,
  makeResponse: (
    body: string,
    init: { status: number; headers: Record<string, string> },
  ) => TResponse,
): (request: HttpRequest) => Promise<TResponse> {
  return async (request) => {
    const result = await router.handle(request);
    return makeResponse(result.body, { status: result.status, headers: result.headers });
  };
}
