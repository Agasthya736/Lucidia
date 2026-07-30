package com.lucidia.backend.agents.vision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Service
public class MedSamClient {

    private final RestClient restClient;

    public MedSamClient(@Value("${lucidia.medsam.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public MedSamFindings segment(byte[] imageBytes, String filename, int x1, int y1, int x2, int y2) {
        String boundary = "----LucidiaBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, imageBytes, filename, x1, y1, x2, y2);

        try {
            return restClient.post()
                    .uri("/segment")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(body)
                    .retrieve()
                    .body(MedSamFindings.class);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("MedSAM /segment rejected request ("
                    + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] imageBytes, String filename,
                                       int x1, int y1, int x2, int y2) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";

            writeFileField(out, boundary, "file", filename, imageBytes);
            writeTextField(out, boundary, "x1", String.valueOf(x1));
            writeTextField(out, boundary, "y1", String.valueOf(y1));
            writeTextField(out, boundary, "x2", String.valueOf(x2));
            writeTextField(out, boundary, "y2", String.valueOf(y2));
            writeTextField(out, boundary, "spacing_mm", "1.0");

            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build multipart body", e);
        }
    }

    private void writeTextField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        String crlf = "\r\n";
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write((value + crlf).getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(ByteArrayOutputStream out, String boundary, String name,
                                 String filename, byte[] fileBytes) throws IOException {
        String crlf = "\r\n";
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: image/png" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
    }
}