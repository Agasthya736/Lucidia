import 'dart:convert';
import 'package:http_parser/http_parser.dart';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:http/http.dart' as http;
import '../shared/theme.dart';

class VisionTestScreen extends StatefulWidget {
  const VisionTestScreen({super.key});

  @override
  State<VisionTestScreen> createState() => _VisionTestScreenState();
}

class _VisionTestScreenState extends State<VisionTestScreen> {
  bool _loading = false;
  String? _error;
  Map<String, dynamic>? _result;

  Future<void> _pickAndAnalyze() async {
    final picked = await FilePicker.platform.pickFiles(
      type: FileType.image,
      withData: true,
    );

    if (picked == null || picked.files.isEmpty) return;

    final file = picked.files.single;

    if (file.bytes == null) {
      setState(() => _error = "Failed to read file bytes");
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });

    try {
      final request = http.MultipartRequest(
        'POST',
        Uri.parse('http://localhost:8080/api/test/vision'),
      );

      String extension = file.extension?.toLowerCase() ?? 'jpg';

      String mimeSubtype;
      switch (extension) {
        case 'png':
          mimeSubtype = 'png';
          break;
        case 'webp':
          mimeSubtype = 'webp';
          break;
        case 'jpg':
        case 'jpeg':
        default:
          mimeSubtype = 'jpeg';
      }

      request.files.add(
        http.MultipartFile.fromBytes(
          'image',
          file.bytes!,
          filename: file.name,
          contentType: MediaType('image', mimeSubtype),
        ),
      );

      final streamed = await request.send();
      final body = await streamed.stream.bytesToString();

      if (streamed.statusCode == 200) {
        setState(() => _result = jsonDecode(body));
      } else {
        setState(() => _error = 'Status ${streamed.statusCode}: $body');
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pipeline Test')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ElevatedButton(
                onPressed: _loading ? null : _pickAndAnalyze,
                child: _loading
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Color(0xFF07211F),
                        ),
                      )
                    : const Text('Pick scan & analyze'),
              ),

              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(
                  _error!,
                  style: TextStyle(color: LucidiaColors.error),
                ),
              ],

              if (_result != null) ...[
                const SizedBox(height: 24),
                _agentCard('Vision A (Gemini)', _result!['visionA']),
                const SizedBox(height: 16),
                _agentCard('Vision B (Ollama)', _result!['visionB']),
                const SizedBox(height: 16),
                _arbitrationCard(_result!['arbitration']),
                const SizedBox(height: 16),
                _reportCard(_result!['report']),
                const SizedBox(height: 16),
                _verificationCard(_result!['verification']),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _agentCard(String title, Map<String, dynamic>? data) {
    if (data == null) return const SizedBox();

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title,
              style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  color: LucidiaColors.teal)),
          const SizedBox(height: 8),
          Text(
            data['summary'] ?? '',
            style: const TextStyle(color: LucidiaColors.textPrimary),
          ),
          const SizedBox(height: 8),
          ...List<String>.from(data['observations'] ?? [])
              .map((o) => Text('• $o',
                  style: const TextStyle(
                      color: LucidiaColors.textSecondary, fontSize: 13))),
          const SizedBox(height: 8),
          Text(
            'Region: ${data['regionDescription']} | Confidence: ${data['confidence']}',
            style: const TextStyle(
                color: LucidiaColors.textSecondary, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _arbitrationCard(Map<String, dynamic>? data) {
    if (data == null) return const SizedBox();

    final bool agree = data['agree'] == true;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: agree ? LucidiaColors.teal : LucidiaColors.violet,
          width: 1.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            agree ? 'Agents agree' : 'Agents disagree - review flagged',
            style: TextStyle(
              fontWeight: FontWeight.w700,
              color: agree
                  ? LucidiaColors.teal
                  : LucidiaColors.violet,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Agreement score: ${(data['agreementScore'] as num?)?.toStringAsFixed(2) ?? "N/A"}',
            style: const TextStyle(color: LucidiaColors.textPrimary),
          ),
          const SizedBox(height: 6),
          Text(
            data['notes'] ?? '',
            style: const TextStyle(
                color: LucidiaColors.textSecondary, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _reportCard(Map<String, dynamic>? data) {
    if (data == null) return const SizedBox();

    final bool flagged = data['flaggedForReview'] == true;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Draft Report',
              style: TextStyle(
                  fontWeight: FontWeight.w700, color: LucidiaColors.teal)),
          const SizedBox(height: 8),
          const Text('Findings',
              style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: LucidiaColors.textPrimary)),
          Text(
            data['findings'] ?? '',
            style: const TextStyle(color: LucidiaColors.textPrimary),
          ),
          const SizedBox(height: 8),
          const Text('Impression',
              style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: LucidiaColors.textPrimary)),
          Text(
            data['impression'] ?? '',
            style: const TextStyle(color: LucidiaColors.textPrimary),
          ),
          if (flagged) ...[
            const SizedBox(height: 8),
            const Text(
              'Flagged for clinician review',
              style: TextStyle(
                  color: LucidiaColors.violet, fontWeight: FontWeight.w600),
            ),
          ],
        ],
      ),
    );
  }

  Widget _verificationCard(Map<String, dynamic>? data) {
    if (data == null) return const SizedBox();

    final bool verified = data['verified'] == true;
    final flags = List<String>.from(data['flags'] ?? []);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LucidiaColors.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: verified ? LucidiaColors.teal : LucidiaColors.error,
          width: 1.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            verified ? 'Report verified' : 'Unverified claims found',
            style: TextStyle(
              fontWeight: FontWeight.w700,
              color: verified ? LucidiaColors.teal : LucidiaColors.error,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            data['notes'] ?? '',
            style: const TextStyle(
                color: LucidiaColors.textSecondary, fontSize: 12),
          ),
          ...flags.map((f) => Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  '- $f',
                  style: const TextStyle(
                      color: LucidiaColors.error, fontSize: 12),
                ),
              )),
        ],
      ),
    );
  }
}