import 'package:flutter/material.dart';

class ScanSubmissionErrorDialog extends StatelessWidget {
  const ScanSubmissionErrorDialog({
    super.key,
    required this.message,
    this.title = 'Submission failed',
  });

  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(title),
      content: Text(message.trim().isEmpty
          ? 'The server could not process your request. Please try again.'
          : message),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('OK'),
        ),
      ],
    );
  }
}
