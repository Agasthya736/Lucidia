import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:google_sign_in/google_sign_in.dart';
import '../shared/api_client.dart';

/// Handles login/register calls, Google authentication, and secure storage of the JWT
/// and user profile details (name, email, avatarUrl).
class AuthService {
  final ApiClient _api = ApiClient();
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const _tokenKey = 'jwt_token';
  static const _nameKey = 'user_name';
  static const _emailKey = 'user_email';
  static const _avatarUrlKey = 'user_avatar_url';

  static const String defaultServerClientId =
      '747503445476-3l387af9b2ttbeo8n0g80630dd5fo853.apps.googleusercontent.com';

  /// Builds a GoogleSignIn instance that works on every platform.
  /// - Web: needs `clientId`; the web plugin rejects `serverClientId`.
  /// - Android/iOS: uses `serverClientId` so an ID token is issued for the backend.
  GoogleSignIn _buildGoogleSignIn(String? serverClientId) {
    final id = serverClientId ?? defaultServerClientId;
    return GoogleSignIn(
      clientId: kIsWeb ? id : null,
      serverClientId: kIsWeb ? null : id,
      scopes: const ['email', 'profile'],
    );
  }

  Future<void> _persistAuthData(Map<String, dynamic> result) async {
    final token = result['token'] as String?;
    if (token == null) {
      throw ApiException('No token returned from server');
    }
    await _storage.write(key: _tokenKey, value: token);

    final name = result['name'] as String?;
    if (name != null && name.isNotEmpty) {
      await _storage.write(key: _nameKey, value: name);
    } else {
      await _storage.delete(key: _nameKey);
    }

    final email = result['email'] as String?;
    if (email != null && email.isNotEmpty) {
      await _storage.write(key: _emailKey, value: email);
    } else {
      await _storage.delete(key: _emailKey);
    }

    final avatarUrl = result['avatarUrl'] as String?;
    if (avatarUrl != null && avatarUrl.isNotEmpty) {
      await _storage.write(key: _avatarUrlKey, value: avatarUrl);
    } else {
      await _storage.delete(key: _avatarUrlKey);
    }
  }

  Future<void> login(String email, String password) async {
    final result = await _api.login(email, password);
    await _persistAuthData(result);
  }

  Future<void> register(String name, String email, String password) async {
    await _api.register(name, email, password);
    // Backend returns success; user logs in separately after registering.
  }

  /// Sign in using Google OAuth ID token.
  /// Handles both native Google Sign-In and dev fallback mode.
  Future<void> loginWithGoogle({String? serverClientId, bool useDevMock = false}) async {
    if (useDevMock) {
      final result = await _api.loginWithGoogle('mock_demo.user@lucidia.health');
      await _persistAuthData(result);
      return;
    }

    try {
      final googleSignIn = _buildGoogleSignIn(serverClientId);

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
      await _persistAuthData(result);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(e.toString());
    }
  }

  /// Fetches authenticated user info from GET /api/me and updates secure storage cache.
  Future<Map<String, dynamic>> getMe() async {
    final token = await getToken();
    if (token == null) {
      throw ApiException('Not logged in');
    }
    final me = await _api.getMe(token);

    final name = me['name'] as String?;
    if (name != null && name.isNotEmpty) {
      await _storage.write(key: _nameKey, value: name);
    }

    final email = me['email'] as String?;
    if (email != null && email.isNotEmpty) {
      await _storage.write(key: _emailKey, value: email);
    }

    final avatarUrl = me['avatarUrl'] as String?;
    if (avatarUrl != null && avatarUrl.isNotEmpty) {
      await _storage.write(key: _avatarUrlKey, value: avatarUrl);
    } else {
      await _storage.delete(key: _avatarUrlKey);
    }

    return me;
  }

  Future<String?> getToken() => _storage.read(key: _tokenKey);
  Future<String?> getName() => _storage.read(key: _nameKey);
  Future<String?> getEmail() => _storage.read(key: _emailKey);
  Future<String?> getAvatarUrl() => _storage.read(key: _avatarUrlKey);

  Future<void> logout() async {
    try {
      final googleSignIn = _buildGoogleSignIn(null);
      if (await googleSignIn.isSignedIn()) {
        await googleSignIn.signOut();
      }
    } catch (_) {}
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _nameKey);
    await _storage.delete(key: _emailKey);
    await _storage.delete(key: _avatarUrlKey);
  }

  Future<bool> isLoggedIn() async {
    final token = await getToken();
    return token != null;
  }
}