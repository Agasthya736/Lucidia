import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ScanService {
  static const String baseUrl = "http://10.209.146.110:8080";
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  Future<String> _authHeader() async {
    final token = await _storage.read(key: 'jwt_token');
    if (token == null) throw Exception('Not logged in');
    return 'Bearer $token';
  }

  Future<Map<String, dynamic>> submitScan(List<int> bytes, String filename) async {
    final auth = await _authHeader();
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl/api/scans'));
    request.headers['Authorization'] = auth;

    String ext = filename.split('.').last.toLowerCase();
    String subtype = switch (ext) {
      'png' => 'png',
      'webp' => 'webp',
      _ => 'jpeg',
    };

    request.files.add(http.MultipartFile.fromBytes(
      'image', bytes, filename: filename, contentType: MediaType('image', subtype),
    ));

    final streamed = await request.send();
    final body = await streamed.stream.bytesToString();
    if (streamed.statusCode != 200 && streamed.statusCode != 202) {
      throw Exception('Submit failed (${streamed.statusCode}): $body');
    }
    return jsonDecode(body);
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

  Future<Map<String, dynamic>> finalizeScan(String id) async {
    final auth = await _authHeader();
    final response = await http.patch(
      Uri.parse('$baseUrl/api/scans/$id/finalize'),
      headers: {'Authorization': auth},
    );
    if (response.statusCode != 200) {
      throw Exception('Finalize failed (${response.statusCode})');
    }
    return jsonDecode(response.body);
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