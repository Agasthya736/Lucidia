import 'package:flutter/material.dart';
import 'pipeline/vision_test_screen.dart';
import 'shared/theme.dart';

void main() {
  runApp(const LucidiaApp());
}

class LucidiaApp extends StatelessWidget {
  const LucidiaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Lucidia',
      theme: buildLucidiaTheme(),
      home: const VisionTestScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}