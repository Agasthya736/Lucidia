import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../shared/theme.dart';
import '../shared/lucidia_mark.dart';
import 'scan_service.dart';
import 'pipeline_status_screen.dart';
import 'report_viewer_screen.dart';

class DashboardScreen extends StatefulWidget {
  final VoidCallback? onNewScanTap;
  final VoidCallback? onViewAllScansTap;

  const DashboardScreen({
    super.key,
    this.onNewScanTap,
    this.onViewAllScansTap,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final ScanService _scanService = ScanService();
  List<Map<String, dynamic>> _scans = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final scans = await _scanService.listScans();
      if (!mounted) return;
      setState(() => _scans = scans);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  int get _totalScans => _scans.length;

  int get _pendingReviewCount => _scans.where((s) {
        final status = s['status'] as String? ?? '';
        final flagged = s['flaggedForReview'] as bool? ?? false;
        return status == 'PROCESSING' || status == 'RECEIVED' || status == 'COMPLETED' || flagged;
      }).length;

  int get _finalizedCount => _scans.where((s) => s['status'] == 'FINALIZED').length;

  List<Map<String, dynamic>> get _recentScans {
    final copy = List<Map<String, dynamic>>.from(_scans);
    copy.sort((a, b) {
      final da = DateTime.tryParse(a['createdAt'] ?? '') ?? DateTime(1970);
      final db = DateTime.tryParse(b['createdAt'] ?? '') ?? DateTime(1970);
      return db.compareTo(da);
    });
    return copy.take(5).toList();
  }

  Color _statusColor(String status, bool flagged) {
    if (status == 'FINALIZED') return LucidiaColors.success;
    if (status == 'FAILED') return LucidiaColors.error;
    if (status == 'PROCESSING' || status == 'RECEIVED') return LucidiaColors.warning;
    if (flagged) return LucidiaColors.warning;
    return LucidiaColors.teal;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: const [
            LucidiaMark(size: 24),
            SizedBox(width: 10),
            Text('Dashboard', style: TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        color: LucidiaColors.teal,
        backgroundColor: LucidiaColors.surfaceElevated,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Summary Metrics Row
              Row(
                children: [
                  Expanded(
                    child: _metricCard(
                      'Total Scans',
                      _totalScans.toString(),
                      Icons.folder_outlined,
                      LucidiaColors.teal,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _metricCard(
                      'Pending Review',
                      _pendingReviewCount.toString(),
                      Icons.rate_review_outlined,
                      LucidiaColors.warning,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _metricCard(
                      'Finalized',
                      _finalizedCount.toString(),
                      Icons.task_alt_outlined,
                      LucidiaColors.success,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // CTA Card for New Scan
              GestureDetector(
                onTap: widget.onNewScanTap,
                child: Container(
                  padding: const EdgeInsets.all(20),
                  decoration: lucidiaCardDecoration(borderColor: LucidiaColors.teal),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: LucidiaColors.teal.withValues(alpha: 0.15),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.add_a_photo_outlined, color: LucidiaColors.teal, size: 28),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              'Submit New CT Scan',
                              style: TextStyle(
                                color: LucidiaColors.textPrimary,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Upload CT scan image for multi-agent AI verification',
                              style: TextStyle(
                                color: LucidiaColors.textSecondary,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.arrow_forward_ios, color: LucidiaColors.teal, size: 16),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 28),

              // Recent Scans Section Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Recent Scans',
                    style: TextStyle(
                      color: LucidiaColors.textPrimary,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  if (widget.onViewAllScansTap != null)
                    TextButton(
                      onPressed: widget.onViewAllScansTap,
                      child: const Text('View All'),
                    ),
                ],
              ),
              const SizedBox(height: 12),

              if (_loading)
                const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(child: CircularProgressIndicator(color: LucidiaColors.teal)),
                )
              else if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(_error!, style: const TextStyle(color: LucidiaColors.error)),
                )
              else if (_recentScans.isEmpty)
                Container(
                  padding: const EdgeInsets.all(32),
                  decoration: lucidiaCardDecoration(),
                  child: const Center(
                    child: Text(
                      'No scans uploaded yet',
                      style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 14),
                    ),
                  ),
                )
              else
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _recentScans.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final scan = _recentScans[index];
                    final status = scan['status'] as String? ?? 'UNKNOWN';
                    final flagged = scan['flaggedForReview'] as bool? ?? false;
                    final createdAt = DateTime.tryParse(scan['createdAt'] ?? '');
                    return InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: () {
                        final target = status == 'PROCESSING' || status == 'RECEIVED'
                            ? PipelineStatusScreen(scanId: scan['id'])
                            : ReportViewerScreen(scanId: scan['id']);
                        Navigator.of(context, rootNavigator: true)
                            .push(MaterialPageRoute(builder: (_) => target))
                            .then((_) => _load());
                      },
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: lucidiaCardDecoration(),
                        child: Row(
                          children: [
                            Container(
                              width: 10,
                              height: 10,
                              decoration: BoxDecoration(
                                color: _statusColor(status, flagged),
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    scan['imageFilename'] ?? 'Scan',
                                    style: const TextStyle(
                                      color: LucidiaColors.textPrimary,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${status[0]}${status.substring(1).toLowerCase()}'
                                    '${createdAt != null ? " \u00B7 ${DateFormat('MMM d, h:mm a').format(createdAt)}" : ""}',
                                    style: const TextStyle(
                                      color: LucidiaColors.textSecondary,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const Icon(
                              Icons.chevron_right,
                              color: LucidiaColors.textSecondary,
                              size: 20,
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _metricCard(String title, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 22),
          const SizedBox(height: 12),
          Text(
            value,
            style: TextStyle(
              color: LucidiaColors.textPrimary,
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            title,
            style: const TextStyle(
              color: LucidiaColors.textSecondary,
              fontSize: 11,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
