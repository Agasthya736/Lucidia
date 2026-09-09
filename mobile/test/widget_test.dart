import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lucidia_app/main.dart';

void main() {
  testWidgets('LucidiaApp launches smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const LucidiaApp());
    // Verify that the initial frame renders without crashing
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
