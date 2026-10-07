import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../shared/theme.dart';
import 'scan_service.dart';
import 'pipeline_status_screen.dart';
import 'report_viewer_screen.dart';

enum ScanFilter { all, pending, finalized }

class ScansListScreen extends StatefulWidget {
  const ScansListScreen({super.key});

  @override
  State<ScansListScreen> createState() => ScansListScreenState();
}

class ScansListScreenState extends State<ScansListScreen> {
  final ScanService _scanService = ScanService();
  List<Map<String, dynamic>> _scans = [];
  bool _loading = true;
  String? _error;
  ScanFilter _selectedFilter = ScanFilter.all;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
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

  List<Map<String, dynamic>> get _filteredScans {
    switch (_selectedFilter) {
      case ScanFilter.pending:
        return _scans.where((s) {
          final status = s['status'] as String? ?? '';
          final flagged = s['flaggedForReview'] as bool? ?? false;
          return status == 'PROCESSING' || status == 'RECEIVED' || status == 'COMPLETED' || flagged;
        }).toList();
      case ScanFilter.finalized:
        return _scans.where((s) => s['status'] == 'FINALIZED').toList();
      case ScanFilter.all:
        return _scans;
    }
  }

  Color _statusColor(String status, bool flagged) {
    if (status == 'FINALIZED' || status == 'COMPLETED') return LucidiaColors.teal;
    if (status == 'FAILED') return LucidiaColors.error;
    if (status == 'PROCESSING' || status == 'RECEIVED') return LucidiaColors.warning;
    if (flagged) return LucidiaColors.warning;
    return LucidiaColors.teal;
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredScans;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Scans & Reports'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                _filterChip('All', ScanFilter.all, _scans.length),
                const SizedBox(width: 8),
                _filterChip(
                  'Pending Review',
                  ScanFilter.pending,
                  _scans.where((s) {
                    final st = s['status'] as String? ?? '';
                    final fl = s['flaggedForReview'] as bool? ?? false;
                    return st == 'PROCESSING' || st == 'RECEIVED' || st == 'COMPLETED' || fl;
                  }).length,
                ),
                const SizedBox(width: 8),
                _filterChip(
                  'Finalized',
                  ScanFilter.finalized,
                  _scans.where((s) => s['status'] == 'FINALIZED').length,
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: RefreshIndicator(
              onRefresh: load,
              color: LucidiaColors.teal,
              backgroundColor: LucidiaColors.surfaceElevated,
              child: _loading
                  ? const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
                  : _error != null
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(_error!,
                                    style: TextStyle(color: LucidiaColors.error),
                                    textAlign: TextAlign.center),
                                const SizedBox(height: 12),
                                OutlinedButton(
                                  onPressed: load,
                                  child: const Text('Retry'),
                                ),
                              ],
                            ),
                          ),
                        )
                      : filtered.isEmpty
                          ? ListView(
                              children: [
                                const SizedBox(height: 120),
                                Center(
                                  child: Text(
                                    _scans.isEmpty
                                        ? 'No scans found'
                                        : 'No scans match the selected filter',
                                    style: TextStyle(
                                      color: LucidiaColors.textSecondary,
                                      fontSize: 14,
                                    ),
                                  ),
                                ),
                              ],
                            )
                          : ListView.separated(
                              padding: const EdgeInsets.all(16),
                              itemCount: filtered.length,
                              separatorBuilder: (_, __) => const SizedBox(height: 12),
                              itemBuilder: (context, index) {
                                final scan = filtered[index];
                                final status = scan['status'] as String? ?? 'UNKNOWN';
                                final flagged = scan['flaggedForReview'] as bool? ?? false;
                                final createdAt = DateTime.tryParse(scan['createdAt'] ?? '');
                                final severity = scan['report'] is Map ? scan['report']['severity'] as String? : null;
                                final resultBandStr = scan['resultBand'] as String?;

                                return InkWell(
                                  borderRadius: BorderRadius.circular(14),
                                  onTap: () {
                                    final target = status == 'PROCESSING' || status == 'RECEIVED'
                                        ? PipelineStatusScreen(scanId: scan['id'])
                                        : ReportViewerScreen(scanId: scan['id']);
                                    Navigator.of(context, rootNavigator: true)
                                        .push(MaterialPageRoute(builder: (_) => target))
                                        .then((_) => load());
                                  },
                                  child: Container(
                                    padding: const EdgeInsets.all(16),
                                    decoration: lucidiaCardDecoration(),
                                    child: Row(
                                      children: [
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Row(
                                                children: [
                                                  Expanded(
                                                    child: Text(
                                                      scan['imageFilename'] ?? 'CT Series',
                                                      style: TextStyle(
                                                        color: AppColors.textPrimary,
                                                        fontWeight: FontWeight.w600,
                                                      ),
                                                      overflow: TextOverflow.ellipsis,
                                                    ),
                                                  ),
                                                  if (resultBandStr != null) ...[
                                                    const SizedBox(width: 8),
                                                    ResultBandBadge(
                                                      band: ResultBand.fromString(resultBandStr),
                                                      compact: true,
                                                    ),
                                                  ] else if (severity != null) ...[
                                                    const SizedBox(width: 8),
                                                    UrgencyBadge(
                                                      level: UrgencyLevel.fromString(severity),
                                                      compact: true,
                                                    ),
                                                  ],
                                                ],
                                              ),
                                              const SizedBox(height: 6),
                                              Row(
                                                children: [
                                                  Container(
                                                    width: 8,
                                                    height: 8,
                                                    margin: const EdgeInsets.only(right: 6),
                                                    decoration: BoxDecoration(
                                                      color: _statusColor(status, flagged),
                                                      shape: BoxShape.circle,
                                                    ),
                                                  ),
                                                  Text(
                                                    '${status[0]}${status.substring(1).toLowerCase()}',
                                                    style: TextStyle(
                                                      color: _statusColor(status, flagged),
                                                      fontSize: 12,
                                                      fontWeight: FontWeight.w500,
                                                    ),
                                                  ),
                                                  if (createdAt != null) ...[
                                                    Text(
                                                      ' \u00B7 ${DateFormat('MMM d, h:mm a').format(createdAt)}',
                                                      style: TextStyle(
                                                        color: AppColors.textSecondary,
                                                        fontSize: 12,
                                                      ),
                                                    ),
                                                  ],
                                                ],
                                              ),
                                            ],
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        Icon(
                                          Icons.chevron_right,
                                          color: AppColors.textSecondary,
                                          size: 20,
                                        ),
                                      ],
                                    ),
                                  ),
                                );
                              },
                            ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _filterChip(String label, ScanFilter filter, int count) {
    final selected = _selectedFilter == filter;
    return ChoiceChip(
      label: Text('$label ($count)'),
      selected: selected,
      onSelected: (val) {
        if (val) setState(() => _selectedFilter = filter);
      },
      selectedColor: LucidiaColors.teal.withValues(alpha: 0.2),
      backgroundColor: LucidiaColors.surfaceElevated,
      labelStyle: TextStyle(
        color: selected ? LucidiaColors.teal : LucidiaColors.textSecondary,
        fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
        fontSize: 12,
      ),
      side: BorderSide(
        color: selected ? LucidiaColors.teal : LucidiaColors.border,
      ),
    );
  }
}
