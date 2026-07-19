import '../../bridge/generated/luno_api.g.dart';

/// Operator-facing copy for a rejected enrolment.
///
/// The codes come from the backend's pairing taxonomy — the node never decides
/// that a session expired or ran out of enrolments, it only renders the verdict.
/// A code this build doesn't know falls through to the backend's own message, so
/// a server can add reasons without waiting for an app release.
String pairingFailureText(PairingResult result) {
  final fallback = result.message ?? 'Pairing failed.';
  return switch (result.errorCode) {
    'invalid_code' =>
      'That pairing code was not recognised. Check it and try again.',
    'session_expired' =>
      'This pairing code has expired. Generate a fresh one from your backend.',
    'session_exhausted' =>
      'This pairing code has already enrolled as many devices as it allows.',
    'session_revoked' => 'This pairing code was revoked.',
    'already_enrolled' =>
      'This device is already registered. Remove it on the backend, or use a code that permits replacement.',
    'approval_denied' => 'This device was not approved.',
    'policy_rejected' => 'The backend refused this device.',
    'not_secure' => 'The backend URL must use https.',
    'network' =>
      "Couldn't reach the backend. Check the URL and your connection.",
    'server' => 'The backend returned an unexpected response.',
    'internal' => 'Something went wrong on this device.',
    _ => fallback,
  };
}
