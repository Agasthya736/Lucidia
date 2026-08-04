import 'package:flutter/material.dart';
import 'auth/auth_service.dart';
import 'auth/login_screen.dart';
import 'navigation/main_shell_screen.dart';
import 'shared/theme.dart';

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
    return MaterialApp(
      title: 'Lucidia',
      theme: buildLucidiaTheme(),
      home: _checkingAuth
          ? const Scaffold(
              body: Center(
                child: CircularProgressIndicator(color: LucidiaColors.teal),
              ),
            )
          : (_isLoggedIn ? const MainShellScreen() : const LoginScreen()),
      debugShowCheckedModeBanner: false,
    );
  }
}