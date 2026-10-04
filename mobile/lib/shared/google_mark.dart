import 'package:flutter/material.dart';

/// Renders the official Google multi-colored 'G' icon using a vector Canvas path.
class GoogleMark extends StatelessWidget {
  final double size;

  const GoogleMark({super.key, this.size = 20});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _GoogleMarkPainter(),
      ),
    );
  }
}

class _GoogleMarkPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final double scale = size.width / 48.0;
    canvas.save();
    canvas.scale(scale, scale);

    // Standard Google 4-color paths normalized to 48x48 viewport
    final paint = Paint()..isAntiAlias = true;

    // Blue section (Right bar + right arc)
    paint.color = const Color(0xFF4285F4);
    final bluePath = Path()
      ..moveTo(46.98, 24.55)
      ..cubicTo(46.98, 22.84, 46.82, 21.2, 46.53, 19.64)
      ..lineTo(24.0, 19.64)
      ..lineTo(24.0, 28.91)
      ..lineTo(36.88, 28.91)
      ..cubicTo(36.33, 31.91, 34.64, 34.46, 32.1, 36.16)
      ..lineTo(32.1, 42.16)
      ..lineTo(39.84, 42.16)
      ..cubicTo(44.36, 38.0, 46.98, 31.84, 46.98, 24.55)
      ..close();
    canvas.drawPath(bluePath, paint);

    // Green section (Bottom arc)
    paint.color = const Color(0xFF34A853);
    final greenPath = Path()
      ..moveTo(24.0, 48.0)
      ..cubicTo(30.48, 48.0, 35.91, 45.85, 39.84, 42.16)
      ..lineTo(32.1, 36.16)
      ..cubicTo(29.95, 37.6, 27.2, 38.45, 24.0, 38.45)
      ..cubicTo(17.75, 38.45, 12.46, 34.23, 10.57, 28.56)
      ..lineTo(2.6, 28.56)
      ..lineTo(2.6, 34.74)
      ..cubicTo(6.52, 42.53, 14.62, 48.0, 24.0, 48.0)
      ..close();
    canvas.drawPath(greenPath, paint);

    // Yellow section (Bottom-left arc)
    paint.color = const Color(0xFFFBBC05);
    final yellowPath = Path()
      ..moveTo(10.57, 28.56)
      ..cubicTo(10.09, 27.13, 9.82, 25.6, 9.82, 24.0)
      ..cubicTo(9.82, 22.4, 10.09, 20.87, 10.57, 19.44)
      ..lineTo(10.57, 13.26)
      ..lineTo(2.6, 13.26)
      ..cubicTo(0.95, 16.54, 0.0, 20.16, 0.0, 24.0)
      ..cubicTo(0.0, 27.84, 0.95, 31.46, 2.6, 34.74)
      ..lineTo(10.57, 28.56)
      ..close();
    canvas.drawPath(yellowPath, paint);

    // Red section (Top arc)
    paint.color = const Color(0xFFEA4335);
    final redPath = Path()
      ..moveTo(24.0, 9.55)
      ..cubicTo(27.52, 9.55, 30.68, 10.76, 33.17, 13.14)
      ..lineTo(40.01, 6.3)
      ..cubicTo(35.89, 2.47, 30.46, 0.0, 24.0, 0.0)
      ..cubicTo(14.62, 0.0, 6.52, 5.47, 2.6, 13.26)
      ..lineTo(10.57, 19.44)
      ..cubicTo(12.46, 13.77, 17.75, 9.55, 24.0, 9.55)
      ..close();
    canvas.drawPath(redPath, paint);

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
