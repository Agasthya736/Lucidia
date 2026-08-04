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
  State<ScansListScreen> createState() => _ScansListScreenState();
}

class _ScansListScreenState extends State<ScansListScreen> {
  final ScanService _scanService = ScanService();
  List<Map<String, dynamic>> _scans = [];
  bool _loading = true;
  String? _error;
  ScanFilter _selectedFilter = ScanFilter.all;

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
    if (status == 'FINALIZED') return LucidiaColors.success;
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
              onRefresh: _load,
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
                                    style: const TextStyle(color: LucidiaColors.error),
                                    textAlign: TextAlign.center),
                                const SizedBox(height: 12),
                                OutlinedButton(
                                  onPressed: _load,
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
                                    style: const TextStyle(
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
                                              Row(
                                                children: [
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
                                                      style: const TextStyle(
                                                        color: LucidiaColors.textSecondary,
                                                        fontSize: 12,
                                                      ),
                                                    ),
                                                  ],
                                                ],
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
