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
  final TextEditingController _notesController = TextEditingController();

  String _selectedModality = 'CT_SERIES'; // 'CT_SERIES' or 'EXTERNAL_PHOTO'
  bool _submitting = false;
  String? _error;
  Map<String, dynamic>? _quota;

  @override
  void initState() {
    super.initState();
    _loadQuota();
  }

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
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
      allowMultiple: _selectedModality == 'CT_SERIES',
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
      final result = await _scanService.submitScanSeries(
        _selectedFiles,
        modality: _selectedModality,
        clinicalNotes: _notesController.text.trim().isNotEmpty ? _notesController.text.trim() : null,
      );
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
    final isExternal = _selectedModality == 'EXTERNAL_PHOTO';

    return Scaffold(
      appBar: AppBar(
        title: Text(isExternal ? 'New External Clinical Photo' : 'New CT Study Series'),
        actions: [
          IconButton(
            tooltip: 'Toggle Theme',
            icon: Icon(
              LucidiaTheme.isDarkMode ? Icons.light_mode : Icons.dark_mode,
              color: LucidiaColors.teal,
            ),
            onPressed: () => setState(() => LucidiaTheme.toggleTheme()),
          ),
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
              _buildModalitySelector(),
              const SizedBox(height: 16),
              _buildResponsibleAiCard(isExternal),
              const SizedBox(height: 16),
              _buildPickerCard(isExternal),
              if (_selectedFiles.isNotEmpty) ...[
                const SizedBox(height: 20),
                _buildSeriesHeader(isExternal),
                const SizedBox(height: 12),
                _buildThumbnailStrip(),
              ],
              const SizedBox(height: 16),
              _buildClinicalNotesField(),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: LucidiaColors.error.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: LucidiaColors.error.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.shield_outlined, color: LucidiaColors.error, size: 22),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Responsible AI & Validation Alert',
                              style: TextStyle(color: LucidiaColors.error, fontSize: 13, fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 4),
                            Text(_error!, style: const TextStyle(color: LucidiaColors.error, fontSize: 12, height: 1.4)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 24),
              _buildSubmitButton(isExternal),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildModalitySelector() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LucidiaColors.border),
      ),
      child: Row(
        children: [
          Expanded(
            child: _modalityTab(
              id: 'CT_SERIES',
              label: 'Radiology CT Scan',
              icon: Icons.layers_outlined,
            ),
          ),
          Expanded(
            child: _modalityTab(
              id: 'EXTERNAL_PHOTO',
              label: 'Photo Analysis',
              icon: Icons.camera_alt_outlined,
            ),
          ),
        ],
      ),
    );
  }

  Widget _modalityTab({required String id, required String label, required IconData icon}) {
    final selected = _selectedModality == id;
    return GestureDetector(
      onTap: _submitting
          ? null
          : () {
              if (_selectedModality != id) {
                setState(() {
                  _selectedModality = id;
                  _selectedFiles.clear();
                  _error = null;
                });
              }
            },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: selected ? LucidiaColors.surface : Colors.transparent,
          borderRadius: BorderRadius.circular(9),
          border: selected ? Border.all(color: LucidiaColors.teal.withValues(alpha: 0.4)) : null,
          boxShadow: selected
              ? [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, 2))]
              : null,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 16, color: selected ? LucidiaColors.teal : LucidiaColors.textSecondary),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                color: selected ? LucidiaColors.textPrimary : LucidiaColors.textSecondary,
                fontSize: 12,
                fontWeight: selected ? FontWeight.bold : FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResponsibleAiCard(bool isExternal) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LucidiaColors.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.verified_user_outlined, color: LucidiaColors.teal, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  isExternal ? 'Responsible AI: External Photo Guidelines' : 'Responsible AI: Radiology Series Guidelines',
                  style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  isExternal
                      ? 'Upload photographs of visible external areas (skin, wounds, rashes, swellings). '
                        'Non-clinical images (memes, pets, screenshots) are automatically rejected by our safety filter.'
                      : 'Upload CT scan slice images for AI-assisted analysis. '
                        'Results are for informational purposes only — always discuss findings with your doctor.',
                  style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11, height: 1.4),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuotaBanner() {
    final int remaining = _quota?['remainingThisMonth'] ?? 20;
    final bool isByok = _quota?['byokActive'] == true;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: isByok
            ? LucidiaColors.teal.withValues(alpha: 0.12)
            : (remaining <= 3 ? LucidiaColors.warning.withValues(alpha: 0.14) : LucidiaColors.surfaceElevated),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isByok
              ? LucidiaColors.teal.withValues(alpha: 0.3)
              : (remaining <= 3 ? LucidiaColors.warning.withValues(alpha: 0.4) : LucidiaColors.border),
        ),
      ),
      child: Row(
        children: [
          Icon(
            isByok ? Icons.key_outlined : Icons.speed_outlined,
            size: 16,
            color: isByok ? LucidiaColors.teal : (remaining <= 3 ? LucidiaColors.warning : LucidiaColors.textSecondary),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              isByok
                  ? 'BYOK active \u2014 unlimited processing'
                  : '$remaining free study evaluations remaining this month',
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

  Widget _buildPickerCard(bool isExternal) {
    return GestureDetector(
      onTap: _submitting ? null : _pickFiles,
      child: Container(
        height: _selectedFiles.isEmpty ? 180 : 110,
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
                isExternal
                    ? (_selectedFiles.isNotEmpty ? Icons.add_a_photo : Icons.camera_alt_outlined)
                    : (_selectedFiles.isNotEmpty ? Icons.add_photo_alternate : Icons.cloud_upload_outlined),
                size: _selectedFiles.isEmpty ? 40 : 28,
                color: _selectedFiles.isNotEmpty ? LucidiaColors.teal : LucidiaColors.textSecondary,
              ),
              const SizedBox(height: 10),
              Text(
                _selectedFiles.isEmpty
                    ? (isExternal ? 'Tap to choose clinical photograph' : 'Tap to select CT series slices')
                    : (isExternal ? 'Tap to replace clinical photograph' : 'Tap to add more slices to series'),
                style: TextStyle(
                  color: LucidiaColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _selectedFiles.isEmpty
                    ? (isExternal ? 'Select surface photo of lesion, wound, or swelling' : 'Select multiple slice images (axial series)')
                    : '${_selectedFiles.length} file(s) selected',
                style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSeriesHeader(bool isExternal) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Icon(isExternal ? Icons.photo_outlined : Icons.layers_outlined, size: 18, color: LucidiaColors.teal),
            const SizedBox(width: 8),
            Text(
              isExternal ? 'Selected Photograph' : 'Selected Series (${_selectedFiles.length} slices)',
              style: TextStyle(
                color: LucidiaColors.textPrimary,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        if (!isExternal)
          Text(
            'Reorder using arrows',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
          ),
      ],
    );
  }

  Widget _buildThumbnailStrip() {
    return SizedBox(
      height: 130,
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

  Widget _buildClinicalNotesField() {
    return TextField(
      controller: _notesController,
      maxLines: 2,
      style: const TextStyle(fontSize: 13),
      decoration: InputDecoration(
        labelText: 'Clinical Context / Anatomical Location (Optional)',
        hintText: _selectedModality == 'EXTERNAL_PHOTO'
            ? 'e.g., Left forearm, 3-week onset, mildly pruritic'
            : 'e.g., Routine screening, chronic dry cough, prior imaging comparison',
      ),
    );
  }

  Widget _buildSubmitButton(bool isExternal) {
    return ElevatedButton(
      onPressed: (_selectedFiles.isEmpty || _submitting) ? null : _submit,
      child: _submitting
          ? const SizedBox(
              height: 20,
              width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
            )
          : Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(isExternal ? Icons.document_scanner : Icons.play_arrow_rounded, size: 20),
                const SizedBox(width: 8),
                Text(
                  isExternal
                      ? 'Analyze Clinical Photograph'
                      : 'Run Responsible AI Analysis (${_selectedFiles.length} slices)',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
              ],
            ),
    );
  }
}