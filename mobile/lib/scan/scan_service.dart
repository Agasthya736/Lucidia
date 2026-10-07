import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:file_picker/file_picker.dart';

class ScanService {
  static const String baseUrl = "https://lucidia-backend-794373598684.asia-south1.run.app";
  static const String byokStorageKey = "custom_gemini_api_key";
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  Future<String> _authHeader() async {
    final token = await _storage.read(key: 'jwt_token');
    if (token == null) throw Exception('Not logged in');
    return 'Bearer $token';
  }

  Future<String?> getCustomApiKey() async {
    return await _storage.read(key: byokStorageKey);
  }

  Future<void> setCustomApiKey(String key) async {
    if (key.trim().isEmpty) {
      await _storage.delete(key: byokStorageKey);
    } else {
      await _storage.write(key: byokStorageKey, value: key.trim());
    }
  }

  Future<void> clearCustomApiKey() async {
    await _storage.delete(key: byokStorageKey);
  }

  Future<Map<String, dynamic>> submitScanSeries(
    List<PlatformFile> files, {
    String modality = 'CT_SERIES',
    String? clinicalNotes,
  }) async {
    if (files.isEmpty) throw Exception('No images selected.');

    final auth = await _authHeader();
    final customKey = await getCustomApiKey();

    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl/api/scans'));
    request.headers['Authorization'] = auth;
    if (customKey != null && customKey.isNotEmpty) {
      request.headers['X-Gemini-Api-Key'] = customKey;
    }

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

    if (streamed.statusCode == 400) {
      try {
        final json = jsonDecode(body);
        throw Exception(json['message'] ?? 'Responsible AI rejection: Image not suitable for clinical evaluation.');
      } catch (e) {
        if (e is Exception && e.toString().contains('Responsible AI')) rethrow;
        throw Exception('Submission rejected (400): $body');
      }
    }

    if (streamed.statusCode == 429) {
      final json = jsonDecode(body);
      throw Exception(json['message'] ?? 'Free tier monthly scan quota reached. Add your Gemini API key in Settings.');
    }

    if (streamed.statusCode != 200 && streamed.statusCode != 202) {
      throw Exception('Submit failed (${streamed.statusCode}): $body');
    }
    return jsonDecode(body);
  }

  Future<Map<String, dynamic>> submitScan(List<int> bytes, String filename, {String modality = 'CT_SERIES', String? clinicalNotes}) async {
    final file = PlatformFile(name: filename, size: bytes.length, bytes: Uint8List.fromList(bytes));
    return submitScanSeries([file], modality: modality, clinicalNotes: clinicalNotes);
  }

  Future<Map<String, dynamic>> getQuota() async {
    final auth = await _authHeader();
    final customKey = await getCustomApiKey();

    final headers = {'Authorization': auth};
    if (customKey != null && customKey.isNotEmpty) {
      headers['X-Gemini-Api-Key'] = customKey;
    }

    final response = await http.get(
      Uri.parse('$baseUrl/api/scans/quota'),
      headers: headers,
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
    final customKey = await getCustomApiKey();

    final headers = {
      'Authorization': auth,
      'Content-Type': 'application/json',
    };
    if (customKey != null && customKey.isNotEmpty) {
      headers['X-Gemini-Api-Key'] = customKey;
    }

    final response = await http.post(
      Uri.parse('$baseUrl/api/scans/$id/chat'),
      headers: headers,
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
}