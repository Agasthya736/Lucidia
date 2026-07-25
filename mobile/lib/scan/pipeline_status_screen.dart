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
  Timer? _timer;
  String _status = 'RECEIVED';
  String? _error;

  static const _stages = ['RECEIVED', 'PROCESSING', 'COMPLETED'];

  @override
  void initState() {
    super.initState();
    _poll();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) => _poll());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _poll() async {
    try {
      final scan = await _scanService.getScan(widget.scanId);
      if (!mounted) return;
      setState(() => _status = scan['status']);

      if (_status == 'COMPLETED' || _status == 'FINALIZED') {
        _timer?.cancel();
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => ReportViewerScreen(scanId: widget.scanId)),
        );
      } else if (_status == 'FAILED') {
        _timer?.cancel();
        setState(() => _error = scan['errorMessage'] ?? 'Processing failed');
      }
    } catch (e) {
      setState(() => _error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final stageIndex = _stages.indexOf(_status).clamp(0, _stages.length - 1);

    return Scaffold(
      appBar: AppBar(title: const Text('Analyzing Scan')),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_error == null) ...[
                  const SizedBox(
                    width: 64, height: 64,
                    child: CircularProgressIndicator(color: LucidiaColors.teal, strokeWidth: 3),
                  ),
                  const SizedBox(height: 32),
                  Text(
                    _status == 'PROCESSING' ? 'Running multi-agent analysis' : 'Preparing scan',
                    style: const TextStyle(
                        color: LucidiaColors.textPrimary,
                        fontSize: 17, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Vision agents \u2192 arbitration \u2192 report \u2192 verification',
                    style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 28),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(_stages.length, (i) {
                      final active = i <= stageIndex;
                      return Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: Container(
                          width: 40, height: 4,
                          decoration: BoxDecoration(
                            color: active ? LucidiaColors.teal : LucidiaColors.border,
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      );
                    }),
                  ),
                ] else ...[
                  const Icon(Icons.error_outline, color: LucidiaColors.error, size: 48),
                  const SizedBox(height: 16),
                  Text(_error!, style: const TextStyle(color: LucidiaColors.error), textAlign: TextAlign.center),
                  const SizedBox(height: 20),
                  OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Back'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}