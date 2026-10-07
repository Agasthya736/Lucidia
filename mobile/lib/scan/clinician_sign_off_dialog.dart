import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'scan_service.dart';

class ClinicianSignOffDialog extends StatefulWidget {
  final String scanId;
  final Function(Map<String, dynamic> updatedScan) onSignedOff;

  const ClinicianSignOffDialog({
    super.key,
    required this.scanId,
    required this.onSignedOff,
  });

  @override
  State<ClinicianSignOffDialog> createState() => _ClinicianSignOffDialogState();
}

class _ClinicianSignOffDialogState extends State<ClinicianSignOffDialog> {
  final ScanService _scanService = ScanService();
  final _formKey = GlobalKey<FormState>();

  final _nameController = TextEditingController();
  final _credsController = TextEditingController();
  final _notesController = TextEditingController();

  bool _confirmedReview = false;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    _credsController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (!_confirmedReview) {
      setState(() => _error = 'Please check the confirmation box to certify clinical review.');
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
        reviewerCredentials: _credsController.text.trim(),
        notes: _notesController.text.trim(),
      );

      if (!mounted) return;
      widget.onSignedOff(updated);
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
          const Icon(Icons.verified_user, color: LucidiaColors.teal, size: 24),
          const SizedBox(width: 10),
          Text(
            'Report Sign-Off',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: LucidiaColors.textPrimary),
          ),
        ],
      ),
      content: SizedBox(
        width: 480,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LucidiaColors.teal.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.3)),
                  ),
                  child: Text(
                    'Lucidia is an assistive documentation tool, not an autonomous diagnostic system. '
                    'Documented sign-off is mandatory before this report can be exported or shared.',
                    style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
                  ),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: 'Full Name *',
                    hintText: 'e.g. Dr. Sarah Jenkins, MD',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                  validator: (val) => val == null || val.trim().isEmpty ? 'Name is required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _credsController,
                  decoration: const InputDecoration(
                    labelText: 'Credentials & Medical License *',
                    hintText: 'e.g. Board Certified Radiologist, Lic #49281',
                    prefixIcon: Icon(Icons.badge_outlined),
                  ),
                  validator: (val) => val == null || val.trim().isEmpty ? 'Credentials are required' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _notesController,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'Clinical Notes / Amendments (Optional)',
                    hintText: 'Additional clinical correlation or recommendations...',
                    prefixIcon: Icon(Icons.edit_note),
                  ),
                ),
                const SizedBox(height: 16),
                InkWell(
                  onTap: () => setState(() => _confirmedReview = !_confirmedReview),
                  borderRadius: BorderRadius.circular(8),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Checkbox(
                          value: _confirmedReview,
                          activeColor: LucidiaColors.teal,
                          onChanged: (val) => setState(() => _confirmedReview = val ?? false),
                        ),
                        Expanded(
                          child: Text(
                            'I confirm that I have personally reviewed this CT series, evaluated the findings, and approved this report.',
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
              : const Text('Sign Off & Unlock Export'),
        ),
      ],
    );
  }
}
