import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../shared/theme.dart';
import '../auth/auth_service.dart';
import '../auth/login_screen.dart';
import 'scan_service.dart';
import 'scan_capture_screen.dart';
import 'pipeline_status_screen.dart';
import 'report_viewer_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ScanService _scanService = ScanService();
  final AuthService _authService = AuthService();
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
      setState(() => _scans = scans);
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _loading = false);
    }
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'FINALIZED':
        return LucidiaColors.teal;
      case 'COMPLETED':
        return LucidiaColors.violet;
      case 'FAILED':
        return LucidiaColors.error;
      case 'PROCESSING':
        return LucidiaColors.textSecondary;
      default:
        return LucidiaColors.textSecondary;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Lucidia'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout, size: 20),
            onPressed: () async {
              await _authService.logout();
              if (!mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const LoginScreen()),
                (route) => false,
              );
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        color: LucidiaColors.teal,
        backgroundColor: LucidiaColors.surfaceElevated,
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: LucidiaColors.teal))
            : _error != null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(_error!, style: const TextStyle(color: LucidiaColors.error)),
                    ),
                  )
                : _scans.isEmpty
                    ? ListView(
                        children: const [
                          SizedBox(height: 120),
                          Center(
                            child: Text(
                              'No scans yet',
                              style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 15),
                            ),
                          ),
                        ],
                      )
                    : ListView.separated(
                        padding: const EdgeInsets.all(16),
                        itemCount: _scans.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 12),
                        itemBuilder: (context, index) {
                          final scan = _scans[index];
                          final status = scan['status'] as String;
                          final createdAt = DateTime.tryParse(scan['createdAt'] ?? '');
                          return InkWell(
                            borderRadius: BorderRadius.circular(14),
                            onTap: () {
                              final target = status == 'PROCESSING' || status == 'RECEIVED'
                                  ? PipelineStatusScreen(scanId: scan['id'])
                                  : ReportViewerScreen(scanId: scan['id']);
                              Navigator.of(context)
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
                                      color: _statusColor(status),
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
                                              fontWeight: FontWeight.w600),
                                        ),
                                        const SizedBox(height: 4),
                                        Text(
                                          '${status[0]}${status.substring(1).toLowerCase()}'
                                          '${createdAt != null ? " Â· ${DateFormat('MMM d, h:mm a').format(createdAt)}" : ""}',
                                          style: const TextStyle(
                                              color: LucidiaColors.textSecondary, fontSize: 12),
                                        ),
                                      ],
                                    ),
                                  ),
                                  const Icon(Icons.chevron_right,
                                      color: LucidiaColors.textSecondary, size: 20),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: LucidiaColors.teal,
        foregroundColor: const Color(0xFF04211F),
        onPressed: () {
          Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => const ScanCaptureScreen()))
              .then((_) => _load());
        },
        icon: const Icon(Icons.add),
        label: const Text('New Scan', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
    );
  }
}