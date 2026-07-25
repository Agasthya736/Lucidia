import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'scan_service.dart';

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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Report')),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
            : _error != null
                ? Center(child: Text(_error!, style: const TextStyle(color: LucidiaColors.error)))
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        if (_scan!['visionA'] != null)
                          _agentCard('Vision A (Gemini)', _scan!['visionA']),
                        if (_scan!['visionA'] != null) const SizedBox(height: 16),
                        if (_scan!['visionB'] != null)
                          _agentCard('Vision B (Ollama)', _scan!['visionB']),
                        if (_scan!['visionB'] != null) const SizedBox(height: 16),
                        if (_scan!['arbitration'] != null) ...[
                          _arbitrationCard(_scan!['arbitration']),
                          const SizedBox(height: 16),
                        ],
                        if (_scan!['report'] != null) ...[
                          _reportCard(_scan!['report']),
                          const SizedBox(height: 16),
                        ],
                        if (_scan!['verification'] != null) ...[
                          _verificationCard(_scan!['verification']),
                          const SizedBox(height: 24),
                        ],
                        if (_scan!['status'] == 'FINALIZED')
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: lucidiaCardDecoration(borderColor: LucidiaColors.teal),
                            child: const Row(
                              children: [
                                Icon(Icons.verified, color: LucidiaColors.teal, size: 20),
                                SizedBox(width: 10),
                                Text('Signed off', style: TextStyle(color: LucidiaColors.teal, fontWeight: FontWeight.w600)),
                              ],
                            ),
                          )
                        else
                          ElevatedButton(
                            onPressed: _finalizing ? null : _finalize,
                            child: _finalizing
                                ? const SizedBox(
                                    height: 20, width: 20,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF04211F)))
                                : const Text('Sign Off Report'),
                          ),
                      ],
                    ),
                  ),
      ),
    );
  }

  Widget _agentCard(String title, Map<String, dynamic> data) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: LucidiaColors.teal)),
          const SizedBox(height: 8),
          Text(data['summary'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary)),
          const SizedBox(height: 8),
          ...List<String>.from(data['observations'] ?? [])
              .map((o) => Text('\u2022 $o', style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 13))),
          const SizedBox(height: 8),
          Text('Region: ${data['regionDescription']} | Confidence: ${data['confidence']}',
              style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12)),
        ],
      ),
    );
  }

  Widget _arbitrationCard(Map<String, dynamic> data) {
    final bool agree = data['agree'] == true;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(borderColor: agree ? LucidiaColors.teal : LucidiaColors.violet),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            agree ? 'Agents agree' : 'Agents disagree - review flagged',
            style: TextStyle(fontWeight: FontWeight.w700, color: agree ? LucidiaColors.teal : LucidiaColors.violet),
          ),
          const SizedBox(height: 6),
          Text('Agreement score: ${(data['agreementScore'] as num?)?.toStringAsFixed(2) ?? "N/A"}',
              style: const TextStyle(color: LucidiaColors.textPrimary)),
          const SizedBox(height: 6),
          Text(data['notes'] ?? '', style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12)),
        ],
      ),
    );
  }

  Widget _reportCard(Map<String, dynamic> data) {
    final bool flagged = data['flaggedForReview'] == true;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Draft Report', style: TextStyle(fontWeight: FontWeight.w700, color: LucidiaColors.teal)),
          const SizedBox(height: 8),
          const Text('Findings', style: TextStyle(fontWeight: FontWeight.w600, color: LucidiaColors.textPrimary)),
          Text(data['findings'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary)),
          const SizedBox(height: 8),
          const Text('Impression', style: TextStyle(fontWeight: FontWeight.w600, color: LucidiaColors.textPrimary)),
          Text(data['impression'] ?? '', style: const TextStyle(color: LucidiaColors.textPrimary)),
          if (flagged) ...[
            const SizedBox(height: 8),
            const Text('Flagged for clinician review',
                style: TextStyle(color: LucidiaColors.violet, fontWeight: FontWeight.w600)),
          ],
        ],
      ),
    );
  }

  Widget _verificationCard(Map<String, dynamic> data) {
    final bool verified = data['verified'] == true;
    final flags = List<String>.from(data['flags'] ?? []);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: lucidiaCardDecoration(borderColor: verified ? LucidiaColors.teal : LucidiaColors.error),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            verified ? 'Report verified' : 'Unverified claims found',
            style: TextStyle(fontWeight: FontWeight.w700, color: verified ? LucidiaColors.teal : LucidiaColors.error),
          ),
          const SizedBox(height: 6),
          Text(data['notes'] ?? '', style: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12)),
          ...flags.map((f) => Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text('- $f', style: const TextStyle(color: LucidiaColors.error, fontSize: 12)),
              )),
        ],
      ),
    );
  }
}