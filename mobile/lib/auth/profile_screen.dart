import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'auth_service.dart';
import 'login_screen.dart';
import '../scan/scan_service.dart';
import '../shared/privacy_policy_link.dart';
import 'about_tool_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final AuthService _authService = AuthService();
  final ScanService _scanService = ScanService();

  bool _loggingOut = false;
  bool _deletingAccount = false;

  bool _loadingProfile = true;
  String? _profileError;
  Map<String, dynamic>? _userProfile;

  Map<String, dynamic>? _quota;

  @override
  void initState() {
    super.initState();
    _loadProfile();
    _loadQuota();
  }

  Future<void> _loadProfile() async {
    setState(() {
      _loadingProfile = true;
      _profileError = null;
    });

    try {
      final me = await _authService.getMe();
      if (mounted) {
        setState(() {
          _userProfile = me;
          _loadingProfile = false;
        });
      }
    } catch (e) {
      final cachedName = await _authService.getName();
      final cachedEmail = await _authService.getEmail();
      final cachedAvatar = await _authService.getAvatarUrl();
      if (mounted) {
        setState(() {
          if (cachedName != null || cachedEmail != null) {
            _userProfile = {
              'name': cachedName ?? 'User',
              'email': cachedEmail ?? '',
              'avatarUrl': cachedAvatar,
            };
          }
          _profileError = e.toString().replaceFirst('ApiException: ', '');
          _loadingProfile = false;
        });
      }
    }
  }

  Future<void> _loadQuota() async {
    try {
      final q = await _scanService.getQuota();
      if (mounted) setState(() => _quota = q);
    } catch (_) {}
  }

  Future<void> _handleLogout() async {
    setState(() => _loggingOut = true);
    await _authService.logout();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (route) => false,
    );
  }

  void _showDisclaimerNotice() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: LucidiaColors.surfaceElevated,
        title: Text('Educational Use Disclaimer', style: TextStyle(color: LucidiaColors.textPrimary)),
        content: SingleChildScrollView(
          child: Text(
            'Important Notice:\n\n'
            '• Lucidia is an educational and informational tool only.\n\n'
            '• The AI-generated reports and analyses are for general informational purposes and '
            'do NOT constitute medical advice, diagnosis, or treatment.\n\n'
            '• Always consult a qualified healthcare professional for any medical concerns '
            'or before making any health-related decisions.\n\n'
            '• Do not rely on Lucidia results for clinical decision-making.',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13, height: 1.4),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Understood'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile & Settings'),
        actions: [
          IconButton(
            tooltip: 'Toggle Light / Dark Theme',
            icon: Icon(
              LucidiaTheme.isDarkMode ? Icons.light_mode : Icons.dark_mode,
              color: LucidiaColors.teal,
            ),
            onPressed: () {
              setState(() {
                LucidiaTheme.toggleTheme();
              });
            },
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildUserCard(),
              const SizedBox(height: 16),
              _buildThemeCard(),
              const SizedBox(height: 16),
              _buildQuotaCard(),
              const SizedBox(height: 16),
              _buildSystemInfoCard(),
              const SizedBox(height: 16),
              _buildComplianceCard(),
              const SizedBox(height: 16),
              _buildRetentionCard(),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _deletingAccount ? null : _confirmAccountDeletion,
                icon: _deletingAccount
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.delete_forever_outlined),
                label: const Text('Delete my account and data'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: LucidiaColors.error,
                  side: const BorderSide(color: LucidiaColors.error),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
              ),
              const SizedBox(height: 32),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: LucidiaColors.error,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
                onPressed: _loggingOut ? null : _handleLogout,
                icon: _loggingOut
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.logout),
                label: const Text('Log Out', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildUserCard() {
    if (_loadingProfile && _userProfile == null) {
      return Container(
        padding: const EdgeInsets.all(24),
        decoration: lucidiaCardDecoration(),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(strokeWidth: 2, color: LucidiaColors.teal),
              const SizedBox(height: 12),
              Text(
                'Loading profile...',
                style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13),
              ),
            ],
          ),
        ),
      );
    }

    if (_profileError != null && _userProfile == null) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: lucidiaCardDecoration(),
        child: Column(
          children: [
            const Icon(Icons.error_outline, color: LucidiaColors.error, size: 36),
            const SizedBox(height: 10),
            Text(
              'Failed to load profile',
              style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            Text(
              _profileError!,
              style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 14),
            ElevatedButton.icon(
              onPressed: _loadProfile,
              icon: const Icon(Icons.refresh, size: 16),
              label: const Text('Retry'),
              style: ElevatedButton.styleFrom(
                backgroundColor: LucidiaColors.teal,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      );
    }

    final name = (_userProfile?['name'] as String?)?.trim();
    final displayName = (name != null && name.isNotEmpty) ? name : 'User';
    final email = (_userProfile?['email'] as String?) ?? '';
    final avatarUrl = _userProfile?['avatarUrl'] as String?;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Row(
        children: [
          _buildAvatar(displayName, avatarUrl),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
                  style: TextStyle(
                    color: LucidiaColors.textPrimary,
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (email.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    email,
                    style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13),
                  ),
                ],
                if (_loadingProfile) ...[
                  const SizedBox(height: 4),
                  const SizedBox(
                    width: 12,
                    height: 12,
                    child: CircularProgressIndicator(strokeWidth: 1.5, color: LucidiaColors.teal),
                  ),
                ],
              ],
            ),
          ),
          if (_profileError != null)
            IconButton(
              icon: const Icon(Icons.refresh, color: LucidiaColors.warning, size: 20),
              tooltip: 'Retry loading profile',
              onPressed: _loadProfile,
            ),
        ],
      ),
    );
  }

  Widget _buildAvatar(String name, String? avatarUrl) {
    if (avatarUrl != null && avatarUrl.trim().isNotEmpty) {
      return CircleAvatar(
        radius: 26,
        backgroundColor: LucidiaColors.surface,
        backgroundImage: NetworkImage(avatarUrl),
        onBackgroundImageError: (_, __) {},
        child: avatarUrl.isEmpty
            ? Text(
                name.isNotEmpty ? name[0].toUpperCase() : 'U',
                style: const TextStyle(fontWeight: FontWeight.bold, color: LucidiaColors.teal, fontSize: 18),
              )
            : null,
      );
    }

    final initial = name.isNotEmpty ? name[0].toUpperCase() : 'U';
    return Container(
      width: 52,
      height: 52,
      decoration: BoxDecoration(
        color: LucidiaColors.teal.withValues(alpha: 0.15),
        shape: BoxShape.circle,
        border: Border.all(color: LucidiaColors.teal.withValues(alpha: 0.4), width: 1.5),
      ),
      child: Center(
        child: Text(
          initial,
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: LucidiaColors.teal,
          ),
        ),
      ),
    );
  }

  Widget _buildThemeCard() {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: LucidiaTheme.themeNotifier,
      builder: (context, mode, _) {
        final isDark = mode == ThemeMode.dark;
        return Container(
          padding: const EdgeInsets.all(18),
          decoration: lucidiaCardDecoration(),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(
                    isDark ? Icons.dark_mode_outlined : Icons.light_mode_outlined,
                    color: LucidiaColors.teal,
                  ),
                  const SizedBox(width: 14),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isDark ? 'Dark Mode' : 'Light Mode',
                        style: TextStyle(
                          color: LucidiaColors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        isDark ? 'Easier on the eyes in low light' : 'Crisp daytime display',
                        style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 11),
                      ),
                    ],
                  ),
                ],
              ),
              Switch.adaptive(
                value: isDark,
                activeTrackColor: LucidiaColors.teal,
                onChanged: (_) {
                  setState(() {
                    LucidiaTheme.toggleTheme();
                  });
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildQuotaCard() {
    final int remaining = _quota?['remainingThisMonth'] ?? 20;
    final int total = _quota?['monthlyLimit'] ?? 20;
    final int used = _quota?['usedThisMonth'] ?? 0;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Monthly Scan Quota',
            style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: total > 0 ? (used / total).clamp(0.0, 1.0) : 0.0,
              backgroundColor: LucidiaColors.surface,
              color: remaining <= 3 ? LucidiaColors.warning : LucidiaColors.teal,
              minHeight: 8,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '$used of $total scans used this month',
                style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
              ),
              Text(
                '$remaining remaining',
                style: TextStyle(
                  color: remaining <= 3 ? LucidiaColors.warning : LucidiaColors.textPrimary,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSystemInfoCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'AI Pipeline',
            style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 14),
          _infoRow(Icons.biotech_outlined, 'Triage Detector', 'Image Pixel Analyzer v1.0'),
          const SizedBox(height: 10),
          _infoRow(Icons.account_tree_outlined, 'Cost Optimization', 'Clean Bypass Activated'),
          const SizedBox(height: 10),
          _infoRow(Icons.auto_awesome, 'Report Synthesis', 'Grounded AI Provider'),
          const SizedBox(height: 10),
          _infoRow(Icons.verified_outlined, 'Verifier Engine', 'Grounding Check Active'),
        ],
      ),
    );
  }

  Widget _buildComplianceCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Legal & Disclaimers',
            style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          Material(
            color: Colors.transparent,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.privacy_tip_outlined, color: LucidiaColors.teal),
              title: Text('Privacy Policy & Data Handling', style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13)),
              trailing: Icon(Icons.chevron_right, color: LucidiaColors.textSecondary),
              onTap: () => openPrivacyPolicy(context),
            ),
          ),
          const Divider(height: 1),
          Material(
            color: Colors.transparent,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.info_outline, color: LucidiaColors.teal),
              title: Text('Educational Use Disclaimer', style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13)),
              trailing: Icon(Icons.chevron_right, color: LucidiaColors.textSecondary),
              onTap: _showDisclaimerNotice,
            ),
          ),
          const Divider(height: 1),
          Material(
            color: Colors.transparent,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.info_outline, color: LucidiaColors.teal),
              title: Text('About this tool', style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13)),
              trailing: Icon(Icons.chevron_right, color: LucidiaColors.textSecondary),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const AboutToolScreen()),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRetentionCard() {
    final int? retentionDays = _userProfile?['retentionDays'] as int?;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Data retention',
            style: TextStyle(
              color: LucidiaColors.textPrimary,
              fontSize: 14,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Choose how long scan data is kept.',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<int?>(
            initialValue: retentionDays,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Keep data',
            ),
            items: const [
              DropdownMenuItem<int?>(value: 30, child: Text('30 days')),
              DropdownMenuItem<int?>(value: null, child: Text('Keep until deleted')),
            ],
            onChanged: (days) => _saveRetention(days),
          ),
        ],
      ),
    );
  }

  Future<void> _saveRetention(int? days) async {
    try {
      await _scanService.updateRetention(days);
      if (!mounted) return;
      setState(() {
        _userProfile = {...?_userProfile, 'retentionDays': days};
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Data retention preference saved.')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not save retention preference: $error')),
      );
    }
  }

  Future<void> _confirmAccountDeletion() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete account and data?'),
        content: const Text(
          'This permanently deletes your account and associated scan data. This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: LucidiaColors.error),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete my account and data'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _deletingAccount = true);
    try {
      await _scanService.deleteAccount();
      await _authService.logout();
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<void>(builder: (_) => const LoginScreen()),
        (_) => false,
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not delete account: $error')),
      );
    } finally {
      if (mounted) setState(() => _deletingAccount = false);
    }
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, size: 16, color: LucidiaColors.teal),
        const SizedBox(width: 10),
        Text(label, style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12)),
        const Spacer(),
        Text(value, style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 12, fontWeight: FontWeight.w500)),
      ],
    );
  }
}
