package com.lucidia.backend.agents.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class MedSamClient {

    private final RestClient restClient;

    public MedSamClient(@Value("${lucidia.medsam.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public MedSamFindings segment(byte[] imageBytes, String filename, int x1, int y1, int x2, int y2) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("x1", String.valueOf(x1));
        form.add("y1", String.valueOf(y1));
        form.add("x2", String.valueOf(x2));
        form.add("y2", String.valueOf(y2));
        form.add("spacing_mm", "1.0");

        return restClient.post()
                .uri("/segment")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(MedSamFindings.class);
    }
}