import 'package:flutter/material.dart';
import 'auth/auth_service.dart';
import 'auth/login_screen.dart';
import 'navigation/main_shell_screen.dart';
import 'theme/lucidia_theme.dart';

void main() {
  runApp(const LucidiaApp());
}

class LucidiaApp extends StatefulWidget {
  const LucidiaApp({super.key});

  @override
  State<LucidiaApp> createState() => _LucidiaAppState();
}

class _LucidiaAppState extends State<LucidiaApp> {
  final AuthService _authService = AuthService();
  bool _checkingAuth = true;
  bool _isLoggedIn = false;

  @override
  void initState() {
    super.initState();
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    final loggedIn = await _authService.isLoggedIn();
    if (!mounted) return;
    setState(() {
      _isLoggedIn = loggedIn;
      _checkingAuth = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: LucidiaTheme.themeNotifier,
      builder: (context, themeMode, _) {
        return MaterialApp(
          title: 'Lucidia',
          theme: LucidiaTheme.light,
          darkTheme: LucidiaTheme.dark,
          themeMode: themeMode,
          home: _checkingAuth
              ? Scaffold(
                  body: Center(
                    child: CircularProgressIndicator(color: AppColors.primary),
                  ),
                )
              : (_isLoggedIn ? const MainShellScreen() : const LoginScreen()),
          debugShowCheckedModeBanner: false,
        );
      },
    );
  }
}