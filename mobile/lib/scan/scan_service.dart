import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:file_picker/file_picker.dart';

class ValidationException implements Exception {
  final String message;
  ValidationException(this.message);

  @override
  String toString() => message;
}

class ScanSubmissionException implements Exception {
  const ScanSubmissionException({
    required this.statusCode,
    required this.message,
    this.code,
  });

  final int statusCode;
  final String message;
  final String? code;

  @override
  String toString() => message;
}

class ConsentRequiredException extends ScanSubmissionException {
  const ConsentRequiredException()
      : super(
          statusCode: 403,
          code: 'CONSENT_REQUIRED',
          message: 'Please accept the consent screen first',
        );
}

class ScanService {
  static const String baseUrl = "https://lucidia-backend-794373598684.asia-south1.run.app";
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  Future<String> _authHeader() async {
    final token = await _storage.read(key: 'jwt_token');
    if (token == null) throw Exception('Not logged in');
    return 'Bearer $token';
  }

  Future<Map<String, dynamic>> submitScanSeries(
    List<PlatformFile> files, {
    String modality = 'CT_SERIES',
    String? clinicalNotes,
  }) async {
    if (files.isEmpty) throw Exception('No images selected.');

    final auth = await _authHeader();

    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl/api/scans'));
    request.headers['Authorization'] = auth;

    request.fields['modality'] = modality;
    if (clinicalNotes != null && clinicalNotes.trim().isNotEmpty) {
      request.fields['clinicalNotes'] = clinicalNotes.trim();
    }

    for (final file in files) {
      if (file.bytes == null) continue;
      String ext = file.name.split('.').last.toLowerCase();
      String subtype = switch (ext) {
        'png' => 'png',
        'webp' => 'webp',
        _ => 'jpeg',
      };

      request.files.add(http.MultipartFile.fromBytes(
        'images',
        file.bytes!,
        filename: file.name,
        contentType: MediaType('image', subtype),
      ));
    }

    final streamed = await request.send();
    final body = await streamed.stream.bytesToString();

    if (streamed.statusCode != 200 && streamed.statusCode != 202) {
      final error = parseSubmissionError(streamed.statusCode, body);
      if (error is ConsentRequiredException) throw error;
      if (error.statusCode == 422) throw ValidationException(error.message);
      throw error;
    }
    return jsonDecode(body);
  }

  static ScanSubmissionException parseSubmissionError(int statusCode, String body) {
    String? message;
    String? code;
    if (body.trim().isNotEmpty) {
      try {
        final decoded = jsonDecode(body);
        if (decoded is Map<String, dynamic>) {
          message = decoded['message']?.toString();
          code = decoded['code']?.toString();
        }
      } on FormatException {
        message = body.trim();
      }
    }

    message ??= switch (statusCode) {
      400 => 'Image not suitable for analysis.',
      403 => 'Please accept the consent screen first',
      422 => 'Image does not meet clinical requirements.',
      429 => 'Monthly scan quota reached. Please try again next month.',
      _ => 'The server could not process your request. Please try again.',
    };
    if (statusCode == 403 &&
        (code == 'CONSENT_REQUIRED' ||
            message.toLowerCase().contains('consent'))) {
      return const ConsentRequiredException();
    }
    return ScanSubmissionException(
      statusCode: statusCode,
      code: code,
      message: message.trim().isEmpty
          ? 'The server could not process your request. Please try again.'
          : message,
    );
  }

  Future<Map<String, dynamic>> submitScan(List<int> bytes, String filename, {String modality = 'CT_SERIES', String? clinicalNotes}) async {
    final file = PlatformFile(name: filename, size: bytes.length, bytes: Uint8List.fromList(bytes));
    return submitScanSeries([file], modality: modality, clinicalNotes: clinicalNotes);
  }

  Future<Map<String, dynamic>> getQuota() async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/scans/quota'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to load quota (${response.statusCode})');
    }
    return jsonDecode(response.body);
  }

  Future<Map<String, dynamic>> getScan(String id) async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/scans/$id'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to load scan (${response.statusCode})');
    }
    return jsonDecode(response.body);
  }

  Future<List<Map<String, dynamic>>> listScans() async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/scans'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to load scans (${response.statusCode})');
    }
    return List<Map<String, dynamic>>.from(jsonDecode(response.body));
  }

  Future<Map<String, dynamic>> finalizeScan({
    required String id,
    required String reviewerName,
    required String reviewerCredentials,
    String? notes,
  }) async {
    final auth = await _authHeader();
    final response = await http.patch(
      Uri.parse('$baseUrl/api/scans/$id/finalize'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'reviewerName': reviewerName,
        'reviewerCredentials': reviewerCredentials,
        'notes': notes ?? '',
      }),
    );
    if (response.statusCode != 200) {
      throw Exception('Finalize failed (${response.statusCode}): ${response.body}');
    }
    return jsonDecode(response.body);
  }

  Future<Uint8List> fetchSliceImage(String id, int sliceIndex) async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/scans/$id/slices/$sliceIndex'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to load slice image (${response.statusCode})');
    }
    return response.bodyBytes;
  }

  Future<Uint8List> fetchImageBytes(String id) async {
    return fetchSliceImage(id, 0);
  }

  Future<Uint8List> downloadReportPdf(String id) async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/scans/$id/report.pdf'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Download failed (${response.statusCode}): ${response.body}');
    }
    return response.bodyBytes;
  }

  Future<String> askQuestion(String id, String question) async {
    final auth = await _authHeader();
    final response = await http.post(
      Uri.parse('$baseUrl/api/scans/$id/chat'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'question': question}),
    );

    if (response.statusCode != 200) {
      throw Exception('Failed to get answer (${response.statusCode})');
    }

    final data = jsonDecode(response.body);
    return data['answer'] ?? 'No answer received.';
  }

  Future<void> deleteScan(String id) async {
    final auth = await _authHeader();
    final response = await http.delete(
      Uri.parse('$baseUrl/api/scans/$id'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 204) {
      throw Exception('Delete failed (${response.statusCode})');
    }
  }

  /// Submit a "was this result helpful?" rating (1-5) with optional comment.
  Future<void> submitFeedback(String scanId, int rating, {String? comment}) async {
    final auth = await _authHeader();
    final response = await http.post(
      Uri.parse('$baseUrl/api/scans/$scanId/feedback'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'rating': rating,
        if (comment != null && comment.trim().isNotEmpty) 'comment': comment.trim(),
      }),
    );
    if (response.statusCode != 200) {
      throw Exception('Feedback submission failed (${response.statusCode})');
    }
  }

  /// Check whether the current user has accepted the consent screen.
  Future<Map<String, dynamic>> checkConsent() async {
    final auth = await _authHeader();
    final response = await http.get(
      Uri.parse('$baseUrl/api/consent'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Could not check consent status (${response.statusCode})');
    }
    return jsonDecode(response.body);
  }

  /// Record the user's consent decision (accepted = true/false).
  Future<void> recordConsent(bool accepted) async {
    final auth = await _authHeader();
    final response = await http.post(
      Uri.parse('$baseUrl/api/consent'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'accepted': accepted}),
    );
    if (response.statusCode != 200) {
      throw Exception('Consent recording failed (${response.statusCode})');
    }
  }

  /// Delete the current user's account and all their data.
  Future<void> deleteAccount() async {
    final auth = await _authHeader();
    final response = await http.delete(
      Uri.parse('$baseUrl/api/me'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 204) {
      throw Exception('Account deletion failed (${response.statusCode})');
    }
  }

  /// Update the user's data retention preference (null = keep until deleted).
  Future<void> updateRetention(int? retentionDays) async {
    final auth = await _authHeader();
    final response = await http.put(
      Uri.parse('$baseUrl/api/me/retention'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'retentionDays': retentionDays}),
    );
    if (response.statusCode != 200) {
      throw Exception('Retention update failed (${response.statusCode})');
    }
  }

  /// Update the user's preferred language.
  Future<void> updateLanguage(String languageCode) async {
    final auth = await _authHeader();
    final response = await http.put(
      Uri.parse('$baseUrl/api/me/language'),
      headers: {
        'Authorization': auth,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'languageCode': languageCode}),
    );
    if (response.statusCode != 200) {
      throw Exception('Language update failed (${response.statusCode})');
    }
  }
}