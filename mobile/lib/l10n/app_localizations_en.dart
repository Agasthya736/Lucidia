// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'Lucidia';

  @override
  String get loginTitle => 'Welcome to Lucidia';

  @override
  String get loginSubtitle =>
      'AI-powered image analysis for educational insights';

  @override
  String get signInWithGoogle => 'Continue with Google';

  @override
  String get signInWithEmail => 'Sign In with Email';

  @override
  String get emailLabel => 'Email';

  @override
  String get passwordLabel => 'Password';

  @override
  String get dashboardTitle => 'Dashboard';

  @override
  String get totalScans => 'Total Scans';

  @override
  String get pendingReview => 'Pending Review';

  @override
  String get finalized => 'Finalized';

  @override
  String get recentScans => 'Recent Scans';

  @override
  String get noScansYet => 'No scans uploaded yet';

  @override
  String get submitNewScan => 'Submit New Scan';

  @override
  String get viewAll => 'View All';

  @override
  String get scansAndReports => 'Scans & Reports';

  @override
  String get newCtStudy => 'New CT Study';

  @override
  String get photoAnalysis => 'Photo Analysis';

  @override
  String get analyzePhoto => 'Analyze Photograph';

  @override
  String get runAnalysis => 'Run AI Analysis';

  @override
  String get profileAndSettings => 'Profile & Settings';

  @override
  String get monthlyQuota => 'Monthly Scan Quota';

  @override
  String scansUsed(int used, int total) {
    return '$used of $total scans used this month';
  }

  @override
  String remaining(int count) {
    return '$count remaining';
  }

  @override
  String get logOut => 'Log Out';

  @override
  String get darkMode => 'Dark Mode';

  @override
  String get lightMode => 'Light Mode';

  @override
  String get aiPipeline => 'AI Pipeline';

  @override
  String get legalAndDisclaimers => 'Legal & Disclaimers';

  @override
  String get privacyPolicy => 'Privacy Policy & Data Handling';

  @override
  String get educationalDisclaimer => 'Educational Use Disclaimer';

  @override
  String get educationalDisclaimerBody =>
      'This AI report is for informational and educational purposes only. It is NOT a medical diagnosis. Always consult a qualified doctor or healthcare professional before making any health decisions.';

  @override
  String get reportViewerTitle => 'Scan Report';

  @override
  String get downloadPdf => 'Download PDF Report';

  @override
  String get aiNoticeShort =>
      '⚠️ Notice: This analysis is AI-generated for informational guidance only and is NOT a definitive medical diagnosis. Please consult a qualified doctor or healthcare professional for clinical evaluation.';

  @override
  String get urgentFinding => '⚠️  Significant Finding Detected';

  @override
  String get followUpRecommended => '🔔  Follow-Up Recommended';

  @override
  String get noSignificantConcerns => '✅  No Significant Concerns';

  @override
  String get suspectedCondition => 'SUSPECTED CONDITION / FINDING';

  @override
  String get inPlainEnglish => 'IN PLAIN LANGUAGE';

  @override
  String get whatToDoNext => 'WHAT TO DO NEXT';

  @override
  String get askQuestionsAboutReport => 'ASK QUESTIONS ABOUT THIS REPORT';

  @override
  String get typeYourQuestion => 'Type your question here...';

  @override
  String get markAsReviewed => 'Mark as Reviewed';

  @override
  String get confirmAndDownload => 'Confirm & Download Report';

  @override
  String get cancel => 'Cancel';

  @override
  String get retry => 'Retry';

  @override
  String get loading => 'Loading...';

  @override
  String get error => 'Error';

  @override
  String get pipelineTitle => 'Triage Pipeline';

  @override
  String get analysisFailed => 'Analysis Failed';

  @override
  String get backToDashboard => 'Back to Dashboard';
}
