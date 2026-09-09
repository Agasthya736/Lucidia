import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../shared/theme.dart';
import 'scan_service.dart';
import 'pipeline_status_screen.dart';
import '../auth/profile_screen.dart';

class ScanCaptureScreen extends StatefulWidget {
  const ScanCaptureScreen({super.key});

  @override
  State<ScanCaptureScreen> createState() => _ScanCaptureScreenState();
}

class _ScanCaptureScreenState extends State<ScanCaptureScreen> {
  final ScanService _scanService = ScanService();
  final List<PlatformFile> _selectedFiles = [];
  bool _submitting = false;
  String? _error;
  Map<String, dynamic>? _quota;

  @override
  void initState() {
    super.initState();
    _loadQuota();
  }

  Future<void> _loadQuota() async {
    try {
      final q = await _scanService.getQuota();
      if (mounted) setState(() => _quota = q);
    } catch (_) {
      // Non-fatal
    }
  }

  Future<void> _pickFiles() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.image,
      allowMultiple: true,
      withData: true,
    );
    if (result == null || result.files.isEmpty) return;

    setState(() {
      for (final f in result.files) {
        if (f.bytes != null && !_selectedFiles.any((existing) => existing.name == f.name)) {
          _selectedFiles.add(f);
        }
      }
      _error = null;
    });
  }

  void _removeSlice(int index) {
    setState(() {
      _selectedFiles.removeAt(index);
    });
  }

  void _moveSlice(int index, int delta) {
    final newIndex = index + delta;
    if (newIndex < 0 || newIndex >= _selectedFiles.length) return;
    setState(() {
      final file = _selectedFiles.removeAt(index);
      _selectedFiles.insert(newIndex, file);
    });
  }

  Future<void> _submit() async {
    if (_selectedFiles.isEmpty) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final result = await _scanService.submitScanSeries(_selectedFiles);
      if (!mounted) return;
      Navigator.of(context, rootNavigator: true).push(
        MaterialPageRoute(builder: (_) => PipelineStatusScreen(scanId: result['id'])),
      );
    } catch (e) {
      if (!mounted) return;
      final err = e.toString().replaceAll('Exception: ', '');
      setState(() => _error = err);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('New CT Study Series'),
        actions: [
          if (_selectedFiles.isNotEmpty)
            TextButton.icon(
              onPressed: _submitting ? null : () => setState(() => _selectedFiles.clear()),
              icon: const Icon(Icons.clear_all, size: 18),
              label: const Text('Clear'),
            ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildQuotaBanner(),
              const SizedBox(height: 16),
              _buildPickerCard(),
              if (_selectedFiles.isNotEmpty) ...[
                const SizedBox(height: 20),
                _buildSeriesHeader(),
                const SizedBox(height: 12),
                _buildThumbnailStrip(),
              ],
              if (_error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: LucidiaColors.error.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: LucidiaColors.error.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: LucidiaColors.error, size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(_error!, style: const TextStyle(color: LucidiaColors.error, fontSize: 13)),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: (_selectedFiles.isNotEmpty && !_submitting) ? _submit : null,
                child: _submitting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Text(_selectedFiles.isEmpty
                        ? 'Select CT Slices to Analyze'
                        : 'Run Triage on ${_selectedFiles.length} ${_selectedFiles.length == 1 ? "Slice" : "Slices"}'),
              ),
              const SizedBox(height: 16),
              const Center(
                child: Text(
                  'Supports axial CT scans in JPEG, PNG, or WebP. Multi-slice series recommended.',
                  style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildQuotaBanner() {
    if (_quota == null) return const SizedBox.shrink();
    final bool isByok = _quota!['byokActive'] == true;
    final int remaining = _quota!['remainingThisMonth'] ?? 0;
    final int total = _quota!['monthlyLimit'] ?? 20;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: isByok
            ? LucidiaColors.teal.withValues(alpha: 0.12)
            : (remaining <= 3 ? LucidiaColors.warning.withValues(alpha: 0.14) : LucidiaColors.surfaceElevated),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isByok
              ? LucidiaColors.teal.withValues(alpha: 0.3)
              : (remaining <= 3 ? LucidiaColors.warning.withValues(alpha: 0.4) : LucidiaColors.border),
        ),
      ),
      child: Row(
        children: [
          Icon(
            isByok ? Icons.vpn_key : Icons.speed,
            size: 18,
            color: isByok ? LucidiaColors.teal : (remaining <= 3 ? LucidiaColors.warning : LucidiaColors.textSecondary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              isByok
                  ? 'Institution BYOK Active — Unlimited scans'
                  : 'Free Tier: $remaining of $total monthly scans remaining',
              style: TextStyle(
                color: isByok
                    ? LucidiaColors.teal
                    : (remaining <= 3 ? LucidiaColors.warning : LucidiaColors.textPrimary),
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          if (!isByok)
            GestureDetector(
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const ProfileScreen()),
              ),
              child: const Text(
                'BYOK Settings \u2192',
                style: TextStyle(color: LucidiaColors.teal, fontSize: 12, fontWeight: FontWeight.bold),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildPickerCard() {
    return GestureDetector(
      onTap: _submitting ? null : _pickFiles,
      child: Container(
        height: _selectedFiles.isEmpty ? 220 : 120,
        decoration: BoxDecoration(
          color: LucidiaColors.surfaceElevated,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: _selectedFiles.isNotEmpty ? LucidiaColors.teal : LucidiaColors.border,
            width: _selectedFiles.isNotEmpty ? 1.5 : 1,
          ),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                _selectedFiles.isNotEmpty ? Icons.add_photo_alternate : Icons.cloud_upload_outlined,
                size: _selectedFiles.isEmpty ? 44 : 32,
                color: _selectedFiles.isNotEmpty ? LucidiaColors.teal : LucidiaColors.textSecondary,
              ),
              const SizedBox(height: 10),
              Text(
                _selectedFiles.isEmpty
                    ? 'Tap to select CT series slices'
                    : 'Tap to add more slices to series',
                style: const TextStyle(
                  color: LucidiaColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _selectedFiles.isEmpty
                    ? 'Select multiple slice images (axial series)'
                    : '${_selectedFiles.length} slices currently in series',
                style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSeriesHeader() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            const Icon(Icons.layers_outlined, size: 18, color: LucidiaColors.teal),
            const SizedBox(width: 8),
            Text(
              'Selected Series (${_selectedFiles.length} slices)',
              style: const TextStyle(
                color: LucidiaColors.textPrimary,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const Text(
          'Reorder using arrows',
          style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
        ),
      ],
    );
  }

  Widget _buildThumbnailStrip() {
    return SizedBox(
      height: 140,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _selectedFiles.length,
        separatorBuilder: (_, __) => const SizedBox(width: 12),
        itemBuilder: (context, index) {
          final file = _selectedFiles[index];
          return Container(
            width: 110,
            decoration: BoxDecoration(
              color: LucidiaColors.surfaceElevated,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: LucidiaColors.border),
            ),
            child: Stack(
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Expanded(
                      child: ClipRRect(
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
                        child: file.bytes != null
                            ? Image.memory(file.bytes!, fit: BoxFit.cover)
                            : const Center(child: Icon(Icons.broken_image, size: 24)),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                      color: LucidiaColors.surface,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.arrow_back, size: 14),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                            onPressed: index > 0 ? () => _moveSlice(index, -1) : null,
                            color: LucidiaColors.textSecondary,
                          ),
                          Text(
                            '#${index + 1}',
                            style: const TextStyle(
                              color: LucidiaColors.teal,
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.arrow_forward, size: 14),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                            onPressed: index < _selectedFiles.length - 1 ? () => _moveSlice(index, 1) : null,
                            color: LucidiaColors.textSecondary,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                Positioned(
                  top: 4,
                  right: 4,
                  child: GestureDetector(
                    onTap: () => _removeSlice(index),
                    child: Container(
                      padding: const EdgeInsets.all(3),
                      decoration: const BoxDecoration(
                        color: Colors.black54,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.close, size: 14, color: Colors.white),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}