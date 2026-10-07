// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Tamil (`ta`).
class AppLocalizationsTa extends AppLocalizations {
  AppLocalizationsTa([String locale = 'ta']) : super(locale);

  @override
  String get appName => 'லூசிடியா';

  @override
  String get loginTitle => 'லூசிடியாவிற்கு வரவேற்கிறோம்';

  @override
  String get loginSubtitle =>
      'கல்வி நோக்கங்களுக்காக AI-இயக்கப்படும் படவிசை பகுப்பாய்வு';

  @override
  String get signInWithGoogle => 'Google மூலம் தொடரவும்';

  @override
  String get signInWithEmail => 'மின்னஞ்சல் மூலம் உள்நுழைக';

  @override
  String get emailLabel => 'மின்னஞ்சல்';

  @override
  String get passwordLabel => 'கடவுச்சொல்';

  @override
  String get dashboardTitle => 'டாஷ்போர்டு';

  @override
  String get totalScans => 'மொத்த ஸ்கேன்கள்';

  @override
  String get pendingReview => 'நிலுவையில் உள்ள மதிப்பாய்வு';

  @override
  String get finalized => 'இறுதிசெய்யப்பட்டது';

  @override
  String get recentScans => 'சமீபத்திய ஸ்கேன்கள்';

  @override
  String get noScansYet => 'இன்னும் ஸ்கேன்கள் பதிவேற்றப்படவில்லை';

  @override
  String get submitNewScan => 'புதிய ஸ்கேன் சமர்பிக்கவும்';

  @override
  String get viewAll => 'அனைத்தையும் காண்க';

  @override
  String get scansAndReports => 'ஸ்கேன்கள் & அறிக்கைகள்';

  @override
  String get newCtStudy => 'புதிய CT ஆய்வு';

  @override
  String get photoAnalysis => 'புகைப்படப் பகுப்பாய்வு';

  @override
  String get analyzePhoto => 'புகைப்படத்தை பகுப்பாய்வு செய்';

  @override
  String get runAnalysis => 'AI பகுப்பாய்வை இயக்கு';

  @override
  String get profileAndSettings => 'சுயவிவரம் & அமைப்புகள்';

  @override
  String get monthlyQuota => 'மாதாந்திர ஸ்கேன் ஒதுக்கீடு';

  @override
  String scansUsed(int used, int total) {
    return 'இம்மாதம் $total இல் $used ஸ்கேன்கள் பயன்படுத்தப்பட்டன';
  }

  @override
  String remaining(int count) {
    return '$count எஞ்சியுள்ளது';
  }

  @override
  String get logOut => 'வெளியேறு';

  @override
  String get darkMode => 'இருண்ட முறை';

  @override
  String get lightMode => 'ஒளி முறை';

  @override
  String get aiPipeline => 'AI பைப்லைன்';

  @override
  String get legalAndDisclaimers => 'சட்டப் பொறுப்புத் துறப்புகள்';

  @override
  String get privacyPolicy => 'தனியுரிமைக் கொள்கை & தரவு கையாளுதல்';

  @override
  String get educationalDisclaimer => 'கல்வி பயன்பாட்டு மறுப்பு';

  @override
  String get educationalDisclaimerBody =>
      'இந்த AI அறிக்கை தகவல் மற்றும் கல்வி நோக்கங்களுக்காக மட்டுமே. இது மருத்துவ கண்டறிதல் அல்ல. எந்த உடல்நலன் தொடர்பான முடிவுகளையும் எடுப்பதற்கு முன் தகுதிவாய்ந்த மருத்துவரை அல்லது சுகாதார நிபுணரை அணுகவும்.';

  @override
  String get reportViewerTitle => 'ஸ்கேன் அறிக்கை';

  @override
  String get downloadPdf => 'PDF அறிக்கையைப் பதிவிறக்கு';

  @override
  String get aiNoticeShort =>
      '⚠️ அறிவிப்பு: இந்த பகுப்பாய்வு AI-உருவாக்கியது, தகவல் வழிகாட்டுதலுக்காக மட்டுமே. இது உறுதியான மருத்துவ கண்டறிதல் அல்ல. மருத்துவ மதிப்பீட்டிற்கு தகுதிவாய்ந்த மருத்துவரை அணுகவும்.';

  @override
  String get urgentFinding => '⚠️  முக்கியமான கண்டுபிடிப்பு கண்டறியப்பட்டது';

  @override
  String get followUpRecommended =>
      '🔔  தொடர்ச்சியான சோதனை பரிந்துரைக்கப்படுகிறது';

  @override
  String get noSignificantConcerns => '✅  குறிப்பிடத்தக்க கவலைகள் இல்லை';

  @override
  String get suspectedCondition => 'சந்தேகிக்கப்படும் நிலை / கண்டுபிடிப்பு';

  @override
  String get inPlainEnglish => 'எளிய மொழியில்';

  @override
  String get whatToDoNext => 'அடுத்து என்ன செய்வது';

  @override
  String get askQuestionsAboutReport =>
      'இந்த அறிக்கையைப் பற்றி கேள்விகள் கேளுங்கள்';

  @override
  String get typeYourQuestion => 'இங்கே உங்கள் கேள்வியை தட்டச்சு செய்யவும்...';

  @override
  String get markAsReviewed => 'மதிப்பாய்வு செய்யப்பட்டதாக குறிக்கவும்';

  @override
  String get confirmAndDownload => 'உறுதிப்படுத்தி அறிக்கையைப் பதிவிறக்கு';

  @override
  String get cancel => 'ரத்து செய்';

  @override
  String get retry => 'மீண்டும் முயற்சி';

  @override
  String get loading => 'ஏற்றுகிறது...';

  @override
  String get error => 'பிழை';

  @override
  String get pipelineTitle => 'ட்ரியாஜ் பைப்லைன்';

  @override
  String get analysisFailed => 'பகுப்பாய்வு தோல்வியடைந்தது';

  @override
  String get backToDashboard => 'டாஷ்போர்டுக்கு திரும்பு';
}
