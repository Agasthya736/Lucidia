import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/constants/app_links.dart';

Future<void> openPrivacyPolicy(BuildContext context) async {
  final uri = Uri.parse(AppLinks.privacyPolicyUrl);
  if (uri.host.contains('TODO-ADD-PUBLIC-PRIVACY-POLICY-URL')) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('The privacy policy link is not configured yet.')),
    );
    return;
  }

  final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
  if (!opened && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Could not open the privacy policy.')),
    );
  }
}
