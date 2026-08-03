import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'scan_service.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

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
  bool _finalizing = false;
  String? _error;

  Uint8List? _imageBytes;
  bool _imageLoading = true;
  double? _imageAspectRatio;
  Size? _imagePixelSize;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final scan = await _scanService.getScan(widget.scanId);
      setState(() => _scan = scan);
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _loading = false);
    }
    _loadImage();
  }

  Future<void> _loadImage() async {
    try {
      final bytes = await _scanService.fetchImageBytes(widget.scanId);
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      final ratio = frame.image.width / frame.image.height;

      if (mounted) {
        setState(() {
          _imageBytes = bytes;
          _imageAspectRatio = ratio;
          _imagePixelSize = Size(frame.image.width.toDouble(), frame.image.height.toDouble());
        });
      }
    } catch (_) {
      // Non-fatal - image display is a bonus, not required for the report to work
    } finally {
      if (mounted) setState(() => _imageLoading = false);
    }
  }

  Future<void> _downloadPdf() async {
    try {
      const storage = FlutterSecureStorage();
      final token = await storage.read(key: 'jwt_token');

      if (token == null || token.isEmpty) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Session expired. Please log in again.')),
        );
        return;
      }

      final encodedToken = Uri.encodeComponent(token);
      final url = Uri.parse(
        'http://localhost:8080/api/scans/${widget.scanId}/report.pdf?access_token=$encodedToken',
      );

      if (await canLaunchUrl(url)) {
        await launchUrl(url, mode: LaunchMode.externalApplication, webOnlyWindowName: '_blank');
      } else {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not open PDF URL')),
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error downloading PDF: $e')),
      );
    }
  }

  Future<void> _finalize() async {
    setState(() => _finalizing = true);
    try {
      final updated = await _scanService.finalizeScan(widget.scanId);
      setState(() => _scan = updated);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Report signed off')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _finalizing = false);
    }
  }

  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: LucidiaColors.surfaceElevated,
        title: const Text('Delete Report'),
        content: const Text(
          'This will permanently delete this report. This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: LucidiaColors.error),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) _delete();
  }

  Future<void> _delete() async {
    try {
      await _scanService.deleteScan(widget.scanId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Report deleted')),
      );
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Delete failed: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Report'),
        actions: [
          if (_scan != null) ...[
            IconButton(
              icon: const Icon(Icons.picture_as_pdf_outlined),
              tooltip: 'Download PDF',
              onPressed: _downloadPdf,
            ),
            IconButton(
              icon: const Icon(Icons.delete_outline),
              tooltip: 'Delete Report',
              onPressed: _confirmDelete,
            ),
            const SizedBox(width: 4),
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
                : _buildContent(),
      ),
    );
  }

  Widget _buildContent() {
    final scan = _scan!;
    final verified = scan['verification']?['verified'] == true;
    final hasVerification = scan['verification'] != null;

    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 100),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              _statusHero(scan),
              const SizedBox(height: 20),
              _scanImageCard(scan),
              const SizedBox(height: 28),

              // ---- Pipeline timeline ----
              if (scan['visionA'] != null)
                _TimelineNode(
                  icon: Icons.visibility_outlined,
                  color: LucidiaColors.violet,
                  isFirst: true,
                  isLast: false,
                  child: _timelineCard(
                    title: 'Vision A · Gemini',
                    color: LucidiaColors.violet,
                    child: _agentBody(scan['visionA']),
                  ),
                ),
              if (scan['visionB'] != null)
                _TimelineNode(
                  icon: Icons.visibility_outlined,
                  color: LucidiaColors.violet,
                  isFirst: false,
                  isLast: false,
                  child: _timelineCard(
                    title: 'Vision B · Ollama',
                    color: LucidiaColors.violet,
                    child: _agentBody(scan['visionB']),
                  ),
                ),
              if (scan['arbitration'] != null)
                _TimelineNode(
                  icon: scan['arbitration']['agree'] == true
                      ? Icons.handshake_outlined
                      : Icons.compare_arrows,
                  color: scan['arbitration']['agree'] == true
                      ? LucidiaColors.teal
                      : LucidiaColors.violet,
                  isFirst: false,
                  isLast: false,
                  child: _arbitrationCard(scan['arbitration']),
                ),
              if (scan['report'] != null)
                _TimelineNode(
                  icon: Icons.description_outlined,
                  color: LucidiaColors.teal,
                  isFirst: false,
                  isLast: !hasVerification,
                  child: _timelineCard(
                    title: 'Draft Report',
                    color: LucidiaColors.teal,
                    child: _reportBody(scan['report']),
                  ),
                ),
              if (hasVerification)
                _TimelineNode(
                  icon: verified ? Icons.fact_check_outlined : Icons.warning_amber_outlined,
                  color: verified ? LucidiaColors.teal : LucidiaColors.error,
                  isFirst: false,
                  isLast: true,
                  child: _timelineCard(
                    title: verified ? 'Verification passed' : 'Verification flags',
                    color: verified ? LucidiaColors.teal : LucidiaColors.error,
                    child: _verificationBody(scan['verification']),
                  ),
                ),
            ]),
          ),
        ),
      ],
    );
  }

  // ---- Scan image + bbox overlay ----

  Widget _scanImageCard(Map<String, dynamic> scan) {
    if (_imageLoading) {
      return Container(
        height: 220,
        alignment: Alignment.center,
        decoration: lucidiaCardDecoration(),
        child: const CircularProgressIndicator(color: LucidiaColors.teal),
      );
    }
    if (_imageBytes == null) {
      return const SizedBox.shrink();
    }

    final geminiBbox = _extractBbox(scan['visionA']);
    final medSamBbox = _extractMedSamBbox(scan['medSam']);
    final ratio = _imageAspectRatio ?? 1.0;
    final showLegend = geminiBbox != null || medSamBbox != null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          decoration: lucidiaCardDecoration(),
          clipBehavior: Clip.antiAlias,
          child: AspectRatio(
            aspectRatio: ratio,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.memory(_imageBytes!, fit: BoxFit.contain),
                if (medSamBbox != null && _imagePixelSize != null)
                  CustomPaint(
                    painter: _MedSamBoxPainter(medSamBbox, _imagePixelSize!),
                  ),
                if (geminiBbox != null)
                  CustomPaint(
                    painter: _BoundingBoxPainter(geminiBbox),
                  ),
              ],
            ),
          ),
        ),
        if (showLegend) ...[
          const SizedBox(height: 10),
          Row(
            children: [
              if (geminiBbox != null) _legendChip('Gemini estimate', LucidiaColors.violet),
              if (geminiBbox != null && medSamBbox != null) const SizedBox(width: 12),
              if (medSamBbox != null) _legendChip('MedSAM segmentation', LucidiaColors.teal),
            ],
          ),
        ],
      ],
    );
  }

  Widget _legendChip(String label, Color color) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            border: Border.all(color: color, width: 2),
            borderRadius: BorderRadius.circular(3),
          ),
        ),
        const SizedBox(width: 6),
        Text(label, style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 11)),
      ],
    );
  }

  List<double>? _extractBbox(dynamic visionA) {
    if (visionA == null) return null;
    final raw = visionA['boundingBox'];
    if (raw == null) return null;
    try {
      final list = List<num>.from(raw);
      if (list.length != 4) return null;
      return list.map((n) => n.toDouble()).toList();
    } catch (_) {
      return null;
    }
  }

  List<double>? _extractMedSamBbox(dynamic medSam) {
    if (medSam == null) return null;
    final raw = medSam['bbox'];
    if (raw == null) return null;
    try {
      final list = List<num>.from(raw);
      if (list.length != 4) return null;
      return list.map((n) => n.toDouble()).toList();
    } catch (_) {
      return null;
    }
  }

  // ---- Status hero ----

  Widget _statusHero(Map<String, dynamic> scan) {
    final status = scan['status'] as String? ?? 'UNKNOWN';
    final (icon, color, label) = _statusVisuals(status);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [color.withValues(alpha: 0.16), LucidiaColors.surfaceElevated],
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: color, size: 26),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.w700,
                    fontSize: 16,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  scan['imageFilename'] ?? 'Untitled scan',
                  style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 13),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  (IconData, Color, String) _statusVisuals(String status) {
    switch (status) {
      case 'FINALIZED':
        return (Icons.verified_outlined, LucidiaColors.teal, 'Signed off');
      case 'COMPLETED':
        return (Icons.check_circle_outline, LucidiaColors.teal, 'Ready for review');
      case 'PROCESSING':
        return (Icons.autorenew, LucidiaColors.violet, 'Processing');
      case 'FAILED':
        return (Icons.error_outline, LucidiaColors.error, 'Failed');
      default:
        return (Icons.hourglass_empty, LucidiaColors.textSecondary, 'Received');
    }
  }

  // ---- Shared timeline card shell ----

  Widget _timelineCard({required String title, required Color color, required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: TextStyle(fontWeight: FontWeight.w700, color: color, fontSize: 15)),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }

  // ---- Arbitration card ----

  Widget _arbitrationCard(Map<String, dynamic> data) {
    final bool agree = data['agree'] == true;
    final color = agree ? LucidiaColors.teal : LucidiaColors.violet;
    final score = (data['agreementScore'] as num?)?.toStringAsFixed(2) ?? 'N/A';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(borderColor: color),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  agree ? 'Agents agree' : 'Agents disagree',
                  style: TextStyle(fontWeight: FontWeight.w700, color: color, fontSize: 15),
                ),
              ),
              _pill('score $score', color),
            ],
          ),
          if ((data['notes'] as String?)?.isNotEmpty == true) ...[
            const SizedBox(height: 8),
            Text(
              data['notes'],
              style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }

  // ---- Report body ----

  Widget _reportBody(Map<String, dynamic> data) {
    final bool flagged = data['flaggedForReview'] == true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _fieldLabel('Findings'),
        const SizedBox(height: 6),
        Text(data['findings'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary, height: 1.5)),
        const SizedBox(height: 16),
        _fieldLabel('Impression'),
        const SizedBox(height: 6),
        Text(data['impression'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary, height: 1.5)),
        if (_showDifferential(data['differential'])) ...[
          const SizedBox(height: 16),
          _differentialCard(data['differential']),
        ],
        if (flagged) ...[
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: LucidiaColors.violet.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.flag_outlined, color: LucidiaColors.violet, size: 16),
                const SizedBox(width: 8),
                const Text(
                  'Flagged for clinician review',
                  style: TextStyle(color: LucidiaColors.violet, fontWeight: FontWeight.w600, fontSize: 13),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 20),
        _actionBar(),
      ],
    );
  }

  bool _showDifferential(dynamic differential) {
    if (differential == null) return false;
    final text = differential.toString().trim();
    if (text.isEmpty) return false;
    return !text.toLowerCase().contains('not applicable');
  }

  Widget _differentialCard(String differential) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LucidiaColors.violet.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LucidiaColors.violet.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.psychology_outlined, color: LucidiaColors.violet, size: 18),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'AI-Suggested Differential — Not a Diagnosis',
                  style: TextStyle(color: LucidiaColors.violet, fontWeight: FontWeight.w700, fontSize: 13),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'General possibilities based on visual features only. Must be confirmed by a clinician.',
            style: TextStyle(
              color: LucidiaColors.textSecondary.withValues(alpha: 0.8),
              fontSize: 11,
              fontStyle: FontStyle.italic,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            differential,
            style: const TextStyle(color: LucidiaColors.textPrimary, fontSize: 13, height: 1.5),
          ),
        ],
      ),
    );
  }

  Widget _actionBar() {
    final scan = _scan!;
    final finalized = scan['status'] == 'FINALIZED';

    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: _downloadPdf,
            icon: const Icon(Icons.download, size: 18),
            label: const Text('PDF'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          flex: 2,
          child: finalized
              ? Container(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  decoration: BoxDecoration(
                    color: LucidiaColors.teal.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.4)),
                  ),
                  child: const Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.verified, color: LucidiaColors.teal, size: 18),
                      SizedBox(width: 8),
                      Text('Signed off', style: TextStyle(color: LucidiaColors.teal, fontWeight: FontWeight.w600)),
                    ],
                  ),
                )
              : ElevatedButton(
                  onPressed: _finalizing ? null : _finalize,
                  child: _finalizing
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF04211F)),
                        )
                      : const Text('Sign Off'),
                ),
        ),
      ],
    );
  }

  // ---- Verification body ----

  Widget _verificationBody(Map<String, dynamic> data) {
    final flags = List<String>.from(data['flags'] ?? []);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if ((data['notes'] as String?)?.isNotEmpty == true)
          Text(data['notes'], style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 13)),
        ...flags.map(
          (f) => Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.error_outline, color: LucidiaColors.error, size: 15),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(f, style: const TextStyle(color: LucidiaColors.error, fontSize: 13, height: 1.4)),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  // ---- Vision agent body ----

  Widget _agentBody(Map<String, dynamic> data) {
    final observations = List<String>.from(data['observations'] ?? []);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(data['summary'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary, height: 1.5)),
        if (observations.isNotEmpty) ...[
          const SizedBox(height: 12),
          ...observations.map(
            (o) => Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    margin: const EdgeInsets.only(top: 7),
                    width: 4,
                    height: 4,
                    decoration: const BoxDecoration(color: LucidiaColors.violet, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(o, style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 13, height: 1.4)),
                  ),
                ],
              ),
            ),
          ),
        ],
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          children: [
            _pill(data['regionDescription'] ?? 'Unknown region', LucidiaColors.violet),
            _pill('confidence ${data['confidence'] ?? 'N/A'}', LucidiaColors.violet),
          ],
        ),
      ],
    );
  }

  // ---- Shared bits ----

  Widget _fieldLabel(String text) {
    return Text(
      text.toUpperCase(),
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
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(text, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w600)),
    );
  }
}

/// A single node in the vertical pipeline timeline: an icon dot connected
/// by a line to the next node, with its content card to the right.
/// Everything is always visible - no expand/collapse.
class _TimelineNode extends StatelessWidget {
  final IconData icon;
  final Color color;
  final bool isFirst;
  final bool isLast;
  final Widget child;

  const _TimelineNode({
    required this.icon,
    required this.color,
    required this.isFirst,
    required this.isLast,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 36,
            child: Column(
              children: [
                Container(
                  width: 4,
                  height: isFirst ? 10 : 0,
                  color: Colors.transparent,
                ),
                Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.15),
                    shape: BoxShape.circle,
                    border: Border.all(color: color.withValues(alpha: 0.5), width: 1.5),
                  ),
                  child: Icon(icon, color: color, size: 17),
                ),
                if (!isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      color: LucidiaColors.border,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 20, top: isFirst ? 10 : 0),
              child: child,
            ),
          ),
        ],
      ),
    );
  }
}

/// Draws Gemini's estimated bounding box - coordinates are on a 0-1000
/// scale relative to image width/height, painted as a fraction of the
/// rendered size regardless of the image's actual resolution.
class _BoundingBoxPainter extends CustomPainter {
  final List<double> bbox; // [x1, y1, x2, y2] on 0-1000 scale

  _BoundingBoxPainter(this.bbox);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = LucidiaColors.violet
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5;

    final fillPaint = Paint()
      ..color = LucidiaColors.violet.withValues(alpha: 0.08)
      ..style = PaintingStyle.fill;

    final rect = Rect.fromLTRB(
      (bbox[0] / 1000) * size.width,
      (bbox[1] / 1000) * size.height,
      (bbox[2] / 1000) * size.width,
      (bbox[3] / 1000) * size.height,
    );

    canvas.drawRect(rect, fillPaint);
    canvas.drawRect(rect, paint);
  }

  @override
  bool shouldRepaint(covariant _BoundingBoxPainter oldDelegate) {
    return oldDelegate.bbox != bbox;
  }
}

/// Draws MedSAM's segmentation box - coordinates are in real pixels of the
/// original image, so this scales by (rendered size / original pixel size)
/// rather than the 0-1000 fraction used for Gemini's box.
class _MedSamBoxPainter extends CustomPainter {
  final List<double> bbox; // [x1, y1, x2, y2] in real image pixels
  final Size originalImageSize;

  _MedSamBoxPainter(this.bbox, this.originalImageSize);

  @override
  void paint(Canvas canvas, Size size) {
    final scaleX = size.width / originalImageSize.width;
    final scaleY = size.height / originalImageSize.height;

    final paint = Paint()
      ..color = LucidiaColors.teal
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5;

    final rect = Rect.fromLTRB(
      bbox[0] * scaleX,
      bbox[1] * scaleY,
      bbox[2] * scaleX,
      bbox[3] * scaleY,
    );

    canvas.drawRect(rect, paint);
  }

  @override
  bool shouldRepaint(covariant _MedSamBoxPainter oldDelegate) {
    return oldDelegate.bbox != bbox || oldDelegate.originalImageSize != originalImageSize;
  }
}