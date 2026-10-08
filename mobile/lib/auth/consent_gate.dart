import 'package:flutter/material.dart';

import '../scan/scan_service.dart';
import 'about_tool_screen.dart';
import 'auth_service.dart';
import 'consent_screen.dart';
import 'login_screen.dart';

typedef AuthenticatedContentBuilder = Widget Function(
  BuildContext context,
  VoidCallback onConsentRequired,
);

class ConsentGate extends StatefulWidget {
  const ConsentGate({
    super.key,
    required this.authenticatedContentBuilder,
    this.scanService,
  });

  final AuthenticatedContentBuilder authenticatedContentBuilder;
  final ScanService? scanService;

  @override
  State<ConsentGate> createState() => _ConsentGateState();
}

enum _ConsentStatus { checking, consent, declined, accepted, failed }

class _ConsentGateState extends State<ConsentGate> {
  final AuthService _authService = AuthService();
  late final ScanService _scanService = widget.scanService ?? ScanService();
  _ConsentStatus _status = _ConsentStatus.checking;
  String? _error;

  @override
  void initState() {
    super.initState();
    _checkConsent();
  }

  Future<void> _checkConsent() async {
    setState(() {
      _status = _ConsentStatus.checking;
      _error = null;
    });
    try {
      final result = await _scanService.checkConsent();
      if (!mounted) return;
      setState(() {
        _status = result['consented'] == true
            ? _ConsentStatus.accepted
            : _ConsentStatus.consent;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = _ConsentStatus.failed;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _acceptConsent() async {
    setState(() => _error = null);
    try {
      await _scanService.recordConsent(true);
      if (mounted) setState(() => _status = _ConsentStatus.accepted);
    } catch (error) {
      if (mounted) {
        setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
      }
    }
  }

  void _requireConsent() {
    setState(() {
      _status = _ConsentStatus.consent;
      _error = 'Please accept the consent screen first';
    });
  }

  Future<void> _signOut() async {
    await _authService.logout();
    if (!mounted) return;
    Navigator.of(context, rootNavigator: true).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    switch (_status) {
      case _ConsentStatus.checking:
        return const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        );
      case _ConsentStatus.failed:
        return Scaffold(
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(_error ?? 'Could not check consent.'),
                  const SizedBox(height: 12),
                  FilledButton(onPressed: _checkConsent, child: const Text('Retry')),
                ],
              ),
            ),
          ),
        );
      case _ConsentStatus.declined:
        return _ConsentDeclinedScreen(
          onReviewAgain: () => setState(() {
            _status = _ConsentStatus.consent;
            _error = null;
          }),
          onSignOut: _signOut,
        );
      case _ConsentStatus.consent:
        return ConsentScreen(
          errorMessage: _error,
          onAccept: _acceptConsent,
          onDecline: () => setState(() {
            _status = _ConsentStatus.declined;
            _error = null;
          }),
          onAbout: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const AboutToolScreen()),
          ),
        );
      case _ConsentStatus.accepted:
        return widget.authenticatedContentBuilder(context, _requireConsent);
    }
  }
}

class _ConsentDeclinedScreen extends StatelessWidget {
  const _ConsentDeclinedScreen({
    required this.onReviewAgain,
    required this.onSignOut,
  });

  final VoidCallback onReviewAgain;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Consent required')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Lucidia cannot be used without your consent. No consent decision has been saved.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: onReviewAgain,
                child: const Text('Review again'),
              ),
              TextButton(onPressed: onSignOut, child: const Text('Sign out')),
            ],
          ),
        ),
      ),
    );
  }
}
