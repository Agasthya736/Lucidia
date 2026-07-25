import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../shared/theme.dart';
import 'scan_service.dart';
import 'pipeline_status_screen.dart';

class ScanCaptureScreen extends StatefulWidget {
  const ScanCaptureScreen({super.key});

  @override
  State<ScanCaptureScreen> createState() => _ScanCaptureScreenState();
}

class _ScanCaptureScreenState extends State<ScanCaptureScreen> {
  final ScanService _scanService = ScanService();
  PlatformFile? _selectedFile;
  bool _submitting = false;
  String? _error;

  Future<void> _pickFile() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.image, withData: true);
    if (result == null || result.files.single.bytes == null) return;
    setState(() {
      _selectedFile = result.files.single;
      _error = null;
    });
  }

  Future<void> _submit() async {
    if (_selectedFile == null) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final result = await _scanService.submitScan(_selectedFile!.bytes!, _selectedFile!.name);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => PipelineStatusScreen(scanId: result['id'])),
      );
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('New Scan')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              GestureDetector(
                onTap: _submitting ? null : _pickFile,
                child: Container(
                  height: 260,
                  decoration: BoxDecoration(
                    color: LucidiaColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: _selectedFile != null ? LucidiaColors.teal : LucidiaColors.border,
                      width: _selectedFile != null ? 1.5 : 1,
                    ),
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          _selectedFile != null ? Icons.check_circle : Icons.add_photo_alternate_outlined,
                          size: 48,
                          color: _selectedFile != null ? LucidiaColors.teal : LucidiaColors.textSecondary,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          _selectedFile?.name ?? 'Tap to select a CT scan image',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: LucidiaColors.textPrimary),
                        ),
                        if (_selectedFile == null) ...[
                          const SizedBox(height: 6),
                          const Text(
                            'JPEG, PNG, or WebP',
                            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!, style: const TextStyle(color: LucidiaColors.error, fontSize: 13)),
              ],
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: (_selectedFile != null && !_submitting) ? _submit : null,
                child: _submitting
                    ? const SizedBox(
                        height: 20, width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF04211F)),
                      )
                    : const Text('Submit for Analysis'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}