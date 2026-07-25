import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class LucidiaColors {
  static const background = Color(0xFF060911);
  static const surface = Color(0xFF0E121C);
  static const surfaceElevated = Color(0xFF141926);
  static const border = Color(0xFF212838);
  static const textPrimary = Color(0xFFF0F2F7);
  static const textSecondary = Color(0xFF8992A8);
  static const teal = Color(0xFF4FD1C5);
  static const violet = Color(0xFF8B7CF6);
  static const error = Color(0xFFF87171);
  static const success = Color(0xFF4FD1C5);
}

ThemeData buildLucidiaTheme() {
  final base = ThemeData.dark(useMaterial3: true);
  final textTheme = GoogleFonts.interTextTheme(base.textTheme).apply(
    bodyColor: LucidiaColors.textPrimary,
    displayColor: LucidiaColors.textPrimary,
  );

  return base.copyWith(
    scaffoldBackgroundColor: LucidiaColors.background,
    colorScheme: base.colorScheme.copyWith(
      surface: LucidiaColors.surface,
      primary: LucidiaColors.teal,
      secondary: LucidiaColors.violet,
      error: LucidiaColors.error,
    ),
    textTheme: textTheme,
    splashFactory: InkSparkle.splashFactory,
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: LucidiaColors.surfaceElevated,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: LucidiaColors.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: LucidiaColors.border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: LucidiaColors.teal, width: 1.5),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: LucidiaColors.error),
      ),
      labelStyle: const TextStyle(color: LucidiaColors.textSecondary),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: LucidiaColors.teal,
        foregroundColor: const Color(0xFF04211F),
        disabledBackgroundColor: LucidiaColors.teal.withValues(alpha: 0.35),
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
        elevation: 0,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: LucidiaColors.textPrimary,
        side: const BorderSide(color: LucidiaColors.border),
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: LucidiaColors.textSecondary),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: LucidiaColors.surfaceElevated,
      contentTextStyle: const TextStyle(color: LucidiaColors.textPrimary),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: LucidiaColors.background,
      elevation: 0,
      centerTitle: false,
      foregroundColor: LucidiaColors.textPrimary,
    ),
    dividerTheme: const DividerThemeData(color: LucidiaColors.border, thickness: 1),
  );
}

/// Reusable card decoration - consistent elevated-surface look across screens.
BoxDecoration lucidiaCardDecoration({Color? borderColor}) {
  return BoxDecoration(
    color: LucidiaColors.surfaceElevated,
    borderRadius: BorderRadius.circular(14),
    border: Border.all(color: borderColor ?? LucidiaColors.border, width: borderColor != null ? 1.5 : 1),
  );
}