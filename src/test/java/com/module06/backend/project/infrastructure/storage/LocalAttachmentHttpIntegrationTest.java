package com.module06.backend.project.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.service.LocalProjectAttachmentStorageService;
import com.module06.backend.project.domain.repository.ProjectRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:local_attachment_${random.uuid};MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.redis.password=test",
    "ai.internal-token=test-internal-token", "app.local-storage.base-url=http://localhost:8080",
    "app.local-attachments.max-upload-bytes=32"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class LocalAttachmentHttpIntegrationTest {
    private static final Path ROOT = temporaryRoot();
    private static final byte[] PDF = "%PDF-1.7\ntest document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @DynamicPropertySource
    static void root(DynamicPropertyRegistry registry) { registry.add("app.local-storage.root", ROOT::toString); }
    @AfterAll static void cleanup() throws Exception { FileSystemUtils.deleteRecursively(ROOT); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired ProjectAttachmentStoragePort storage;
    @MockitoBean ProjectRepository projects;

    @BeforeEach
    void project() { when(projects.existsActiveByCompanyIdAndId(1L, 20L)).thenReturn(true); }

    @Test
    void pdfUploadConfirmDownloadRangeHeadAndOwnerDelete() throws Exception {
        assertThat(storage).isInstanceOf(LocalProjectAttachmentStorageService.class);
        var ticket = issue();
        String key = ticket.get("fileUrl").asText();
        URI upload = URI.create(ticket.get("uploadUrl").asText());
        mvc.perform(put(upload).contentType("application/pdf").content(PDF)).andExpect(status().isNoContent());
        assertThat(Files.readAllBytes(ROOT.resolve(key))).isEqualTo(PDF);
        mvc.perform(put(upload).contentType("application/pdf").content(PDF)).andExpect(status().isConflict());
        long id = confirm(key, PDF.length, 200);
        assertThat(confirm(key, PDF.length, 200)).isEqualTo(id);
        String response = mvc.perform(get("/api/projects/20/attachments/" + id + "/download-url")
                .header("Authorization", bearer(1, "MEMBER", false))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        URI download = URI.create(json.readTree(response).at("/data/downloadUrl").asText());
        mvc.perform(get(download)).andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("UTF-8")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(head(download)).andExpect(status().isOk()).andExpect(header().longValue("Content-Length", PDF.length));
        mvc.perform(get(download).header("Range", "bytes=0-3")).andExpect(status().isPartialContent())
                .andExpect(content().bytes("%PDF".getBytes()));
        mvc.perform(delete("/api/projects/20/attachments/" + id).header("Authorization", bearer(1, "MEMBER", true)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/20/attachments/" + id).header("Authorization", bearer(1, "OWNER", false)))
                .andExpect(status().isOk());
        assertThat(ROOT.resolve(key)).doesNotExist();
        mvc.perform(get(download)).andExpect(status().isNotFound());
    }

    @Test
    void confirmRequiresRealFileAndMatchingSizeAndProjectKey() throws Exception {
        var ticket = issue();
        String key = ticket.get("fileUrl").asText();
        confirm(key, PDF.length, 400);
        mvc.perform(put(URI.create(ticket.get("uploadUrl").asText())).content(PDF)).andExpect(status().isNoContent());
        confirm(key, PDF.length - 1, 400);
        confirm(key.replace("project-20", "project-21"), PDF.length, 400);
        confirm(key, PDF.length, 200);
    }

    @Test
    void mintingAndMetadataApisKeepJwtAndCompanyBoundaries() throws Exception {
        String body = "{\"fileName\":\"기획서.pdf\",\"fileSize\":" + PDF.length + "}";
        mvc.perform(post("/api/projects/20/attachments/upload-url").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/20/attachments/upload-url").header("Authorization", bearer(1, "MEMBER", false))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/20/attachments/upload-url").header("Authorization", bearer(2, "OWNER", false))
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
        var ticket = issue();
        String key = ticket.get("fileUrl").asText();
        mvc.perform(put(URI.create(ticket.get("uploadUrl").asText())).content(PDF)).andExpect(status().isNoContent());
        long id = confirm(key, PDF.length, 200);
        mvc.perform(get("/api/projects/20/attachments/" + id + "/download-url").header("Authorization", bearer(2, "OWNER", false)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/21/attachments/" + id + "/download-url").header("Authorization", bearer(1, "OWNER", false)))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedObjectsRejectMissingTamperedWrongMethodAndWrongLengthRequests() throws Exception {
        var ticket = issue();
        String url = ticket.get("uploadUrl").asText();
        String key = ticket.get("fileUrl").asText();
        mvc.perform(put("/" + key).content(PDF)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("PJ-008"));
        mvc.perform(put("/" + key).header("Authorization", bearer(1, "OWNER", false)).content(PDF)).andExpect(status().isForbidden());
        mvc.perform(get(URI.create(url))).andExpect(status().isForbidden());
        mvc.perform(put(URI.create(url.replace("project-20", "project-21"))).content(PDF)).andExpect(status().isForbidden());
        mvc.perform(put(URI.create(url + "x")).content(PDF)).andExpect(status().isForbidden());
        mvc.perform(put(URI.create(url)).content(new byte[PDF.length + 1])).andExpect(status().isBadRequest());
        assertThat(ROOT.resolve(key)).doesNotExist();
        mvc.perform(post("/api/projects/20/attachments/upload-url").header("Authorization", bearer(1, "OWNER", false))
                .contentType("application/json").content("{\"fileName\":\"large.pdf\",\"fileSize\":33}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void frontendPreflightAndBrowserSelectedMimeTypesWork() throws Exception {
        var ticket = issue();
        String key = ticket.get("fileUrl").asText();
        mvc.perform(options("/" + key).header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        // The existing FE sends File directly; the browser decides (or omits) Content-Type.
        mvc.perform(put(URI.create(ticket.get("uploadUrl").asText())).contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .content(PDF)).andExpect(status().isNoContent());
    }

    private JsonNode issue() throws Exception {
        String body = mvc.perform(post("/api/projects/20/attachments/upload-url")
                .header("Authorization", bearer(1, "MEMBER", true)).contentType("application/json")
                .content("{\"fileName\":\"기획서.pdf\",\"fileSize\":" + PDF.length + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private long confirm(String key, long size, int statusCode) throws Exception {
        String body = mvc.perform(post("/api/projects/20/attachments/confirm")
                .header("Authorization", bearer(1, "OWNER", false)).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("fileName", "기획서.pdf", "fileUrl", key, "fileSize", size))))
                .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
        return statusCode == 200 ? json.readTree(body).at("/data/attachmentId").asLong() : -1;
    }

    private String bearer(long company, String role, boolean admin) {
        return "Bearer " + tokens.createAccessToken(new AuthPrincipal(11L, company, role, admin, 1L));
    }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("attachment-http-"); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
}
