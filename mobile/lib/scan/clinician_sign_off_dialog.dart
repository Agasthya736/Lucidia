import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'scan_service.dart';

/// Dialog that lets users optionally add personal notes before marking
/// a report as reviewed, then unlocking the PDF export.
/// Name and credentials are entirely optional for this educational app.
class ReviewConfirmDialog extends StatefulWidget {
  final String scanId;
  final Function(Map<String, dynamic> updatedScan) onConfirmed;

  const ReviewConfirmDialog({
    super.key,
    required this.scanId,
    required this.onConfirmed,
  });

  @override
  State<ReviewConfirmDialog> createState() => _ReviewConfirmDialogState();
}

class _ReviewConfirmDialogState extends State<ReviewConfirmDialog> {
  final ScanService _scanService = ScanService();

  final _nameController = TextEditingController();
  final _notesController = TextEditingController();

  bool _confirmedRead = false;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_confirmedRead) {
      setState(() => _error = 'Please confirm that you have read the disclaimer above.');
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final updated = await _scanService.finalizeScan(
        id: widget.scanId,
        reviewerName: _nameController.text.trim(),
        reviewerCredentials: '',
        notes: _notesController.text.trim(),
      );

      if (!mounted) return;
      widget.onConfirmed(updated);
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString().replaceAll('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: LucidiaColors.surfaceElevated,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      title: Row(
        children: [
          const Icon(Icons.check_circle_outline, color: LucidiaColors.teal, size: 24),
          const SizedBox(width: 10),
          Text(
            'Mark as Reviewed',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: LucidiaColors.textPrimary),
          ),
        ],
      ),
      content: SizedBox(
        width: 480,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Prominent disclaimer
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: LucidiaColors.warning.withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: LucidiaColors.warning.withValues(alpha: 0.4)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.info_outline, color: LucidiaColors.warning, size: 16),
                        const SizedBox(width: 6),
                        Text(
                          'Educational Use Only',
                          style: TextStyle(
                            color: LucidiaColors.warning,
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'This AI report is for informational and educational purposes only. '
                      'It is NOT a medical diagnosis. Always consult a qualified doctor '
                      'or healthcare professional before making any health decisions.',
                      style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Optional name field
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Your Name (optional)',
                  hintText: 'e.g. John Doe',
                  prefixIcon: Icon(Icons.person_outline),
                ),
              ),
              const SizedBox(height: 12),

              // Optional personal notes
              TextField(
                controller: _notesController,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Personal Notes (optional)',
                  hintText: 'Questions to ask your doctor, follow-up reminders, etc.',
                  prefixIcon: Icon(Icons.edit_note),
                ),
              ),
              const SizedBox(height: 16),

              // Confirmation checkbox
              InkWell(
                onTap: () => setState(() => _confirmedRead = !_confirmedRead),
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Checkbox(
                        value: _confirmedRead,
                        activeColor: LucidiaColors.teal,
                        onChanged: (val) => setState(() => _confirmedRead = val ?? false),
                      ),
                      Expanded(
                        child: Text(
                          'I understand that this is an AI-generated report for informational '
                          'purposes only and is not a substitute for professional medical advice.',
                          style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13, height: 1.3),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: const TextStyle(color: LucidiaColors.error, fontSize: 12)),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _submitting ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _submitting ? null : _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: LucidiaColors.teal,
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          ),
          child: _submitting
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Text('Confirm & Download Report'),
        ),
      ],
    );
  }
}

/// Backwards-compatible alias — existing callers that use ClinicianSignOffDialog still compile.
typedef ClinicianSignOffDialog = ReviewConfirmDialog;
