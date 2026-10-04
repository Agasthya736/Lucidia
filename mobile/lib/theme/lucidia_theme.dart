import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Centralized clinical color tokens for Lucidia (VeriRad).
/// Adaptive between Clinical Light mode and Radiology Workstation Dark mode.
class AppColors {
  static bool get isDark => LucidiaTheme.isDarkMode;

  // --- Canvas & Surfaces ---
  static Color get background => isDark ? const Color(0xFF070D18) : const Color(0xFFF8FAFC);
  static Color get surface => isDark ? const Color(0xFF0C1425) : const Color(0xFFFFFFFF);
  static Color get surfaceElevated => isDark ? const Color(0xFF111D35) : const Color(0xFFF1F5F9);
  static Color get surfaceBright => isDark ? const Color(0xFF18233C) : const Color(0xFFFFFFFF);
  static Color get border => isDark ? const Color(0xFF1E2F52) : const Color(0xFFE2E8F0);
  static Color get cardBorder => isDark ? const Color(0xFF253456) : const Color(0xFFCBD5E1);
  static Color get borderSubtle => isDark ? const Color(0xFF15223D) : const Color(0xFFF1F5F9);

  // --- Typography ---
  static Color get textPrimary => isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0F172A);
  static Color get textSecondary => isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569);
  static Color get textMuted => isDark ? const Color(0xFF64748B) : const Color(0xFF64748B);

  // --- Brand & Clinical Accents ---
  static const Color teal = Color(0xFF0D9488);
  static const Color tealLight = Color(0xFF14B8A6);
  static const Color tealGlow = Color(0xFF0F766E);
  static const Color primary = Color(0xFF2563EB); // Clinical Sapphire Blue
  static const Color violet = Color(0xFF7C3AED);
  static const Color cyan = Color(0xFF0891B2);

  // --- Categorical Urgency & Severity Tokens ---
  static const Color urgencyRoutine = Color(0xFF0D9488);
  static Color get urgencyRoutineBg => isDark ? const Color(0xFF0B2421) : const Color(0xFFCCFBF1);

  static const Color urgencyFollowUp = Color(0xFFD97706);
  static Color get urgencyFollowUpBg => isDark ? const Color(0xFF2E1C07) : const Color(0xFFFEF3C7);

  static const Color urgencyUrgent = Color(0xFFDC2626);
  static Color get urgencyUrgentBg => isDark ? const Color(0xFF330E12) : const Color(0xFFFEE2E2);

  static const Color urgencyNeutral = Color(0xFF64748B);
  static Color get urgencyNeutralBg => isDark ? const Color(0xFF141C2E) : const Color(0xFFF1F5F9);

  // --- Feedback & State ---
  static const Color error = Color(0xFFDC2626);
  static const Color warning = Color(0xFFD97706);
  static const Color success = Color(0xFF16A34A);
}

/// Categorical triage and findings urgency levels.
enum UrgencyLevel {
  routine,
  followUpRecommended,
  urgent;

  static UrgencyLevel fromString(String? value) {
    if (value == null || value.trim().isEmpty) return UrgencyLevel.routine;
    final normalized = value.trim().toUpperCase().replaceAll(' ', '_').replaceAll('-', '_');
    if (normalized.contains('URGENT') || normalized.contains('CRITICAL')) {
      return UrgencyLevel.urgent;
    }
    if (normalized.contains('FOLLOW') || normalized.contains('RECOMMEND') || normalized.contains('WATCH')) {
      return UrgencyLevel.followUpRecommended;
    }
    return UrgencyLevel.routine;
  }

  Color get color {
    switch (this) {
      case UrgencyLevel.urgent:
        return AppColors.urgencyUrgent;
      case UrgencyLevel.followUpRecommended:
        return AppColors.urgencyFollowUp;
      case UrgencyLevel.routine:
        return AppColors.urgencyRoutine;
    }
  }

  Color get backgroundColor {
    switch (this) {
      case UrgencyLevel.urgent:
        return AppColors.urgencyUrgentBg;
      case UrgencyLevel.followUpRecommended:
        return AppColors.urgencyFollowUpBg;
      case UrgencyLevel.routine:
        return AppColors.urgencyRoutineBg;
    }
  }

  IconData get icon {
    switch (this) {
      case UrgencyLevel.urgent:
        return Icons.error_outline;
      case UrgencyLevel.followUpRecommended:
        return Icons.schedule_outlined;
      case UrgencyLevel.routine:
        return Icons.check_circle_outline;
    }
  }

  String get label {
    switch (this) {
      case UrgencyLevel.urgent:
        return 'URGENT';
      case UrgencyLevel.followUpRecommended:
        return 'FOLLOW-UP RECOMMENDED';
      case UrgencyLevel.routine:
        return 'ROUTINE';
    }
  }

  String get description {
    switch (this) {
      case UrgencyLevel.urgent:
        return 'Significant focal abnormality detected. Immediate clinical review required.';
      case UrgencyLevel.followUpRecommended:
        return 'Lesion identified requiring interval radiological monitoring or correlation.';
      case UrgencyLevel.routine:
        return 'Visualized anatomy unremarkable. Standard clinical follow-up.';
    }
  }
}

/// Reusable urgency badge component with consistent styling app-wide.
class UrgencyBadge extends StatelessWidget {
  final UrgencyLevel level;
  final bool showIcon;
  final double fontSize;
  final EdgeInsets? padding;
  final bool compact;

  const UrgencyBadge({
    super.key,
    required this.level,
    this.showIcon = true,
    this.fontSize = 12.0,
    this.padding,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final c = level.color;
    final bg = level.backgroundColor;

    return Container(
      padding: padding ??
          EdgeInsets.symmetric(
            horizontal: compact ? 8 : 12,
            vertical: compact ? 3 : 6,
          ),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.withValues(alpha: 0.5), width: 1.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (showIcon) ...[
            Icon(level.icon, color: c, size: fontSize + 2),
            SizedBox(width: compact ? 4 : 6),
          ],
          Text(
            compact && level == UrgencyLevel.followUpRecommended ? 'FOLLOW-UP' : level.label,
            style: TextStyle(
              color: c,
              fontSize: fontSize,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.6,
            ),
          ),
        ],
      ),
    );
  }
}

/// Clinical theme definitions for Lucidia with reactive Light & Dark mode support.
class LucidiaTheme {
  /// Global theme mode notifier. Defaults to Clinical Light mode for high readability.
  static final ValueNotifier<ThemeMode> themeNotifier = ValueNotifier<ThemeMode>(ThemeMode.light);

  static bool get isDarkMode => themeNotifier.value == ThemeMode.dark;

  static void toggleTheme() {
    themeNotifier.value = isDarkMode ? ThemeMode.light : ThemeMode.dark;
  }

  static ThemeData get light => buildLightTheme();
  static ThemeData get dark => buildDarkTheme();

  static ThemeData buildLightTheme() {
    final base = ThemeData.light(useMaterial3: true);
    final textTheme = GoogleFonts.interTextTheme(base.textTheme).apply(
      bodyColor: const Color(0xFF0F172A),
      displayColor: const Color(0xFF0F172A),
    );

    const bg = Color(0xFFF8FAFC);
    const surf = Color(0xFFFFFFFF);
    const surfElevated = Color(0xFFF1F5F9);
    const borderCol = Color(0xFFE2E8F0);
    const textPrimaryCol = Color(0xFF0F172A);
    const textSecondaryCol = Color(0xFF475569);

    return base.copyWith(
      scaffoldBackgroundColor: bg,
      canvasColor: surf,
      cardColor: surfElevated,
      dialogTheme: const DialogThemeData(
        backgroundColor: surf,
        surfaceTintColor: Colors.transparent,
      ),
      colorScheme: base.colorScheme.copyWith(
        surface: surf,
        primary: AppColors.primary,
        secondary: AppColors.teal,
        error: AppColors.error,
        onSurface: textPrimaryCol,
        onPrimary: Colors.white,
      ),
      textTheme: textTheme,
      splashFactory: InkSparkle.splashFactory,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surf,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderCol),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderCol),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.primary, width: 1.5),
        ),
        labelStyle: const TextStyle(color: textSecondaryCol),
        hintStyle: const TextStyle(color: Color(0xFF94A3B8)),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.25),
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: textPrimaryCol,
          side: const BorderSide(color: borderCol),
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: textSecondaryCol),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: const Color(0xFF1E293B),
        contentTextStyle: const TextStyle(color: Colors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: surf,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        foregroundColor: textPrimaryCol,
        iconTheme: IconThemeData(color: textPrimaryCol),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: surf,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: Color(0xFF64748B),
      ),
      dividerTheme: const DividerThemeData(color: borderCol, thickness: 1),
      listTileTheme: const ListTileThemeData(
        tileColor: Colors.transparent,
        iconColor: AppColors.primary,
        textColor: textPrimaryCol,
        contentPadding: EdgeInsets.zero,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.primary,
      ),
    );
  }

  static ThemeData buildDarkTheme() {
    final base = ThemeData.dark(useMaterial3: true);
    final textTheme = GoogleFonts.interTextTheme(base.textTheme).apply(
      bodyColor: const Color(0xFFF8FAFC),
      displayColor: const Color(0xFFF8FAFC),
    );

    const bg = Color(0xFF070D18);
    const surf = Color(0xFF0C1425);
    const surfElevated = Color(0xFF111D35);
    const borderCol = Color(0xFF1E2F52);
    const textPrimaryCol = Color(0xFFF8FAFC);
    const textSecondaryCol = Color(0xFF94A3B8);

    return base.copyWith(
      scaffoldBackgroundColor: bg,
      canvasColor: surf,
      cardColor: surfElevated,
      dialogTheme: const DialogThemeData(
        backgroundColor: surfElevated,
        surfaceTintColor: Colors.transparent,
      ),
      colorScheme: base.colorScheme.copyWith(
        surface: surf,
        primary: AppColors.primary,
        secondary: AppColors.teal,
        error: AppColors.error,
        onSurface: textPrimaryCol,
        onPrimary: Colors.white,
      ),
      textTheme: textTheme,
      splashFactory: InkSparkle.splashFactory,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surf,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderCol),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderCol),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.primary, width: 1.5),
        ),
        labelStyle: const TextStyle(color: textSecondaryCol),
        hintStyle: const TextStyle(color: Color(0xFF64748B)),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.25),
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: textPrimaryCol,
          side: const BorderSide(color: borderCol),
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: textSecondaryCol),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: const Color(0xFF18233C),
        contentTextStyle: const TextStyle(color: Colors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: bg,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        foregroundColor: textPrimaryCol,
        iconTheme: IconThemeData(color: textPrimaryCol),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: surf,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: Color(0xFF64748B),
      ),
      dividerTheme: const DividerThemeData(color: borderCol, thickness: 1),
      listTileTheme: const ListTileThemeData(
        tileColor: Colors.transparent,
        iconColor: AppColors.primary,
        textColor: textPrimaryCol,
        contentPadding: EdgeInsets.zero,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.primary,
      ),
    );
  }
}

/// Reusable card decoration - dynamic according to Light/Dark theme.
BoxDecoration lucidiaCardDecoration({Color? borderColor}) {
  return BoxDecoration(
    color: AppColors.surfaceElevated,
    borderRadius: BorderRadius.circular(14),
    border: Border.all(color: borderColor ?? AppColors.cardBorder, width: borderColor != null ? 1.5 : 1),
  );
}

/// Premium gradient card decoration for highlight sections.
BoxDecoration lucidiaGradientDecoration({Color? accentColor}) {
  final accent = accentColor ?? AppColors.primary;
  return BoxDecoration(
    gradient: LinearGradient(
      colors: [
        accent.withValues(alpha: 0.08),
        AppColors.surfaceElevated,
      ],
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
    ),
    borderRadius: BorderRadius.circular(14),
    border: Border.all(color: accent.withValues(alpha: 0.25), width: 1),
  );
}
