import 'dart:isolate';

import 'package:measure_flutter/attribute_value.dart';
import 'package:measure_flutter/src/events/custom_event_data.dart';
import 'package:measure_flutter/src/events/event_type.dart';
import 'package:measure_flutter/src/logger/logger.dart';
import 'package:measure_flutter/src/method_channel/signal_processor.dart';

final class CustomEventCollector {
  final Logger logger;
  final SignalProcessor signalProcessor;
  bool enabled = false;

  CustomEventCollector({
    required this.logger,
    required this.signalProcessor,
  });

  void register() {
    enabled = true;
  }

  void unregister() {
    enabled = false;
  }

  void trackCustomEvent(
    String name,
    DateTime? timestamp,
    Map<String, AttributeValue> attributes,
  ) {
    if (!enabled) {
      return;
    }
    signalProcessor.trackEvent(
      data: CustomEventData(name: name),
      type: EventType.custom,
      timestamp: timestamp ?? DateTime.now(),
      userDefinedAttrs: attributes,
      userTriggered: true,
      threadName: Isolate.current.debugName,
    );
  }
}
