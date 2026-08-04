import 'package:flutter/material.dart';
import '../shared/theme.dart';
import '../scan/dashboard_screen.dart';
import '../scan/scans_list_screen.dart';
import '../scan/scan_capture_screen.dart';
import '../auth/profile_screen.dart';

class MainShellScreen extends StatefulWidget {
  const MainShellScreen({super.key});

  @override
  State<MainShellScreen> createState() => _MainShellScreenState();
}

class _MainShellScreenState extends State<MainShellScreen> {
  int _currentIndex = 0;

  void _onTabSelected(int index) {
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final List<Widget> pages = [
      DashboardScreen(
        onNewScanTap: () => _onTabSelected(2),
        onViewAllScansTap: () => _onTabSelected(1),
      ),
      const ScansListScreen(),
      const ScanCaptureScreen(),
      const ProfileScreen(),
    ];

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: pages,
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          border: Border(
            top: BorderSide(color: LucidiaColors.border, width: 1),
          ),
        ),
        child: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: _onTabSelected,
          type: BottomNavigationBarType.fixed,
          backgroundColor: LucidiaColors.surface,
          selectedItemColor: LucidiaColors.teal,
          unselectedItemColor: LucidiaColors.textSecondary,
          selectedFontSize: 12,
          unselectedFontSize: 12,
          elevation: 0,
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.dashboard_outlined),
              activeIcon: Icon(Icons.dashboard),
              label: 'Dashboard',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.format_list_bulleted_outlined),
              activeIcon: Icon(Icons.format_list_bulleted),
              label: 'Scans',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.add_circle_outline),
              activeIcon: Icon(Icons.add_circle),
              label: 'New Scan',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.person_outline),
              activeIcon: Icon(Icons.person),
              label: 'Profile',
            ),
          ],
        ),
      ),
    );
  }
}
