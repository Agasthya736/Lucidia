import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ta.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ta'),
  ];

  /// The name of the application
  ///
  /// In en, this message translates to:
  /// **'Lucidia'**
  String get appName;

  /// Login screen heading
  ///
  /// In en, this message translates to:
  /// **'Welcome to Lucidia'**
  String get loginTitle;

  /// Login screen subtitle
  ///
  /// In en, this message translates to:
  /// **'AI-powered image analysis for educational insights'**
  String get loginSubtitle;

  /// Google sign-in button label
  ///
  /// In en, this message translates to:
  /// **'Continue with Google'**
  String get signInWithGoogle;

  /// Email sign-in button label
  ///
  /// In en, this message translates to:
  /// **'Sign In with Email'**
  String get signInWithEmail;

  /// Email input label
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get emailLabel;

  /// Password input label
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabel;

  /// Dashboard screen title
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get dashboardTitle;

  /// Dashboard metric: total scans
  ///
  /// In en, this message translates to:
  /// **'Total Scans'**
  String get totalScans;

  /// Dashboard metric: scans pending review
  ///
  /// In en, this message translates to:
  /// **'Pending Review'**
  String get pendingReview;

  /// Dashboard metric: finalized scans
  ///
  /// In en, this message translates to:
  /// **'Finalized'**
  String get finalized;

  /// Dashboard section heading for recent scans
  ///
  /// In en, this message translates to:
  /// **'Recent Scans'**
  String get recentScans;

  /// Empty state for no scans
  ///
  /// In en, this message translates to:
  /// **'No scans uploaded yet'**
  String get noScansYet;

  /// CTA button to start a new scan
  ///
  /// In en, this message translates to:
  /// **'Submit New Scan'**
  String get submitNewScan;

  /// Link to view all scans
  ///
  /// In en, this message translates to:
  /// **'View All'**
  String get viewAll;

  /// Scans list screen title
  ///
  /// In en, this message translates to:
  /// **'Scans & Reports'**
  String get scansAndReports;

  /// New CT scan screen title
  ///
  /// In en, this message translates to:
  /// **'New CT Study'**
  String get newCtStudy;

  /// External photo analysis screen title
  ///
  /// In en, this message translates to:
  /// **'Photo Analysis'**
  String get photoAnalysis;

  /// Submit button for external photo
  ///
  /// In en, this message translates to:
  /// **'Analyze Photograph'**
  String get analyzePhoto;

  /// Submit button for CT scan
  ///
  /// In en, this message translates to:
  /// **'Run AI Analysis'**
  String get runAnalysis;

  /// Profile screen title
  ///
  /// In en, this message translates to:
  /// **'Profile & Settings'**
  String get profileAndSettings;

  /// Quota card heading
  ///
  /// In en, this message translates to:
  /// **'Monthly Scan Quota'**
  String get monthlyQuota;

  /// Quota usage string
  ///
  /// In en, this message translates to:
  /// **'{used} of {total} scans used this month'**
  String scansUsed(int used, int total);

  /// Remaining scans count
  ///
  /// In en, this message translates to:
  /// **'{count} remaining'**
  String remaining(int count);

  /// Log out button
  ///
  /// In en, this message translates to:
  /// **'Log Out'**
  String get logOut;

  /// Dark mode toggle label
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkMode;

  /// Light mode toggle label
  ///
  /// In en, this message translates to:
  /// **'Light Mode'**
  String get lightMode;

  /// Pipeline info card heading on profile
  ///
  /// In en, this message translates to:
  /// **'AI Pipeline'**
  String get aiPipeline;

  /// Compliance card heading
  ///
  /// In en, this message translates to:
  /// **'Legal & Disclaimers'**
  String get legalAndDisclaimers;

  /// Privacy policy menu item
  ///
  /// In en, this message translates to:
  /// **'Privacy Policy & Data Handling'**
  String get privacyPolicy;

  /// Disclaimer menu item
  ///
  /// In en, this message translates to:
  /// **'Educational Use Disclaimer'**
  String get educationalDisclaimer;

  /// Full educational disclaimer text
  ///
  /// In en, this message translates to:
  /// **'This AI report is for informational and educational purposes only. It is NOT a medical diagnosis. Always consult a qualified doctor or healthcare professional before making any health decisions.'**
  String get educationalDisclaimerBody;

  /// Report viewer screen title
  ///
  /// In en, this message translates to:
  /// **'Scan Report'**
  String get reportViewerTitle;

  /// PDF download button
  ///
  /// In en, this message translates to:
  /// **'Download PDF Report'**
  String get downloadPdf;

  /// Short AI disclaimer shown at top of report
  ///
  /// In en, this message translates to:
  /// **'⚠️ Notice: This analysis is AI-generated for informational guidance only and is NOT a definitive medical diagnosis. Please consult a qualified doctor or healthcare professional for clinical evaluation.'**
  String get aiNoticeShort;

  /// Urgency banner: urgent level
  ///
  /// In en, this message translates to:
  /// **'⚠️  Significant Finding Detected'**
  String get urgentFinding;

  /// Urgency banner: follow-up level
  ///
  /// In en, this message translates to:
  /// **'🔔  Follow-Up Recommended'**
  String get followUpRecommended;

  /// Urgency banner: routine level
  ///
  /// In en, this message translates to:
  /// **'✅  No Significant Concerns'**
  String get noSignificantConcerns;

  /// Report section label
  ///
  /// In en, this message translates to:
  /// **'SUSPECTED CONDITION / FINDING'**
  String get suspectedCondition;

  /// Patient-friendly summary section label
  ///
  /// In en, this message translates to:
  /// **'IN PLAIN LANGUAGE'**
  String get inPlainEnglish;

  /// Recommendations section label
  ///
  /// In en, this message translates to:
  /// **'WHAT TO DO NEXT'**
  String get whatToDoNext;

  /// Q&A chat section label
  ///
  /// In en, this message translates to:
  /// **'ASK QUESTIONS ABOUT THIS REPORT'**
  String get askQuestionsAboutReport;

  /// Q&A input hint
  ///
  /// In en, this message translates to:
  /// **'Type your question here...'**
  String get typeYourQuestion;

  /// Review confirm dialog title
  ///
  /// In en, this message translates to:
  /// **'Mark as Reviewed'**
  String get markAsReviewed;

  /// Review confirm dialog submit button
  ///
  /// In en, this message translates to:
  /// **'Confirm & Download Report'**
  String get confirmAndDownload;

  /// Generic cancel button
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// Generic retry button
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// Generic loading text
  ///
  /// In en, this message translates to:
  /// **'Loading...'**
  String get loading;

  /// Generic error label
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get error;

  /// Pipeline status screen title
  ///
  /// In en, this message translates to:
  /// **'Triage Pipeline'**
  String get pipelineTitle;

  /// Pipeline error state title
  ///
  /// In en, this message translates to:
  /// **'Analysis Failed'**
  String get analysisFailed;

  /// Button to navigate back to dashboard
  ///
  /// In en, this message translates to:
  /// **'Back to Dashboard'**
  String get backToDashboard;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ta'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ta':
      return AppLocalizationsTa();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
