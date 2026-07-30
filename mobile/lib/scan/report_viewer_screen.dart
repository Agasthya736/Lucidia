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
    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 100),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              _statusHero(scan),
              const SizedBox(height: 20),
              if (scan['arbitration'] != null) ...[
                _arbitrationBanner(scan['arbitration']),
                const SizedBox(height: 16),
              ],
              if (scan['report'] != null) ...[
                _ExpandableSection(
                  icon: Icons.description_outlined,
                  title: 'Draft Report',
                  accent: LucidiaColors.teal,
                  initiallyExpanded: true,
                  child: _reportBody(scan['report']),
                ),
                const SizedBox(height: 12),
              ],
              if (scan['verification'] != null) ...[
                _ExpandableSection(
                  icon: (scan['verification']['verified'] == true)
                      ? Icons.fact_check_outlined
                      : Icons.warning_amber_outlined,
                  title: (scan['verification']['verified'] == true)
                      ? 'Verification passed'
                      : 'Verification flags',
                  accent: (scan['verification']['verified'] == true)
                      ? LucidiaColors.teal
                      : LucidiaColors.error,
                  initiallyExpanded: scan['verification']['verified'] != true,
                  child: _verificationBody(scan['verification']),
                ),
                const SizedBox(height: 12),
              ],
              if (scan['visionA'] != null) ...[
                _ExpandableSection(
                  icon: Icons.visibility_outlined,
                  title: 'Vision A · Gemini',
                  accent: LucidiaColors.violet,
                  child: _agentBody(scan['visionA']),
                ),
                const SizedBox(height: 12),
              ],
              if (scan['visionB'] != null) ...[
                _ExpandableSection(
                  icon: Icons.visibility_outlined,
                  title: 'Vision B · Ollama',
                  accent: LucidiaColors.violet,
                  child: _agentBody(scan['visionB']),
                ),
                const SizedBox(height: 12),
              ],
            ]),
          ),
        ),
      ],
    );
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

  // ---- Arbitration banner ----

  Widget _arbitrationBanner(Map<String, dynamic> data) {
    final bool agree = data['agree'] == true;
    final color = agree ? LucidiaColors.teal : LucidiaColors.violet;
    final score = (data['agreementScore'] as num?)?.toStringAsFixed(2) ?? 'N/A';

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(borderColor: color),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(agree ? Icons.handshake_outlined : Icons.compare_arrows, color: color, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      agree ? 'Agents agree' : 'Agents disagree — review flagged',
                      style: TextStyle(fontWeight: FontWeight.w700, color: color, fontSize: 14),
                    ),
                    const Spacer(),
                    _pill('score $score', color),
                  ],
                ),
                if ((data['notes'] as String?)?.isNotEmpty == true) ...[
                  const SizedBox(height: 6),
                  Text(
                    data['notes'],
                    style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
                  ),
                ],
              ],
            ),
          ),
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

/// Reusable collapsible section card used throughout the report view.
class _ExpandableSection extends StatefulWidget {
  final IconData icon;
  final String title;
  final Color accent;
  final Widget child;
  final bool initiallyExpanded;

  const _ExpandableSection({
    required this.icon,
    required this.title,
    required this.accent,
    required this.child,
    this.initiallyExpanded = false,
  });

  @override
  State<_ExpandableSection> createState() => _ExpandableSectionState();
}

class _ExpandableSectionState extends State<_ExpandableSection> {
  late bool _expanded = widget.initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: lucidiaCardDecoration(),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(widget.icon, color: widget.accent, size: 20),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      widget.title,
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: widget.accent,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  AnimatedRotation(
                    turns: _expanded ? 0.5 : 0,
                    duration: const Duration(milliseconds: 200),
                    child: Icon(Icons.keyboard_arrow_down, color: LucidiaColors.textSecondary),
                  ),
                ],
              ),
            ),
          ),
          AnimatedCrossFade(
            firstChild: const SizedBox(width: double.infinity),
            secondChild: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: widget.child,
            ),
            crossFadeState: _expanded ? CrossFadeState.showSecond : CrossFadeState.showFirst,
            firstCurve: Curves.easeOut,
            secondCurve: Curves.easeOut,
            sizeCurve: Curves.easeOut,
            duration: const Duration(milliseconds: 200),
            layoutBuilder: (top, topKey, bottom, bottomKey) => Stack(
              alignment: Alignment.topCenter,
              children: [
                Positioned(key: bottomKey, child: bottom),
                Positioned(key: topKey, child: top),
              ],
            ),
          ),
        ],
      ),
    );
  }
}