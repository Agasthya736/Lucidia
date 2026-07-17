import 'package:flutter/material.dart';
import 'theme.dart';

/// The Lucidia mark: two overlapping circles that blend where they meet.
/// A literal visual of the app's core mechanism - two independent agents,
/// one reconciled answer - not a generic logo placeholder.
class LucidiaMark extends StatelessWidget {
  final double size;
  const LucidiaMark({super.key, this.size = 56});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _MarkPainter()),
    );
  }
}

class _MarkPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final r = size.width * 0.32;
    final leftCenter = Offset(size.width * 0.38, size.height * 0.5);
    final rightCenter = Offset(size.width * 0.62, size.height * 0.5);

    final teal = Paint()
      ..color = LucidiaColors.teal.withValues(alpha: 0.85)
      ..blendMode = BlendMode.plus;
    final violet = Paint()
      ..color = LucidiaColors.violet.withValues(alpha: 0.85)
      ..blendMode = BlendMode.plus;

    canvas.saveLayer(Offset.zero & size, Paint());
    canvas.drawCircle(leftCenter, r, teal);
    canvas.drawCircle(rightCenter, r, violet);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}