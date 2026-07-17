import 'dart:convert';
import 'package:http/http.dart' as http;

/// Thin wrapper around the Lucidia backend auth endpoints.
/// Base URL points at your local Spring Boot instance for now -
/// swap for a real host once you deploy.
class ApiClient {
  // localhost works for Chrome/Windows targets, since backend runs on the same machine.
// Android emulator needs 10.0.2.2 instead - swap when testing on that target.
  static const String baseUrl = 'http://localhost:8080'; // 10.0.2.2 = Android emulator's localhost

  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );

    if (response.statusCode == 200) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    } else {
      throw ApiException(_extractMessage(response.body, 'Login failed'));
    }
  }

  Future<Map<String, dynamic>> register(
      String name, String email, String password) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/auth/register'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'name': name, 'email': email, 'password': password}),
    );

    if (response.statusCode == 200 || response.statusCode == 201) {
      return jsonDecode(response.body) as Map<String, dynamic>;
    } else {
      throw ApiException(_extractMessage(response.body, 'Registration failed'));
    }
  }

  String _extractMessage(String body, String fallback) {
    try {
      final decoded = jsonDecode(body);
      return decoded['message'] ?? fallback;
    } catch (_) {
      return fallback;
    }
  }
}

class ApiException implements Exception {
  final String message;
  ApiException(this.message);
  @override
  String toString() => message;
}