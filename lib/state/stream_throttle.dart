import 'dart:async';

extension ThrottleTrailing<T> on Stream<T> {
  /// Emits the first event immediately, then at most one event per [window],
  /// always delivering the most recent event seen during the window (trailing
  /// edge). Lets a continuously-firing source — e.g. a per-log-line revision
  /// tick that each drives a full re-fetch — settle into a bounded update rate
  /// without dropping the latest value.
  Stream<T> throttleTrailing(Duration window) {
    late StreamController<T> controller;
    StreamSubscription<T>? sub;
    Timer? timer;
    T? latest;
    var hasPending = false;

    void onWindowEnd() {
      if (hasPending) {
        hasPending = false;
        controller.add(latest as T);
        timer = Timer(window, onWindowEnd);
      } else {
        timer = null;
      }
    }

    controller = StreamController<T>(
      onListen: () {
        sub = listen(
          (event) {
            if (timer == null) {
              controller.add(event);
              timer = Timer(window, onWindowEnd);
            } else {
              latest = event;
              hasPending = true;
            }
          },
          onError: controller.addError,
          onDone: () {
            timer?.cancel();
            controller.close();
          },
        );
      },
      onCancel: () {
        timer?.cancel();
        final s = sub;
        sub = null;
        return s?.cancel();
      },
    );
    return controller.stream;
  }
}
