import 'dart:async';
import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'scan_service.dart';
import 'report_viewer_screen.dart';

class PipelineStatusScreen extends StatefulWidget {
  final String scanId;
  const PipelineStatusScreen({super.key, required this.scanId});

  @override
  State<PipelineStatusScreen> createState() => _PipelineStatusScreenState();
}

class _PipelineStatusScreenState extends State<PipelineStatusScreen> {
  final ScanService _scanService = ScanService();
  Timer? _pollTimer;
  Timer? _stageTimer;

  String _status = 'RECEIVED';
  String? _error;
  int _currentStage = 0;

  final List<_PipelineStep> _stages = const [
    _PipelineStep(
      title: 'Running Triage Detector',
      subtitle: 'Analyzing slice pixel morphology & density contours',
      icon: Icons.biotech_outlined,
    ),
    _PipelineStep(
      title: 'Assessing Triage Evidence',
      subtitle: 'Analyzing scan images',
      icon: Icons.filter_alt_outlined,
    ),
    _PipelineStep(
      title: 'Synthesizing Structured Report',
      subtitle: 'Evidence-grounded clinical drafting or clean auto-summary',
      icon: Icons.auto_awesome_outlined,
    ),
    _PipelineStep(
      title: 'Verifying Grounding & Claims',
      subtitle: 'Strict cross-validation against detector findings',
      icon: Icons.fact_check_outlined,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _poll();
    _pollTimer = Timer.periodic(const Duration(seconds: 2), (_) => _poll());

    // Advance informative animation stages while waiting for backend completion
    _stageTimer = Timer.periodic(const Duration(milliseconds: 1800), (timer) {
      if (!mounted) return;
      if (_currentStage < _stages.length - 1) {
        setState(() => _currentStage++);
      }
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _stageTimer?.cancel();
    super.dispose();
  }

  Future<void> _poll() async {
    try {
      final scan = await _scanService.getScan(widget.scanId);
      if (!mounted) return;
      setState(() => _status = scan['status']);

      if (_status == 'COMPLETED' || _status == 'FINALIZED') {
        _pollTimer?.cancel();
        _stageTimer?.cancel();
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => ReportViewerScreen(scanId: widget.scanId)),
        );
      } else if (_status == 'FAILED') {
        _pollTimer?.cancel();
        _stageTimer?.cancel();
        setState(() => _error = scan['errorMessage'] ?? 'Pipeline analysis encountered an error');
      }
    } catch (e) {
      // transient network error, keep polling
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Triage Pipeline'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 20),
            child: _error != null ? _buildErrorView() : _buildProgressView(),
          ),
        ),
      ),
    );
  }

  Widget _buildProgressView() {
    final activeStage = _stages[_currentStage];

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Stack(
          alignment: Alignment.center,
          children: [
            const SizedBox(
              width: 80,
              height: 80,
              child: CircularProgressIndicator(
                color: LucidiaColors.teal,
                strokeWidth: 3.5,
              ),
            ),
            Icon(activeStage.icon, color: LucidiaColors.teal, size: 36),
          ],
        ),
        const SizedBox(height: 32),
        Text(
          activeStage.title,
        style: TextStyle(
            color: LucidiaColors.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          activeStage.subtitle,
          style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 36),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: lucidiaCardDecoration(),
          child: Column(
            children: List.generate(_stages.length, (i) {
              final isDone = i < _currentStage;
              final isCurrent = i == _currentStage;

              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    Icon(
                      isDone
                          ? Icons.check_circle
                          : (isCurrent ? Icons.radio_button_checked : Icons.radio_button_unchecked),
                      color: isDone
                          ? LucidiaColors.success
                          : (isCurrent ? LucidiaColors.teal : LucidiaColors.border),
                      size: 18,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        _stages[i].title,
                        style: TextStyle(
                          color: isCurrent
                              ? LucidiaColors.textPrimary
                              : (isDone ? LucidiaColors.textSecondary : LucidiaColors.textSecondary.withValues(alpha: 0.5)),
                          fontSize: 13,
                          fontWeight: isCurrent ? FontWeight.w600 : FontWeight.normal,
                        ),
                      ),
                    ),
                    if (isCurrent)
                      const SizedBox(
                        width: 12,
                        height: 12,
                        child: CircularProgressIndicator(strokeWidth: 2, color: LucidiaColors.teal),
                      ),
                  ],
                ),
              );
            }),
          ),
        ),
        const SizedBox(height: 24),
          Text(
          'Clean scans and clear photos skip LLM synthesis for instant cost-efficient reporting.',
          style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildErrorView() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.error_outline, color: LucidiaColors.error, size: 52),
        const SizedBox(height: 16),
          Text(
          'Analysis Failed',
          style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 18, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 10),
        Text(
          _error!,
          style: const TextStyle(color: LucidiaColors.error, fontSize: 13),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.arrow_back),
          label: const Text('Back to Dashboard'),
        ),
      ],
    );
  }
}

class _PipelineStep {
  final String title;
  final String subtitle;
  final IconData icon;

  const _PipelineStep({
    required this.title,
    required this.subtitle,
    required this.icon,
  });
}