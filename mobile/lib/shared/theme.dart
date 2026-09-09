import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class LucidiaColors {
  // --- Ultra-dark palette ---
  static const background = Color(0xFF050A14);
  static const surface = Color(0xFF0C1322);
  static const surfaceElevated = Color(0xFF111A2E);
  static const surfaceBright = Color(0xFF162038);
  static const border = Color(0xFF1E2A45);
  static const borderSubtle = Color(0xFF151F36);

  // --- Text ---
  static const textPrimary = Color(0xFFEAEFF6);
  static const textSecondary = Color(0xFF7A8BA8);
  static const textTertiary = Color(0xFF4D5E78);

  // --- Accents ---
  static const teal = Color(0xFF3B82F6);
  static const tealGlow = Color(0xFF1D4ED8);
  static const violet = Color(0xFF8B5CF6);
  static const violetGlow = Color(0xFF6D28D9);
  static const cyan = Color(0xFF06B6D4);

  // --- Semantics ---
  static const error = Color(0xFFEF4444);
  static const errorDim = Color(0xFFDC2626);
  static const success = Color(0xFF22C55E);
  static const successDim = Color(0xFF16A34A);
  static const warning = Color(0xFFF59E0B);
  static const warningDim = Color(0xFFD97706);
}

ThemeData buildLucidiaTheme() {
  final base = ThemeData.dark(useMaterial3: true);
  final textTheme = GoogleFonts.interTextTheme(base.textTheme).apply(
    bodyColor: LucidiaColors.textPrimary,
    displayColor: LucidiaColors.textPrimary,
  );

  return base.copyWith(
    scaffoldBackgroundColor: LucidiaColors.background,
    canvasColor: LucidiaColors.surface,
    cardColor: LucidiaColors.surfaceElevated,
    dialogTheme: const DialogThemeData(backgroundColor: LucidiaColors.surfaceElevated),
    colorScheme: base.colorScheme.copyWith(
      surface: LucidiaColors.surface,
      primary: LucidiaColors.teal,
      secondary: LucidiaColors.violet,
      error: LucidiaColors.error,
      onSurface: LucidiaColors.textPrimary,
      onPrimary: Colors.white,
    ),
    textTheme: textTheme,
    splashFactory: InkSparkle.splashFactory,
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: LucidiaColors.surface,
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
      hintStyle: const TextStyle(color: LucidiaColors.textTertiary),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: LucidiaColors.teal,
        foregroundColor: const Color(0xFFFFFFFF),
        disabledBackgroundColor: LucidiaColors.teal.withValues(alpha: 0.25),
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
      backgroundColor: LucidiaColors.surfaceBright,
      contentTextStyle: const TextStyle(color: LucidiaColors.textPrimary),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: LucidiaColors.background,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      centerTitle: false,
      foregroundColor: LucidiaColors.textPrimary,
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: LucidiaColors.surface,
      selectedItemColor: LucidiaColors.teal,
      unselectedItemColor: LucidiaColors.textTertiary,
    ),
    dividerTheme: const DividerThemeData(color: LucidiaColors.border, thickness: 1),
    listTileTheme: const ListTileThemeData(
      tileColor: Colors.transparent,
      iconColor: LucidiaColors.teal,
      textColor: LucidiaColors.textPrimary,
      contentPadding: EdgeInsets.symmetric(horizontal: 0),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: LucidiaColors.teal,
    ),
    chipTheme: ChipThemeData(
      backgroundColor: LucidiaColors.surface,
      selectedColor: LucidiaColors.teal.withValues(alpha: 0.18),
      labelStyle: const TextStyle(color: LucidiaColors.textSecondary, fontSize: 12),
      side: const BorderSide(color: LucidiaColors.border),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    checkboxTheme: CheckboxThemeData(
      fillColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) return LucidiaColors.teal;
        return LucidiaColors.border;
      }),
      checkColor: WidgetStateProperty.all(Colors.white),
    ),
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

/// Premium gradient card decoration for highlight sections.
BoxDecoration lucidiaGradientDecoration({Color? accentColor}) {
  final accent = accentColor ?? LucidiaColors.teal;
  return BoxDecoration(
    gradient: LinearGradient(
      colors: [
        accent.withValues(alpha: 0.08),
        LucidiaColors.surfaceElevated,
      ],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
    borderRadius: BorderRadius.circular(14),
    border: Border.all(color: accent.withValues(alpha: 0.25), width: 1),
  );
}