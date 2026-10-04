import 'package:flutter/material.dart';
import '../theme/lucidia_theme.dart';

export '../theme/lucidia_theme.dart';

class LucidiaColors {
  // Aliased to AppColors single source of truth (adaptive to Light/Dark mode)
  static Color get background => AppColors.background;
  static Color get surface => AppColors.surface;
  static Color get surfaceElevated => AppColors.surfaceElevated;
  static Color get surfaceBright => AppColors.surfaceBright;
  static Color get border => AppColors.border;
  static Color get borderSubtle => AppColors.borderSubtle;

  static Color get textPrimary => AppColors.textPrimary;
  static Color get textSecondary => AppColors.textSecondary;
  static Color get textTertiary => AppColors.textMuted;

  static const Color teal = AppColors.teal;
  static const Color tealGlow = AppColors.tealGlow;
  static const Color primary = AppColors.primary;
  static const Color violet = AppColors.violet;
  static const Color violetGlow = Color(0xFF6D28D9);
  static const Color cyan = AppColors.cyan;

  static const Color error = AppColors.error;
  static const Color errorDim = Color(0xFFDC2626);
  static const Color success = AppColors.success;
  static const Color successDim = Color(0xFF16A34A);
  static const Color warning = AppColors.warning;
  static const Color warningDim = Color(0xFFD97706);
}

ThemeData buildLucidiaTheme() {
  return LucidiaTheme.light;
}