import 'package:flutter/material.dart';

class AboutToolScreen extends StatelessWidget {
  const AboutToolScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('About this tool')),
      body: const SafeArea(
        child: SingleChildScrollView(
          padding: EdgeInsets.all(24),
          child: Text(
            'Lucidia provides informational analysis of images submitted by you. '
            'It is not a medical device and does not provide a diagnosis or treatment. '
            'Review any health concerns with a qualified healthcare professional.',
            style: TextStyle(fontSize: 16, height: 1.5),
          ),
        ),
      ),
    );
  }
}
