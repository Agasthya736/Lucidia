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
          content: Text('Clinician sign-off is mandatory before downloading the report PDF.'),
          backgroundColor: LucidiaColors.warning,
        ),
      );
      return;
    }

    try {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Downloading report PDF...')),
      );

      final bytes = await _scanService.downloadReportPdf(widget.scanId);

      final studyId = (widget.scanId.length >= 8)
          ? widget.scanId.substring(0, 8).toUpperCase()
          : widget.scanId.toUpperCase();
      final filename = 'Lucidia_Report_$studyId.pdf';

      final savedPath = await savePdfAndOpen(bytes, filename);

      if (!mounted) return;
      ScaffoldMessenger.of(context).clearSnackBars();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Report downloaded: $savedPath'),
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Radiology Report'),
        actions: [
          if (_scan != null) ...[
            IconButton(
              icon: Icon(
                _scan!['status'] == 'FINALIZED' ? Icons.picture_as_pdf : Icons.lock_outline,
                color: _scan!['status'] == 'FINALIZED' ? LucidiaColors.teal : LucidiaColors.textSecondary,
              ),
              tooltip: _scan!['status'] == 'FINALIZED' ? 'Download PDF Report' : 'Locked — Sign-off required',
              onPressed: _scan!['status'] == 'FINALIZED' ? _downloadPdf : _openSignOffDialog,
            ),
          ],
        ],
      ),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
            : _error != null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(_error!, style: const TextStyle(color: LucidiaColors.error)),
                    ),
                  )
                : _buildReportBody(),
      ),
    );
  }

  Widget _buildReportBody() {
    final scan = _scan!;
    final report = scan['report'] as Map<String, dynamic>? ?? {};
    final triage = scan['triage'] as Map<String, dynamic>? ?? {};
    final verification = scan['verification'] as Map<String, dynamic>? ?? {};

    final isFinalized = scan['status'] == 'FINALIZED';
    final isEscalated = scan['isEscalated'] == true;
    final int sliceCount = scan['sliceCount'] ?? 1;

    final String severity = (report['severity'] ?? 'ROUTINE').toString().toUpperCase();
    final String impression = report['impression'] ?? 'No significant abnormality detected.';
    final String recommendations = report['recommendations'] ?? 'Routine clinical follow-up as indicated.';
    final double confidence = (report['detectorConfidence'] as num?)?.toDouble() ??
        ((triage['overallConfidence'] as num?)?.toDouble() ?? 0.88);

    final List<dynamic> clinicalFindings = report['clinicalFindings'] as List<dynamic>? ?? [];

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 40),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 1. Mandatory Clinical Disclaimer Banner
          _buildDisclaimerBanner(),
          const SizedBox(height: 16),

          // 2. Clinical Study Header
          _buildStudyHeader(scan, isEscalated, sliceCount),
          const SizedBox(height: 16),

          // 3. Categorical Urgency / Severity Banner
          _buildSeverityBanner(severity),
          const SizedBox(height: 20),

          // 4. Suspected Abnormality / Impression Card
          _buildImpressionCard(impression, severity),
          const SizedBox(height: 20),

          // 5. Interactive Series Slice Viewer with Bounding Box Overlays
          _buildSliceViewerSection(sliceCount, triage),
          const SizedBox(height: 20),

          // 6. Region-by-Region Clinical Findings
          _buildClinicalFindingsSection(clinicalFindings),
          const SizedBox(height: 20),

          // 7. Triage Detector Evidence
          _buildTriageEvidenceSection(triage, confidence, sliceCount),
          const SizedBox(height: 20),

          // 8. Grounding Verification
          _buildVerificationSection(verification),
          const SizedBox(height: 20),

          // 9. Recommendations
          _buildRecommendationsCard(recommendations),
          const SizedBox(height: 28),

          // 10. Clinician Sign-Off Card / Action Bar
          _buildSignOffSection(isFinalized, scan),
        ],
      ),
    );
  }

  Widget _buildDisclaimerBanner() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: LucidiaColors.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const [
          Icon(Icons.gavel_outlined, color: LucidiaColors.textSecondary, size: 18),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'CLINICAL DECISION SUPPORT NOTICE: Lucidia is a second-read documentation tool. '
              'It does NOT provide autonomous diagnostic decisions. Documented clinician review is mandatory.',
              style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStudyHeader(Map<String, dynamic> scan, bool isEscalated, int sliceCount) {
    final String studyId = (scan['id'] as String? ?? '00000000').substring(0, 8).toUpperCase();
    final String filename = scan['imageFilename'] ?? 'CT_Study';

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'STUDY: $studyId',
                style: const TextStyle(
                  color: LucidiaColors.textPrimary,
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
              ),
              _pill(
                isEscalated ? 'Grounded Synthesis' : 'Clean Auto-Summary',
                isEscalated ? LucidiaColors.violet : LucidiaColors.teal,
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Divider(height: 1),
          const SizedBox(height: 8),
          Row(
            children: [
              _metaChip(Icons.filter_none, '$sliceCount Slices in Series'),
              const SizedBox(width: 12),
              _metaChip(Icons.medical_information_outlined, 'CT Chest (Axial)'),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Primary file: $filename',
            style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildSeverityBanner(String severity) {
    final (color, icon, label, description) = switch (severity) {
      'URGENT' => (
          LucidiaColors.error,
          Icons.notification_important,
          'URGENT TRIAGE FLAG',
          'Significant lesion or mass identified. Prioritize immediate review.',
        ),
      'FOLLOW_UP_RECOMMENDED' => (
          LucidiaColors.warning,
          Icons.schedule,
          'FOLLOW-UP RECOMMENDED',
          'Focal finding identified requiring interval radiological monitoring.',
        ),
      _ => (
          LucidiaColors.teal,
          Icons.check_circle_outline,
          'ROUTINE / CLEAN STUDY',
          'Visualized parenchyma within normal limits. Standard clinical follow-up.',
        ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [color.withValues(alpha: 0.20), LucidiaColors.surfaceElevated],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withValues(alpha: 0.5), width: 1.5),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(color: color.withValues(alpha: 0.2), shape: BoxShape.circle),
            child: Icon(icon, color: color, size: 22),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 14, letterSpacing: 0.5),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildImpressionCard(String impression, String severity) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionLabel('SUSPECTED ABNORMALITY / IMPRESSION'),
          const SizedBox(height: 10),
          Text(
            impression,
            style: const TextStyle(
              color: LucidiaColors.textPrimary,
              fontSize: 15,
              fontWeight: FontWeight.w600,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSliceViewerSection(int totalSlices, Map<String, dynamic> triage) {
    final sliceBytes = _sliceImages[_selectedSliceIndex];
    final List<dynamic> sliceFindingsList = triage['sliceFindings'] as List<dynamic>? ?? [];

    // Extract lesions for current slice
    List<dynamic> currentSliceLesions = [];
    if (_selectedSliceIndex < sliceFindingsList.length) {
      final sliceData = sliceFindingsList[_selectedSliceIndex] as Map<String, dynamic>?;
      if (sliceData != null) {
        currentSliceLesions = sliceData['lesions'] as List<dynamic>? ?? [];
      }
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _sectionLabel('SERIES SLICE VIEWER & DETECTOR OVERLAYS'),
              Text(
                'Slice ${_selectedSliceIndex + 1} of $totalSlices',
                style: const TextStyle(color: LucidiaColors.teal, fontWeight: FontWeight.bold, fontSize: 12),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Slice image with CustomPaint overlay
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: AspectRatio(
              aspectRatio: 1.0,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Container(color: Colors.black),
                  if (_sliceLoading)
                    const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
                  else if (sliceBytes != null)
                    Image.memory(sliceBytes, fit: BoxFit.contain)
                  else
                    const Center(
                      child: Text('Slice preview unavailable', style: TextStyle(color: LucidiaColors.textSecondary)),
                    ),

                  // Draw detector bounding boxes on current slice
                  if (currentSliceLesions.isNotEmpty)
                    CustomPaint(
                      painter: _DetectorOverlayPainter(currentSliceLesions),
                    ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 12),
          // Slice Scrubber Navigation
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              OutlinedButton.icon(
                onPressed: _selectedSliceIndex > 0 ? () => _loadSliceImage(_selectedSliceIndex - 1) : null,
                icon: const Icon(Icons.chevron_left, size: 16),
                label: const Text('Prev Slice'),
                style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8)),
              ),
              if (currentSliceLesions.isNotEmpty)
                _pill('${currentSliceLesions.length} lesion(s) detected', LucidiaColors.error)
              else
                _pill('Normal slice', LucidiaColors.teal),
              OutlinedButton.icon(
                onPressed: _selectedSliceIndex < totalSlices - 1 ? () => _loadSliceImage(_selectedSliceIndex + 1) : null,
                icon: const Icon(Icons.chevron_right, size: 16),
                label: const Text('Next Slice'),
                style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8)),
              ),
            ],
          ),

          // Horizontal thumbnail picker for series
          if (totalSlices > 1) ...[
            const SizedBox(height: 12),
            SizedBox(
              height: 48,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: totalSlices,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final isSelected = index == _selectedSliceIndex;
                  return InkWell(
                    onTap: () => _loadSliceImage(index),
                    borderRadius: BorderRadius.circular(8),
                    child: Container(
                      width: 48,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: isSelected ? LucidiaColors.teal : LucidiaColors.surface,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isSelected ? LucidiaColors.teal : LucidiaColors.border,
                          width: isSelected ? 2 : 1,
                        ),
                      ),
                      child: Text(
                        '#${index + 1}',
                        style: TextStyle(
                          color: isSelected ? Colors.white : LucidiaColors.textSecondary,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildClinicalFindingsSection(List<dynamic> findings) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionLabel('CLINICAL FINDINGS (BY ANATOMICAL REGION)'),
          const SizedBox(height: 12),
          if (findings.isEmpty)
            const Text(
              'Thoracic anatomy within normal visual limits. No suspicious mass, nodule, or infiltration.',
              style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13, height: 1.4),
            )
          else
            ...findings.map((f) {
              final region = f['region'] ?? 'Thorax';
              final status = (f['status'] ?? 'NORMAL').toString().toUpperCase();
              final desc = f['description'] ?? '';
              final isAbnormal = status == 'ABNORMAL';

              return Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: LucidiaColors.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isAbnormal ? LucidiaColors.error.withValues(alpha: 0.4) : LucidiaColors.border,
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          region,
                          style: const TextStyle(
                            color: LucidiaColors.textPrimary,
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                        _pill(status, isAbnormal ? LucidiaColors.error : LucidiaColors.teal),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      desc,
                      style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 13, height: 1.4),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildTriageEvidenceSection(Map<String, dynamic> triage, double confidence, int sliceCount) {
    final int abnormalSlices = triage['abnormalSlicesCount'] ?? 0;
    final String summary = triage['summaryEvidence'] ?? 'Pixel detector analysis complete.';

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _sectionLabel('TRIAGE DETECTOR EVIDENCE'),
              _pill('${(confidence * 100).toStringAsFixed(0)}% confidence', LucidiaColors.violet),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              _metricBlock('Confidence', '${(confidence * 100).toStringAsFixed(0)}%'),
              const SizedBox(width: 12),
              _metricBlock('Abnormal Slices', '$abnormalSlices / $sliceCount'),
              const SizedBox(width: 12),
              _metricBlock('Triage Status', triage['overallStatus'] ?? 'NORMAL'),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            summary,
            style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
          ),
        ],
      ),
    );
  }

  Widget _buildVerificationSection(Map<String, dynamic> verification) {
    final bool verified = verification['verified'] == true;
    final List<dynamic> flags = verification['flags'] as List<dynamic>? ?? [];
    final String notes = verification['notes'] ?? 'Verification check completed.';

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                verified ? Icons.check_circle : Icons.warning_amber_rounded,
                color: verified ? LucidiaColors.teal : LucidiaColors.error,
                size: 20,
              ),
              const SizedBox(width: 10),
              Text(
                verified ? 'GROUNDING VERIFICATION PASSED' : 'GROUNDING ISSUES FLAGGED',
                style: TextStyle(
                  color: verified ? LucidiaColors.teal : LucidiaColors.error,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            notes,
            style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
          ),
          if (flags.isNotEmpty) ...[
            const SizedBox(height: 8),
            ...flags.map(
              (f) => Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('• ', style: TextStyle(color: LucidiaColors.error)),
                    Expanded(
                      child: Text(
                        f.toString(),
                        style: const TextStyle(color: LucidiaColors.error, fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildRecommendationsCard(String recommendations) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionLabel('CLINICAL RECOMMENDATIONS'),
          const SizedBox(height: 10),
          Text(
            recommendations,
            style: const TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, height: 1.4),
          ),
        ],
      ),
    );
  }

  Widget _buildSignOffSection(bool isFinalized, Map<String, dynamic> scan) {
    if (isFinalized) {
      final String reviewer = scan['reviewerName'] ?? 'Attending Clinician';
      final String creds = scan['reviewerCredentials'] ?? 'MD, Radiologist';
      final String date = scan['finalizedAt'] ?? '';

      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: LucidiaColors.teal.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.4), width: 1.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: const [
                Icon(Icons.verified, color: LucidiaColors.teal, size: 22),
                SizedBox(width: 8),
                Text(
                  'CLINICIAN SIGN-OFF COMPLETED',
                  style: TextStyle(color: LucidiaColors.teal, fontWeight: FontWeight.bold, fontSize: 13),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Reviewed by $reviewer ($creds)',
              style: const TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.w600),
            ),
            if (date.isNotEmpty)
              Text('Finalized on: $date', style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 11)),
            const SizedBox(height: 14),
            ElevatedButton.icon(
              onPressed: _downloadPdf,
              icon: const Icon(Icons.picture_as_pdf),
              label: const Text('Export Official Signed PDF'),
              style: ElevatedButton.styleFrom(backgroundColor: LucidiaColors.teal),
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
            children: const [
              Icon(Icons.pending_actions, color: LucidiaColors.warning, size: 22),
              SizedBox(width: 8),
              Text(
                'MANDATORY CLINICIAN SIGN-OFF REQUIRED',
                style: TextStyle(color: LucidiaColors.warning, fontWeight: FontWeight.bold, fontSize: 13),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'In compliance with diagnostic decision support safety standards, report export and sharing are locked '
            'until certified by a licensed clinician.',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _openSignOffDialog,
                  icon: const Icon(Icons.draw_outlined),
                  label: const Text('Complete Clinician Sign-Off'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _sectionLabel(String label) {
    return Text(
      label,
      style: const TextStyle(
        color: LucidiaColors.textSecondary,
        fontSize: 11,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.8,
      ),
    );
  }

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

  Widget _metaChip(IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: LucidiaColors.textSecondary),
        const SizedBox(width: 4),
        Text(text, style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12)),
      ],
    );
  }

  Widget _metricBlock(String label, String value) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: LucidiaColors.surface,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 10)),
            const SizedBox(height: 2),
            Text(value, style: const TextStyle(color: LucidiaColors.textPrimary, fontWeight: FontWeight.bold, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}

/// Custom painter rendering bounding boxes and confidence tags directly on the slice canvas
class _DetectorOverlayPainter extends CustomPainter {
  final List<dynamic> lesions;

  _DetectorOverlayPainter(this.lesions);

  @override
  void paint(Canvas canvas, Size size) {
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
        final x1 = (rawBox[0] / 1000.0) * size.width;
        final y1 = (rawBox[1] / 1000.0) * size.height;
        final x2 = (rawBox[2] / 1000.0) * size.width;
        final y2 = (rawBox[3] / 1000.0) * size.height;

        final rect = Rect.fromLTRB(x1, y1, x2, y2);
        canvas.drawRect(rect, fillPaint);
        canvas.drawRect(rect, strokePaint);

        // Tag label
        final label = '${l['lesionType'] ?? "Lesion"} · ${(l['confidence'] * 100).toStringAsFixed(0)}%';
        final textSpan = TextSpan(
          text: label,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 10,
            fontWeight: FontWeight.bold,
            backgroundColor: LucidiaColors.error,
          ),
        );
        final tp = TextPainter(text: textSpan, textDirection: TextDirection.ltr);
        tp.layout();
        tp.paint(canvas, Offset(x1, Math.max(0, y1 - 14)));
      }
    }
  }

  @override
  bool shouldRepaint(covariant _DetectorOverlayPainter oldDelegate) {
    return oldDelegate.lesions != lesions;
  }
}

class Math {
  static double max(double a, double b) => a > b ? a : b;
}