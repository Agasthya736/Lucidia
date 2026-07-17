import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Lucidia dark theme.
/// Deep slate background (not pure black) with a teal/violet dual accent -
/// the two colors echo the two-agent consensus model, not a random choice.
class LucidiaColors {
  static const background = Color(0xFF0B1120);
  static const surface = Color(0xFF141B2E);
  static const surfaceElevated = Color(0xFF1A2338);
  static const border = Color(0xFF262F45);
  static const textPrimary = Color(0xFFE7EBF3);
  static const textSecondary = Color(0xFF8D96AC);
  static const teal = Color(0xFF4FD1C5);
  static const violet = Color(0xFF8B7CF6);
  static const error = Color(0xFFF87171);
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
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: LucidiaColors.surfaceElevated,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: LucidiaColors.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: LucidiaColors.border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: LucidiaColors.teal, width: 1.5),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: LucidiaColors.error),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: LucidiaColors.error, width: 1.5),
      ),
      labelStyle: const TextStyle(color: LucidiaColors.textSecondary),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: LucidiaColors.teal,
        foregroundColor: const Color(0xFF07211F),
        disabledBackgroundColor: LucidiaColors.teal.withValues(alpha: 0.4),
        padding: const EdgeInsets.symmetric(vertical: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
        elevation: 0,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: LucidiaColors.textSecondary),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: LucidiaColors.surfaceElevated,
      contentTextStyle: const TextStyle(color: LucidiaColors.textPrimary),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: LucidiaColors.background,
      elevation: 0,
      foregroundColor: LucidiaColors.textPrimary,
    ),
  );
}