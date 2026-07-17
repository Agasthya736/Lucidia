import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../shared/api_client.dart';

/// Handles login/register calls and secure storage of the JWT.
/// Kept separate from the API client so screens depend on this,
/// not on HTTP details directly.
class AuthService {
  final ApiClient _api = ApiClient();
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const _tokenKey = 'jwt_token';

  Future<void> login(String email, String password) async {
    final result = await _api.login(email, password);
    final token = result['token'] as String?;
    if (token == null) {
      throw ApiException('No token returned from server');
    }
    await _storage.write(key: _tokenKey, value: token);
  }

  Future<void> register(String name, String email, String password) async {
    await _api.register(name, email, password);
    // Backend returns success; user logs in separately after registering.
  }

  Future<String?> getToken() => _storage.read(key: _tokenKey);

  Future<void> logout() => _storage.delete(key: _tokenKey);

  Future<bool> isLoggedIn() async {
    final token = await getToken();
    return token != null;
  }
}