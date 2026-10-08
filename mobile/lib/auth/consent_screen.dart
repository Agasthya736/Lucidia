import 'package:flutter/material.dart';

import '../shared/privacy_policy_link.dart';

class ConsentScreen extends StatelessWidget {
  const ConsentScreen({
    super.key,
    required this.onAccept,
    required this.onDecline,
    required this.onAbout,
    this.errorMessage,
  });

  final VoidCallback onAccept;
  final VoidCallback onDecline;
  final VoidCallback onAbout;
  final String? errorMessage;

  static const _ink = Color(0xFF19324D);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Before you continue')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Not a medical device. Does not diagnose, treat, cure or prevent any condition. '
                'Consult a healthcare professional.',
                style: TextStyle(
                  color: Color(0xFF7A3514),
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'Please read and choose whether to continue using Lucidia.',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 16),
              const Text(
                '• Images you submit are sent to our server and to Google’s Gemini API for analysis.\n\n'
                '• Results are informational only and are not a substitute for professional medical advice.\n\n'
                '• You can delete your data at any time by deleting your account.',
                style: TextStyle(fontSize: 15, height: 1.5),
              ),
              if (errorMessage != null) ...[
                const SizedBox(height: 18),
                Text(
                  errorMessage!,
                  style: const TextStyle(
                    color: Color(0xFF9B2C2C),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
              const SizedBox(height: 24),
              FilledButton(
                style: FilledButton.styleFrom(backgroundColor: _ink),
                onPressed: onAccept,
                child: const Text('Accept and continue'),
              ),
              TextButton(
                style: TextButton.styleFrom(foregroundColor: _ink),
                onPressed: onDecline,
                child: const Text('Decline'),
              ),
              TextButton(
                style: TextButton.styleFrom(foregroundColor: _ink),
                onPressed: onAbout,
                child: const Text('About this tool'),
              ),
              TextButton(
                style: TextButton.styleFrom(foregroundColor: _ink),
                onPressed: () => openPrivacyPolicy(context),
                child: const Text('Privacy policy'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
