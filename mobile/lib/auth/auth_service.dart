import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:google_sign_in/google_sign_in.dart';
import '../shared/api_client.dart';

/// Handles login/register calls, Google authentication, and secure storage of the JWT.
/// Kept separate from the API client so screens depend on this,
/// not on HTTP details directly.
class AuthService {
  final ApiClient _api = ApiClient();
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const _tokenKey = 'jwt_token';
  static const String defaultServerClientId =
      '747503445476-3l387af9b2ttbeo8n0g80630dd5fo853.apps.googleusercontent.com';

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

  /// Sign in using Google OAuth ID token.
  /// Handles both native Google Sign-In and dev fallback mode.
  Future<void> loginWithGoogle({String? serverClientId, bool useDevMock = false}) async {
    if (useDevMock) {
      final result = await _api.loginWithGoogle('mock_demo.clinician@lucidia.health');
      final token = result['token'] as String?;
      if (token == null) {
        throw ApiException('No token returned from server');
      }
      await _storage.write(key: _tokenKey, value: token);
      return;
    }

    try {
      final googleSignIn = GoogleSignIn(
        serverClientId: serverClientId ?? defaultServerClientId,
        scopes: const ['email', 'profile'],
      );

      final account = await googleSignIn.signIn();
      if (account == null) {
        throw ApiException('Google sign-in was cancelled');
      }

      final auth = await account.authentication;
      String? idToken = auth.idToken;

      // Fallback in debug mode if idToken was not generated because client ID isn't registered in Google Cloud yet
      if (idToken == null || idToken.isEmpty) {
        if (kDebugMode) {
          idToken = 'mock_${account.email}';
        } else {
          throw ApiException('Unable to retrieve Google ID token. Please verify Google Cloud Client ID configuration.');
        }
      }

      final result = await _api.loginWithGoogle(idToken);
      final token = result['token'] as String?;
      if (token == null) {
        throw ApiException('No token returned from server');
      }
      await _storage.write(key: _tokenKey, value: token);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(e.toString());
    }
  }

  Future<String?> getToken() => _storage.read(key: _tokenKey);

  Future<void> logout() async {
    try {
      final googleSignIn = GoogleSignIn();
      if (await googleSignIn.isSignedIn()) {
        await googleSignIn.signOut();
      }
    } catch (_) {}
    await _storage.delete(key: _tokenKey);
  }

  Future<bool> isLoggedIn() async {
    final token = await getToken();
    return token != null;
  }
}