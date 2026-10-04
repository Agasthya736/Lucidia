import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../shared/theme.dart';
import '../shared/pdf_saver.dart';
import 'scan_service.dart';
import 'clinician_sign_off_dialog.dart';

class ReportViewerScreen extends StatefulWidget {
  final String scanId;
  const ReportViewerScreen({super.key, required this.scanId});

  @override
  State<ReportViewerScreen> createState() => _ReportViewerScreenState();
}

class _ReportViewerScreenState extends State<ReportViewerScreen> {
  final ScanService _scanService = ScanService();
  Map<String, dynamic>? _scan;
  bool _loading = true;
  String? _error;

  int _selectedSliceIndex = 0;
  final Map<int, Uint8List> _sliceImages = {};
  bool _sliceLoading = false;
  bool _showTelemetry = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final scan = await _scanService.getScan(widget.scanId);
      setState(() {
        _scan = scan;
        _loading = false;
      });
      _loadSliceImage(0);
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _loadSliceImage(int sliceIndex) async {
    if (_sliceImages.containsKey(sliceIndex)) {
      setState(() => _selectedSliceIndex = sliceIndex);
      return;
    }
    setState(() {
      _selectedSliceIndex = sliceIndex;
      _sliceLoading = true;
    });
    try {
      final bytes = await _scanService.fetchSliceImage(widget.scanId, sliceIndex);
      if (mounted) {
        setState(() {
          _sliceImages[sliceIndex] = bytes;
          _sliceLoading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _sliceLoading = false);
    }
  }

  void _openSignOffDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => ClinicianSignOffDialog(
        scanId: widget.scanId,
        onSignedOff: (updated) {
          setState(() => _scan = updated);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Clinician sign-off recorded. Report export unlocked.')),
          );
        },
      ),
    );
  }

  Future<void> _downloadPdf() async {
    if (_scan?['status'] != 'FINALIZED') {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Clinician sign-off is required before exporting the PDF.'),
          backgroundColor: LucidiaColors.warning,
        ),
      );
      return;
    }
    try {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Preparing report PDF...')),
      );
      final bytes = await _scanService.downloadReportPdf(widget.scanId);
      final studyId = (widget.scanId.length >= 8)
          ? widget.scanId.substring(0, 8).toUpperCase()
          : widget.scanId.toUpperCase();
      final savedPath = await savePdfAndOpen(bytes, 'Lucidia_Report_$studyId.pdf');
      if (!mounted) return;
      ScaffoldMessenger.of(context).clearSnackBars();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Saved: $savedPath'),
          duration: const Duration(seconds: 4),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).clearSnackBars();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('PDF error: ${e.toString().replaceAll("Exception: ", "")}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = LucidiaTheme.isDarkMode;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(
          'Scan Report',
          style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold),
        ),
        backgroundColor: AppColors.surface,
        iconTheme: IconThemeData(color: AppColors.textPrimary),
        actions: [
          IconButton(
            tooltip: isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode',
            icon: Icon(
              isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
              color: LucidiaColors.teal,
            ),
            onPressed: () => setState(() => LucidiaTheme.toggleTheme()),
          ),
          if (_scan != null)
            IconButton(
              icon: Icon(
                _scan!['status'] == 'FINALIZED'
                    ? Icons.picture_as_pdf_outlined
                    : Icons.lock_outline,
                color: _scan!['status'] == 'FINALIZED'
                    ? LucidiaColors.teal
                    : AppColors.textSecondary,
              ),
              tooltip: _scan!['status'] == 'FINALIZED'
                  ? 'Download PDF Report'
                  : 'Sign-off required to export',
              onPressed: _scan!['status'] == 'FINALIZED' ? _downloadPdf : _openSignOffDialog,
            ),
        ],
      ),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
            : _error != null
                ? _buildError()
                : _buildReport(),
      ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off_outlined, color: AppColors.textSecondary, size: 48),
            const SizedBox(height: 16),
            Text(
              'Could not load report',
              style: TextStyle(color: AppColors.textPrimary, fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _error!,
              style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: () {
                setState(() {
                  _loading = true;
                  _error = null;
                });
                _load();
              },
              icon: const Icon(Icons.refresh),
              label: const Text('Try Again'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReport() {
    final scan = _scan!;
    final report = scan['report'] as Map<String, dynamic>? ?? {};
    final triage = scan['triage'] as Map<String, dynamic>? ?? {};
    final verification = scan['verification'] as Map<String, dynamic>? ?? {};

    final isFinalized = scan['status'] == 'FINALIZED';
    final String modality = (scan['modality'] ?? 'CT_SERIES').toString().toUpperCase();
    final bool isExternal = modality == 'EXTERNAL_PHOTO';
    final int sliceCount = scan['sliceCount'] ?? 1;

    final String severity = (report['severity'] ?? 'ROUTINE').toString().toUpperCase();
    final String impression = report['impression'] ?? 'No significant abnormality detected.';
    final String patientFriendly = report['patientFriendlySummary'] as String? ?? '';
    final String recommendations =
        report['recommendations'] as String? ?? 'Routine clinical follow-up as indicated.';
    final List<dynamic> clinicalFindings = report['clinicalFindings'] as List<dynamic>? ?? [];
    final double confidence = (report['detectorConfidence'] as num?)?.toDouble() ??
        ((triage['overallConfidence'] as num?)?.toDouble() ?? 0.88);
    final String generatedBy = report['generatedBy'] as String? ?? 'Lucidia AI Pipeline';

    final level = UrgencyLevel.fromString(severity);

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 1. Urgency Banner
          _buildUrgencyBanner(level, isExternal),
          const SizedBox(height: 14),

          // 2. Summary Card
          _buildSummaryCard(scan, impression, isExternal, sliceCount),
          const SizedBox(height: 14),

          // 3. Plain-English Explanation
          if (patientFriendly.isNotEmpty) ...[
            _buildPatientExplanationCard(patientFriendly),
            const SizedBox(height: 14),
          ],

          // 4. What To Do Next
          _buildNextStepsCard(recommendations),
          const SizedBox(height: 14),

          // 5. Image Viewer
          _buildImageViewer(sliceCount, triage, isExternal),
          const SizedBox(height: 14),

          // 6. Findings by Region
          if (clinicalFindings.isNotEmpty) ...[
            _buildFindingsCard(clinicalFindings, isExternal),
            const SizedBox(height: 14),
          ],

          // 7. Technical Details (collapsed)
          _buildTechDetailsAccordion(triage, verification, confidence, sliceCount, generatedBy),
          const SizedBox(height: 20),

          // 8. Sign-Off / Export
          _buildSignOffCard(isFinalized, scan),
        ],
      ),
    );
  }

  // â”€â”€ 1. Urgency Banner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildUrgencyBanner(UrgencyLevel level, bool isExternal) {
    String headline;
    String subtext;

    switch (level) {
      case UrgencyLevel.urgent:
        headline = 'âš ï¸  Significant Finding Detected';
        subtext = isExternal
            ? 'This photograph shows a feature that requires prompt clinical evaluation. Please consult a specialist as soon as possible.'
            : 'This scan shows a finding that needs immediate clinical attention. Do not delay follow-up.';
        break;
      case UrgencyLevel.followUpRecommended:
        headline = 'ðŸ””  Follow-Up Recommended';
        subtext = isExternal
            ? 'A feature was noted that should be monitored or reviewed by a clinician at your next appointment.'
            : 'A finding was noted on this scan. Interval monitoring or specialist correlation is advised.';
        break;
      case UrgencyLevel.routine:
        headline = 'âœ…  No Significant Concerns';
        subtext = isExternal
            ? 'The image does not show features requiring urgent action. Continue routine monitoring.'
            : 'The scan appears within normal limits. Standard clinical follow-up is recommended.';
        break;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: level.backgroundColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: level.color.withValues(alpha: 0.5), width: 1.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            headline,
            style: TextStyle(
              color: level.color,
              fontSize: 16,
              fontWeight: FontWeight.bold,
              height: 1.3,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            subtext,
            style: TextStyle(color: AppColors.textPrimary, fontSize: 13, height: 1.5),
          ),
        ],
      ),
    );
  }

  // â”€â”€ 2. Summary Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildSummaryCard(
    Map<String, dynamic> scan,
    String impression,
    bool isExternal,
    int sliceCount,
  ) {
    final String id = scan['id'] as String? ?? '--------';
    final String studyId = id.length >= 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Study #$studyId',
                style: TextStyle(
                  color: LucidiaColors.teal,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                ),
              ),
              _pill(
                isExternal ? 'External Photograph' : 'CT Study  Â·  $sliceCount slices',
                isExternal ? LucidiaColors.violet : LucidiaColors.teal,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Divider(height: 1, color: AppColors.border),
          const SizedBox(height: 14),
          Text(
            'What the AI found:',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 11,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.6,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            impression,
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 15,
              fontWeight: FontWeight.w600,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceElevated,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline, color: AppColors.textSecondary, size: 15),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'This is an AI-generated second-read tool. It does not replace a licensed clinician\'s diagnosis.',
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 11, height: 1.4),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // â”€â”€ 3. Plain-English Explanation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildPatientExplanationCard(String explanation) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LucidiaColors.teal.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.25)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.record_voice_over_outlined, color: LucidiaColors.teal, size: 18),
              const SizedBox(width: 8),
              Text(
                'IN PLAIN ENGLISH',
                style: TextStyle(
                  color: LucidiaColors.teal,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            explanation,
            style: TextStyle(color: AppColors.textPrimary, fontSize: 14, height: 1.55),
          ),
        ],
      ),
    );
  }

  // â”€â”€ 4. What To Do Next â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildNextStepsCard(String recommendations) {
    final lines = recommendations
        .split(RegExp(r'(?<=[.!?])\s+'))
        .where((s) => s.trim().isNotEmpty)
        .toList();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.task_alt_outlined, color: LucidiaColors.teal, size: 20),
              const SizedBox(width: 8),
              Text(
                'WHAT TO DO NEXT',
                style: TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          if (lines.length <= 1)
            Text(
              recommendations.trim(),
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 14,
                height: 1.55,
                fontWeight: FontWeight.w500,
              ),
            )
          else
            ...lines.map(
              (line) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 7,
                      height: 7,
                      margin: const EdgeInsets.only(top: 6, right: 10),
                      decoration: BoxDecoration(
                        color: LucidiaColors.teal,
                        shape: BoxShape.circle,
                      ),
                    ),
                    Expanded(
                      child: Text(
                        line.trim(),
                        style: TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 13,
                          height: 1.55,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  // â”€â”€ 5. Image Viewer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildImageViewer(int totalSlices, Map<String, dynamic> triage, bool isExternal) {
    final sliceBytes = _sliceImages[_selectedSliceIndex];
    final List<dynamic> sliceFindingsList = triage['sliceFindings'] as List<dynamic>? ?? [];
    List<dynamic> lesions = [];
    if (_selectedSliceIndex < sliceFindingsList.length) {
      final sliceData = sliceFindingsList[_selectedSliceIndex] as Map<String, dynamic>?;
      lesions = sliceData?['lesions'] as List<dynamic>? ?? [];
    }

    double? imageAspectRatio;
    if (_selectedSliceIndex < sliceFindingsList.length) {
      final sliceData = sliceFindingsList[_selectedSliceIndex] as Map<String, dynamic>?;
      final metrics = sliceData?['metrics'] as Map<String, dynamic>?;
      final res = metrics?['resolution'] as String?;
      if (res != null && res.contains('x')) {
        final parts = res.split('x');
        final w = double.tryParse(parts[0]);
        final h = double.tryParse(parts[1]);
        if (w != null && h != null && h > 0) {
          imageAspectRatio = w / h;
        }
      }
    }

    final double viewerAspect = isExternal ? (imageAspectRatio ?? 0.75).clamp(0.65, 1.4) : 1.0;

    return Container(
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  isExternal ? 'CLINICAL PHOTOGRAPH' : 'CT SLICE VIEWER',
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                  ),
                ),
                if (!isExternal)
                  Text(
                    'Slice ${_selectedSliceIndex + 1} of $totalSlices',
                    style: TextStyle(color: LucidiaColors.teal, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: const BorderRadius.vertical(bottom: Radius.circular(14)),
            child: AspectRatio(
              aspectRatio: viewerAspect,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Container(color: Colors.black),
                  if (_sliceLoading)
                    const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
                  else if (sliceBytes != null)
                    Image.memory(sliceBytes, fit: BoxFit.contain)
                  else
                    Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            isExternal
                                ? Icons.image_not_supported_outlined
                                : Icons.layers_outlined,
                            color: AppColors.textSecondary,
                            size: 40,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Image preview unavailable',
                            style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  if (lesions.isNotEmpty)
                    CustomPaint(
                      painter: _OverlayPainter(lesions, imageAspectRatio: imageAspectRatio),
                    ),
                ],
              ),
            ),
          ),
          if (!isExternal && totalSlices > 1) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back_ios_new, size: 16),
                    color: LucidiaColors.teal,
                    onPressed: _selectedSliceIndex > 0
                        ? () => _loadSliceImage(_selectedSliceIndex - 1)
                        : null,
                  ),
                  Expanded(
                    child: Slider(
                      value: _selectedSliceIndex.toDouble(),
                      min: 0,
                      max: (totalSlices - 1).toDouble(),
                      divisions: totalSlices > 1 ? totalSlices - 1 : 1,
                      activeColor: LucidiaColors.teal,
                      onChanged: (v) => _loadSliceImage(v.round()),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.arrow_forward_ios, size: 16),
                    color: LucidiaColors.teal,
                    onPressed: _selectedSliceIndex < totalSlices - 1
                        ? () => _loadSliceImage(_selectedSliceIndex + 1)
                        : null,
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  // â”€â”€ 6. Findings by Region â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildFindingsCard(List<dynamic> findings, bool isExternal) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isExternal
                    ? Icons.photo_size_select_actual_outlined
                    : Icons.analytics_outlined,
                color: LucidiaColors.teal,
                size: 18,
              ),
              const SizedBox(width: 8),
              Text(
                isExternal ? 'AREA-BY-AREA FINDINGS' : 'ORGAN-BY-ORGAN FINDINGS',
                style: TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          ...findings.map((f) {
            final region = f['region'] as String? ?? 'Region';
            final status = (f['status'] ?? 'NORMAL').toString().toUpperCase();
            final desc = f['description'] as String? ?? '';
            final isAbnormal = status == 'ABNORMAL';
            final Color rowColor = isAbnormal ? LucidiaColors.error : LucidiaColors.success;

            return Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                  color: rowColor.withValues(alpha: isAbnormal ? 0.4 : 0.2),
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    margin: const EdgeInsets.only(top: 2, right: 12),
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: rowColor.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      isAbnormal ? Icons.warning_amber_rounded : Icons.check_circle_outline,
                      color: rowColor,
                      size: 14,
                    ),
                  ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Text(
                                region,
                                style: TextStyle(
                                  color: AppColors.textPrimary,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 13,
                                ),
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: rowColor.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: Text(
                                isAbnormal ? 'Abnormal' : 'Normal',
                                style: TextStyle(
                                  color: rowColor,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (desc.isNotEmpty) ...[
                          const SizedBox(height: 6),
                          Text(
                            desc,
                            style: TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: 13,
                              height: 1.45,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  // â”€â”€ 7. Technical Details (collapsed accordion) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildTechDetailsAccordion(
    Map<String, dynamic> triage,
    Map<String, dynamic> verification,
    double confidence,
    int sliceCount,
    String generatedBy,
  ) {
    final int abnormalSlices = triage['abnormalSlicesCount'] ?? 0;
    final String triageSummary = triage['summaryEvidence'] as String? ?? 'Pixel detector analysis complete.';
    final bool verified = verification['verified'] == true;
    final List<dynamic> flags = verification['flags'] as List<dynamic>? ?? [];

    return Container(
      decoration: lucidiaCardDecoration(),
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: _showTelemetry,
          onExpansionChanged: (v) => setState(() => _showTelemetry = v),
          tilePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          title: Row(
            children: [
              Icon(Icons.biotech_outlined, color: LucidiaColors.teal, size: 18),
              const SizedBox(width: 10),
              Text(
                'Technical AI Details',
                style: TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          subtitle: Text(
            'Tap to ${_showTelemetry ? "hide" : "show"} confidence metrics',
            style: TextStyle(color: AppColors.textSecondary, fontSize: 11),
          ),
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Divider(height: 1, color: AppColors.border),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      _metricTile('AI Confidence', '${(confidence * 100).toStringAsFixed(0)}%'),
                      const SizedBox(width: 8),
                      _metricTile('Abnormal Slices', '$abnormalSlices / $sliceCount'),
                      const SizedBox(width: 8),
                      _metricTile('Status', triage['overallStatus'] as String? ?? 'NORMAL'),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    triageSummary,
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 12, height: 1.4),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Icon(
                        verified ? Icons.verified_outlined : Icons.warning_amber_rounded,
                        color: verified ? LucidiaColors.teal : LucidiaColors.error,
                        size: 15,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        verified ? 'Grounding verification passed' : 'Grounding issues flagged',
                        style: TextStyle(
                          color: verified ? LucidiaColors.teal : LucidiaColors.error,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  if (flags.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    ...flags.map((f) => Text(
                          'â€¢ $f',
                          style: TextStyle(color: LucidiaColors.error, fontSize: 11),
                        )),
                  ],
                  const SizedBox(height: 8),
                  Text(
                    'Generated by: $generatedBy',
                    style: TextStyle(color: AppColors.textSecondary, fontSize: 11),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // â”€â”€ 8. Sign-Off / Export â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _buildSignOffCard(bool isFinalized, Map<String, dynamic> scan) {
    if (isFinalized) {
      final String reviewer = scan['reviewerName'] as String? ?? 'Attending Clinician';
      final String creds = scan['reviewerCredentials'] as String? ?? 'MD';
      final String date = scan['finalizedAt'] as String? ?? '';

      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: LucidiaColors.teal.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.4), width: 1.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.verified_outlined, color: LucidiaColors.teal, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Clinician Sign-Off Completed',
                  style: TextStyle(
                    color: LucidiaColors.teal,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Reviewed by $reviewer â€” $creds',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
            if (date.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(
                'Finalized: $date',
                style: TextStyle(color: AppColors.textSecondary, fontSize: 11),
              ),
            ],
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: _downloadPdf,
                icon: const Icon(Icons.picture_as_pdf_outlined),
                label: const Text('Export Signed PDF Report'),
                style: ElevatedButton.styleFrom(backgroundColor: LucidiaColors.teal),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: LucidiaColors.warning.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: LucidiaColors.warning.withValues(alpha: 0.4), width: 1.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.draw_outlined, color: LucidiaColors.warning, size: 22),
              const SizedBox(width: 8),
              Text(
                'Clinician Sign-Off Required',
                style: TextStyle(
                  color: LucidiaColors.warning,
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Before this report can be exported or shared, it must be reviewed and signed off by a licensed clinician. '
            'This is a regulatory safety requirement.',
            style: TextStyle(color: AppColors.textSecondary, fontSize: 13, height: 1.45),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _openSignOffDialog,
              icon: const Icon(Icons.edit_note_outlined),
              label: const Text('Complete Clinician Sign-Off'),
              style: ElevatedButton.styleFrom(backgroundColor: LucidiaColors.warning),
            ),
          ),
        ],
      ),
    );
  }

  // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  Widget _pill(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Text(
        text,
        style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w600),
      ),
    );
  }

  Widget _metricTile(String label, String value) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: AppColors.surfaceElevated,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: TextStyle(color: AppColors.textSecondary, fontSize: 10)),
            const SizedBox(height: 3),
            Text(
              value,
              style: TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 13,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// â”€â”€ Bounding box overlay painter â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
class _OverlayPainter extends CustomPainter {
  final List<dynamic> lesions;
  final double? imageAspectRatio;

  _OverlayPainter(this.lesions, {this.imageAspectRatio});

  @override
  void paint(Canvas canvas, Size size) {
    if (lesions.isEmpty) return;

    Rect destRect = Rect.fromLTWH(0, 0, size.width, size.height);
    if (imageAspectRatio != null && imageAspectRatio! > 0) {
      final containerAspect = size.width / size.height;
      if (imageAspectRatio! < containerAspect) {
        // Image is narrower than container (pillarboxed)
        final drawWidth = size.height * imageAspectRatio!;
        final offsetX = (size.width - drawWidth) / 2.0;
        destRect = Rect.fromLTWH(offsetX, 0, drawWidth, size.height);
      } else {
        // Image is wider than container (letterboxed)
        final drawHeight = size.width / imageAspectRatio!;
        final offsetY = (size.height - drawHeight) / 2.0;
        destRect = Rect.fromLTWH(0, offsetY, size.width, drawHeight);
      }
    }

    final strokePaint = Paint()
      ..color = LucidiaColors.error
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5;

    final fillPaint = Paint()
      ..color = LucidiaColors.error.withValues(alpha: 0.15)
      ..style = PaintingStyle.fill;

    for (final l in lesions) {
      final rawBox = l['boundingBox'];
      if (rawBox is List && rawBox.length == 4) {
        final x1 = destRect.left + (rawBox[0] / 1000.0) * destRect.width;
        final y1 = destRect.top + (rawBox[1] / 1000.0) * destRect.height;
        final x2 = destRect.left + (rawBox[2] / 1000.0) * destRect.width;
        final y2 = destRect.top + (rawBox[3] / 1000.0) * destRect.height;

        final rect = Rect.fromLTRB(x1, y1, x2, y2);
        canvas.drawRect(rect, fillPaint);
        canvas.drawRect(rect, strokePaint);

        final rawConf = l['confidence'];
        final confStr = rawConf is num ? '${(rawConf * 100).toStringAsFixed(0)}%' : '';
        final labelText =
            '${l['lesionType'] ?? "Finding"}${confStr.isNotEmpty ? " - $confStr" : ""}';

        final textSpan = TextSpan(
          text: labelText,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 10,
            fontWeight: FontWeight.bold,
            backgroundColor: LucidiaColors.error,
          ),
        );
        final tp = TextPainter(text: textSpan, textDirection: TextDirection.ltr);
        tp.layout(maxWidth: size.width - 20);
        final labelY = (y1 - 16) < 4.0 ? (y2 + 4.0).clamp(4.0, size.height - 20) : (y1 - 16);
        tp.paint(canvas, Offset(x1.clamp(4.0, size.width - tp.width - 4), labelY));
      }
    }
  }

  @override
  bool shouldRepaint(covariant _OverlayPainter old) =>
      old.lesions != lesions || old.imageAspectRatio != imageAspectRatio;
}

