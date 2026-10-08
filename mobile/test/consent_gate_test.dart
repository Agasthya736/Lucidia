import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lucidia_app/auth/consent_gate.dart';
import 'package:lucidia_app/scan/scan_service.dart';
import 'package:lucidia_app/scan/scan_submission_error_dialog.dart';

class _FakeScanService extends ScanService {
  _FakeScanService({this.consented = false});

  bool consented;
  bool? recordedConsent;

  @override
  Future<Map<String, dynamic>> checkConsent() async => {'consented': consented};

  @override
  Future<void> recordConsent(bool accepted) async {
    recordedConsent = accepted;
    consented = accepted;
  }
}

Widget _consentApp(_FakeScanService service) {
  return MaterialApp(
    home: ConsentGate(
      scanService: service,
      authenticatedContentBuilder: (context, onConsentRequired) => Scaffold(
        body: Column(
          children: [
            const Text('Dashboard'),
            const Text('New Scan'),
            TextButton(
              onPressed: onConsentRequired,
              child: const Text('Simulate consent error'),
            ),
          ],
        ),
      ),
    ),
  );
}

void main() {
  testWidgets('shows consent screen when consent has not been accepted', (
    tester,
  ) async {
    final service = _FakeScanService();
    await tester.pumpWidget(_consentApp(service));
    await tester.pumpAndSettle();

    expect(find.text('Before you continue'), findsOneWidget);
    expect(find.textContaining('Not a medical device.'), findsOneWidget);
    expect(find.text('New Scan'), findsNothing);
  });

  testWidgets('accepting records consent and continues', (tester) async {
    final service = _FakeScanService();
    await tester.pumpWidget(_consentApp(service));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Accept and continue'));
    await tester.pumpAndSettle();

    expect(service.recordedConsent, isTrue);
    expect(find.text('New Scan'), findsOneWidget);
  });

  testWidgets('declining blocks New Scan and allows review again', (
    tester,
  ) async {
    final service = _FakeScanService();
    await tester.pumpWidget(_consentApp(service));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Decline'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('cannot be used without your consent'),
      findsOneWidget,
    );
    expect(find.text('New Scan'), findsNothing);
    expect(service.recordedConsent, isNull);

    await tester.tap(find.text('Review again'));
    await tester.pumpAndSettle();
    expect(find.text('Before you continue'), findsOneWidget);
  });

  testWidgets('CONSENT_REQUIRED returns users to the consent screen', (
    tester,
  ) async {
    final service = _FakeScanService(consented: true);
    await tester.pumpWidget(_consentApp(service));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Simulate consent error'));
    await tester.pumpAndSettle();

    expect(find.text('Please accept the consent screen first'), findsOneWidget);
    expect(find.text('New Scan'), findsNothing);
  });

  testWidgets('submission error dialog displays the server message', (
    tester,
  ) async {
    const serverMessage = 'The uploaded image could not be analyzed.';
    final serverError = ScanService.parseSubmissionError(
      500,
      '{"message":"$serverMessage"}',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ScanSubmissionErrorDialog(message: serverError.message),
        ),
      ),
    );

    expect(find.text(serverMessage), findsOneWidget);
  });

  test('parses machine-readable missing-consent errors', () {
    final error = ScanService.parseSubmissionError(
      403,
      '{"code":"CONSENT_REQUIRED","message":"Please accept the consent screen first"}',
    );

    expect(error, isA<ConsentRequiredException>());
    expect(error.message, 'Please accept the consent screen first');
  });
}
