import 'package:flutter/material.dart';
import '../shared/theme.dart';
import 'auth_service.dart';
import 'login_screen.dart';
import '../scan/scan_service.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final AuthService _authService = AuthService();
  final ScanService _scanService = ScanService();

  final _apiKeyController = TextEditingController();
  bool _obscureKey = true;
  bool _savingKey = false;
  bool _loggingOut = false;
  bool _hasCustomKey = false;

  bool _loadingProfile = true;
  String? _profileError;
  Map<String, dynamic>? _userProfile;

  Map<String, dynamic>? _quota;

  @override
  void initState() {
    super.initState();
    _loadProfile();
    _loadSettings();
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    super.dispose();
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

  Future<void> _loadSettings() async {
    final key = await _scanService.getCustomApiKey();
    if (key != null && key.isNotEmpty) {
      setState(() {
        _apiKeyController.text = key;
        _hasCustomKey = true;
      });
    }

    try {
      final q = await _scanService.getQuota();
      if (mounted) setState(() => _quota = q);
    } catch (_) {}
  }

  Future<void> _saveKey() async {
    setState(() => _savingKey = true);
    final key = _apiKeyController.text.trim();
    await _scanService.setCustomApiKey(key);
    await _loadSettings();
    if (!mounted) return;
    setState(() => _savingKey = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(key.isEmpty
            ? 'Custom Gemini key cleared. Using standard quota.'
            : 'Gemini API key saved securely. Unlimited BYOK mode activated.'),
      ),
    );
  }

  Future<void> _clearKey() async {
    await _scanService.clearCustomApiKey();
    setState(() {
      _apiKeyController.clear();
      _hasCustomKey = false;
    });
    await _loadSettings();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Custom API key removed.')),
    );
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

  void _showPrivacyPolicy() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: LucidiaColors.surfaceElevated,
        title: Text('Privacy Policy & Data Handling', style: TextStyle(color: LucidiaColors.textPrimary)),
        content: SingleChildScrollView(
          child: Text(
            'Lucidia (VeriRad) Data Protection & Privacy Notice:\n\n'
            '1. Patient Data Protection: CT scan slice images processed by Lucidia are transmitted over encrypted TLS channels. '
            'Protected Health Information (PHI) is automatically stripped or de-identified.\n\n'
            '2. BYOK Privacy: When Bring-Your-Own-Key (BYOK) mode is active, your Gemini API key is stored locally in the hardware-backed '
            'Android Keystore / iOS Keychain. It is never logged or stored in backend logs.\n\n'
            '3. Data Retention: Image files are stored temporarily for report generation and sign-off, and can be permanently deleted at any time.\n\n'
            '4. Zero Third-Party Monetization: Health data is never sold, leased, or used for third-party model training.',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 13, height: 1.4),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  void _showRegulatoryNotice() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: LucidiaColors.surfaceElevated,
        title: Text('Regulatory Classification & SaMD', style: TextStyle(color: LucidiaColors.textPrimary)),
        content: SingleChildScrollView(
          child: Text(
            'Software as a Medical Device (SaMD) Notice:\n\n'
            '• Medical Device Positioning: Lucidia is designed as a second-read documentation and triage workflow assistive tool.\n\n'
            '• Not Autonomous: The system does NOT provide autonomous diagnostic decisions. It does not replace professional radiological evaluation.\n\n'
            '• Mandatory Sign-Off: All generated documentation requires explicit review and certification before clinical use or export.\n\n'
            '• Intended Use: For use in medical documentation and imaging workflows.',
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
              _buildByokCard(),
              const SizedBox(height: 16),
              _buildSystemInfoCard(),
              const SizedBox(height: 16),
              _buildComplianceCard(),
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
                        isDark ? 'Radiology Dark Mode' : 'Clinical Light Mode',
                        style: TextStyle(
                          color: LucidiaColors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        isDark ? 'Optimized for dim diagnostic reading rooms' : 'Crisp daytime theme',
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
    final bool isByok = _hasCustomKey || (_quota?['byokActive'] == true);

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Scan Quota & Rate Limits',
                style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isByok ? LucidiaColors.teal.withValues(alpha: 0.15) : LucidiaColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: isByok ? LucidiaColors.teal : LucidiaColors.border),
                ),
                child: Text(
                  isByok ? 'BYOK · Unlimited' : 'Free Tier',
                  style: TextStyle(
                    color: isByok ? LucidiaColors.teal : LucidiaColors.textSecondary,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (isByok)
            const Text(
              'Your custom API key is active. Scan limits and rate constraints are waived.',
              style: TextStyle(color: LucidiaColors.teal, fontSize: 12),
            )
          else ...[
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
        ],
      ),
    );
  }

  Widget _buildByokCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: lucidiaCardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.vpn_key_outlined, size: 18, color: LucidiaColors.teal),
              SizedBox(width: 8),
              Text(
                'Bring Your Own Key (BYOK)',
                style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Supply your personal or institution Google Gemini API key to unlock unlimited high-throughput scans. '
            'Stored securely in local Keystore, never logged on backend.',
            style: TextStyle(color: LucidiaColors.textSecondary, fontSize: 12, height: 1.4),
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _apiKeyController,
            obscureText: _obscureKey,
            decoration: InputDecoration(
              labelText: 'Gemini API Key',
              hintText: 'AIzaSy...',
              prefixIcon: const Icon(Icons.key),
              suffixIcon: IconButton(
                icon: Icon(_obscureKey ? Icons.visibility_off : Icons.visibility),
                onPressed: () => setState(() => _obscureKey = !_obscureKey),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _savingKey ? null : _saveKey,
                  child: _savingKey
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('Save API Key'),
                ),
              ),
              if (_hasCustomKey) ...[
                const SizedBox(width: 12),
                OutlinedButton(
                  onPressed: _clearKey,
                  style: OutlinedButton.styleFrom(foregroundColor: LucidiaColors.error),
                  child: const Text('Clear'),
                ),
              ],
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
            'Architecture & Pipeline Status',
            style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 14),
          _infoRow(Icons.memory, 'Triage Detector', 'CT Pixel Analyzer v1.0'),
          const SizedBox(height: 10),
          _infoRow(Icons.account_tree_outlined, 'Cost Branching', 'Clean Bypass Activated'),
          const SizedBox(height: 10),
          _infoRow(Icons.auto_awesome, 'Synthesis LLM', 'Grounded Schema Provider'),
          const SizedBox(height: 10),
          _infoRow(Icons.verified_outlined, 'Verifier Engine', 'Strict Grounding Check'),
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
            'Compliance & Medical Disclaimers',
            style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          Material(
            color: Colors.transparent,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.privacy_tip_outlined, color: LucidiaColors.teal),
              title: Text('Privacy Policy & Health Data Protection', style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13)),
              trailing: Icon(Icons.chevron_right, color: LucidiaColors.textSecondary),
              onTap: _showPrivacyPolicy,
            ),
          ),
          const Divider(height: 1),
          Material(
            color: Colors.transparent,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.medical_services_outlined, color: LucidiaColors.teal),
              title: Text('SaMD Classification & Review Notice', style: TextStyle(color: LucidiaColors.textPrimary, fontSize: 13)),
              trailing: Icon(Icons.chevron_right, color: LucidiaColors.textSecondary),
              onTap: _showRegulatoryNotice,
            ),
          ),
        ],
      ),
    );
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
