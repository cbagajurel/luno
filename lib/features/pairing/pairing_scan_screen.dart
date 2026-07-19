import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/bridge_providers.dart';
import '../../state/pairing_providers.dart';
import '../../ui/ui.dart';
import 'pairing_banner.dart';
import 'pairing_messages.dart';

/// Scans a Luno pairing QR code. The camera and the confirm step live here; the
/// payload itself is parsed by native, so the QR format has exactly one
/// implementation and Flutter stays a viewfinder.
///
/// Pops `true` once the backend has accepted the enrolment — as a credential or
/// as a wait for approval — and the caller leaves the pairing flow.
class PairingScanScreen extends ConsumerStatefulWidget {
  const PairingScanScreen({super.key});

  @override
  ConsumerState<PairingScanScreen> createState() => _PairingScanScreenState();
}

class _PairingScanScreenState extends ConsumerState<PairingScanScreen> {
  final _controller = MobileScannerController(
    formats: const [BarcodeFormat.qrCode],
  );

  /// Detection fires per frame; this keeps one scan from queueing a dozen enrols.
  bool _handling = false;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_handling) return;
    final raw = capture.barcodes
        .map((b) => b.rawValue)
        .firstWhere((v) => v != null && v.isNotEmpty, orElse: () => null);
    if (raw == null) return;

    setState(() {
      _handling = true;
      _error = null;
    });
    await _controller.stop();

    final parsed = await ref.read(bridgeProvider).parsePairingPayload(raw);
    if (!mounted) return;

    switch (parsed.status) {
      case PairingPayloadStatus.ok:
        await _confirmAndPair(raw, parsed.payload!);
      case PairingPayloadStatus.unsupportedVersion:
      case PairingPayloadStatus.malformed:
        _rejectScan(
          parsed.reason ?? 'That QR code is not a Luno pairing code.',
        );
    }
  }

  Future<void> _confirmAndPair(String raw, PairingPayloadInfo payload) async {
    final confirmed = await showLunoSheet<bool>(
      context: context,
      title: payload.label ?? 'Enrol with this backend?',
      subtitle: payload.backendUrl,
      builder: (sheetCtx) => _ConfirmScan(
        payload: payload,
        onConfirm: () => Navigator.of(sheetCtx).pop(true),
        onCancel: () => Navigator.of(sheetCtx).pop(false),
      ),
    );
    if (!mounted) return;
    if (confirmed != true) {
      _resumeScanning();
      return;
    }

    setState(() => _submitting = true);
    try {
      final result = await ref
          .read(pairingProvider.notifier)
          .pairFromPayload(raw, backendUrl: payload.backendUrl);
      if (!mounted) return;
      switch (result.outcome) {
        case PairingOutcome.success:
        case PairingOutcome.pending:
          Navigator.of(context).pop(true);
        case PairingOutcome.failure:
          _rejectScan(pairingFailureText(result));
      }
    } catch (e) {
      if (mounted) _rejectScan('$e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _rejectScan(String message) {
    setState(() => _error = message);
    _resumeScanning();
  }

  void _resumeScanning() {
    setState(() => _handling = false);
    unawaitedStart(_controller);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Scan pairing code'),
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            tooltip: 'Toggle torch',
            icon: const Icon(Icons.flashlight_on_rounded),
            onPressed: () => _controller.toggleTorch(),
          ),
        ],
      ),
      extendBodyBehindAppBar: true,
      body: Stack(
        fit: StackFit.expand,
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: _onDetect,
            errorBuilder: (context, error) => _CameraUnavailable(
              error: error,
              onOpenSettings: () => ref.read(bridgeProvider).openAppSettings(),
            ),
          ),
          const _ViewfinderFrame(),
          if (_submitting)
            const ColoredBox(
              color: Colors.black54,
              child: Center(child: CircularProgressIndicator()),
            ),
          Positioned(
            left: LunoSpacing.lg,
            right: LunoSpacing.lg,
            bottom: LunoSpacing.xl,
            child: SafeArea(
              child: _error == null
                  ? const _ScanHint()
                  : PairingErrorBanner(_error!),
            ),
          ),
        ],
      ),
    );
  }
}

/// Restarting is best-effort: if the camera is already gone the viewfinder's
/// own error state is what the operator needs to see, not a thrown future.
void unawaitedStart(MobileScannerController controller) {
  controller.start().catchError((_) {});
}

class _ScanHint extends StatelessWidget {
  const _ScanHint();

  @override
  Widget build(BuildContext context) {
    return Text(
      'Point the camera at the pairing QR code shown by your backend.',
      textAlign: TextAlign.center,
      style: context.text.bodyMedium?.copyWith(color: Colors.white70),
    );
  }
}

class _ViewfinderFrame extends StatelessWidget {
  const _ViewfinderFrame();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 240,
        height: 240,
        decoration: BoxDecoration(
          border: Border.all(color: Colors.white70, width: 2),
          borderRadius: BorderRadius.circular(24),
        ),
      ),
    );
  }
}

class _ConfirmScan extends StatelessWidget {
  const _ConfirmScan({
    required this.payload,
    required this.onConfirm,
    required this.onCancel,
  });

  final PairingPayloadInfo payload;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (payload.pin != null) ...[
          Row(
            children: [
              Icon(
                Icons.verified_user_rounded,
                size: 18,
                color: context.semantic.positive.color,
              ),
              const SizedBox(width: LunoSpacing.xs),
              Expanded(
                child: Text(
                  'This code pins the backend certificate.',
                  style: context.text.bodySmall?.copyWith(
                    color: context.scheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: LunoSpacing.md),
        ],
        LunoButton(
          label: 'Enrol this device',
          icon: Icons.check_rounded,
          expand: true,
          onPressed: onConfirm,
        ),
        const SizedBox(height: LunoSpacing.sm),
        LunoButton(
          label: 'Cancel',
          variant: LunoButtonVariant.text,
          expand: true,
          onPressed: onCancel,
        ),
      ],
    );
  }
}

class _CameraUnavailable extends StatelessWidget {
  const _CameraUnavailable({required this.error, required this.onOpenSettings});

  final MobileScannerException error;
  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    final denied = error.errorCode == MobileScannerErrorCode.permissionDenied;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(LunoSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.no_photography_rounded,
              size: 40,
              color: Colors.white70,
            ),
            const SizedBox(height: LunoSpacing.md),
            Text(
              denied
                  ? 'Luno needs camera access to scan a pairing code.'
                  : 'The camera is unavailable on this device.',
              textAlign: TextAlign.center,
              style: context.text.bodyMedium?.copyWith(color: Colors.white70),
            ),
            if (denied) ...[
              const SizedBox(height: LunoSpacing.lg),
              LunoButton(
                label: 'Open settings',
                icon: Icons.settings_rounded,
                variant: LunoButtonVariant.tonal,
                onPressed: onOpenSettings,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
