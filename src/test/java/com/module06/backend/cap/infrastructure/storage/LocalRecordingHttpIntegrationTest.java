package com.module06.backend.cap.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;
import com.module06.backend.metering.infrastructure.storage.LocalStorageObjectDeletionAdapter;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:local_recording_${random.uuid};MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.redis.password=test",
    "ai.internal-token=test-internal-token", "app.local-storage.base-url=http://localhost:8080",
    "app.local-storage.max-upload-bytes=32"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class LocalRecordingHttpIntegrationTest {
    private static final Path ROOT = temporaryRoot();
    private static final byte[] AUDIO = new byte[]{0,1,2,3,4,5,6,7,8,9};
    @DynamicPropertySource
    static void root(DynamicPropertyRegistry registry) { registry.add("app.local-storage.root", ROOT::toString); }
    @AfterAll static void cleanup() throws Exception { FileSystemUtils.deleteRecursively(ROOT); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired CapObjectStoragePort storage;
    @Autowired LocalStorageObjectDeletionAdapter storageDeletion;
    @MockitoBean MeetingReferenceRepository meetings;
    @Autowired ReportMeetingStorageUsagePort metering;

    @BeforeEach
    void meeting() {
        when(meetings.findCompanyId(10L)).thenReturn(Optional.of(1L));
        when(meetings.findProjectId(10L)).thenReturn(Optional.of(20L));
        when(meetings.existsById(10L)).thenReturn(true);
        when(meetings.isHost(10L,11L)).thenReturn(true);
    }

    @Test
    void signedPutRegisterPlaybackRangeHeadAndRecordingDelete() throws Exception {
        assertThat(storage).isInstanceOf(LocalCapObjectStorageAdapter.class);
        var upload = issue();
        String key = upload.key();
        mvc.perform(put(URI.create(upload.url())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isNoContent());
        assertThat(storage.objectMatches(key,AUDIO.length)).isTrue();
        assertThat(Files.readAllBytes(ROOT.resolve(key))).isEqualTo(AUDIO);
        mvc.perform(put(URI.create(upload.url())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CAP-027"));
        register(key,AUDIO.length,200);
        String url = playback();
        mvc.perform(get(URI.create(url.replace("/meeting-10/", "/meeting-99/"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(URI.create(url))).andExpect(status().isOk())
                .andExpect(content().bytes(AUDIO)).andExpect(content().contentType("audio/wav"));
        mvc.perform(get(URI.create(url)).header("Range","bytes=2-5"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 2-5/10"))
                .andExpect(content().bytes(new byte[]{2,3,4,5}));
        mvc.perform(get(URI.create(url)).header("Range","bytes=-3"))
                .andExpect(status().isPartialContent()).andExpect(content().bytes(new byte[]{7,8,9}));
        mvc.perform(get(URI.create(url)).header("Range","bytes=100-200"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(head(URI.create(url))).andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length",10));
        mvc.perform(delete("/api/meetings/10/recordings").param("confirm","true")
                        .header("Authorization",bearer(11L,1L)))
                .andExpect(status().isOk());
        assertThat(ROOT.resolve(key)).doesNotExist();
        mvc.perform(get(URI.create(url))).andExpect(status().isNotFound());
    }

    @Test
    void completeRejectsMissingFileAndWrongSizeAndBulkDeletionSharesRoot() throws Exception {
        var upload = issue();
        register(upload.key(),10,400);
        mvc.perform(put(URI.create(upload.url())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isNoContent());
        register(upload.key(),9,400);
        register(upload.key(),10,200);
        String url = playback();
        storageDeletion.deleteAll(List.of(upload.key()));
        assertThat(ROOT.resolve(upload.key())).doesNotExist();
        mvc.perform(get(URI.create(url))).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymousMintingCrossCompanyAccessInvalidSignaturesAndWrongMethods() throws Exception {
        mvc.perform(post("/api/meetings/10/recordings/manual/upload-url")
                        .contentType("application/json").content("{\"fileName\":\"audio.wav\",\"contentType\":\"audio/wav\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/meetings/10/recordings/manual/upload-url")
                        .header("Authorization",bearer(22L,2L))
                        .contentType("application/json").content("{\"fileName\":\"audio.wav\",\"contentType\":\"audio/wav\"}"))
                .andExpect(status().isForbidden());
        var upload = issue();
        mvc.perform(get(URI.create(upload.url()))).andExpect(status().isForbidden());
        mvc.perform(get("/recordings/org-1/meeting-10/missing.wav")).andExpect(status().isForbidden());
        mvc.perform(get("/recordings/org-1/meeting-10/missing.wav").param("token","bad"))
                .andExpect(status().isForbidden());
        mvc.perform(put(URI.create(upload.url())).contentType("audio/mp3").content(AUDIO))
                .andExpect(status().isForbidden());
        mvc.perform(put(URI.create(upload.url())).contentType("audio/wav").content(new byte[33]))
                .andExpect(status().isPayloadTooLarge());
        assertThat(ROOT.resolve(upload.key())).doesNotExist();
        mvc.perform(put(URI.create(upload.url())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isNoContent());
        register(upload.key(),10,200);
        mvc.perform(get("/api/meetings/10/recordings/playback-url").header("Authorization",bearer(22L,2L)))
                .andExpect(status().isForbidden());
        mvc.perform(put(URI.create(playback())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isForbidden());
    }

    @Test
    void localUploadCorsPreflightAndRangeHeadersAreAvailableToFrontend() throws Exception {
        mvc.perform(options("/recordings/org-1/meeting-10/missing.wav")
                        .header("Origin","http://localhost:3000")
                        .header("Access-Control-Request-Method","PUT")
                        .header("Access-Control-Request-Headers","content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
    }

    @Test
    void chunkUploadUsesSameSignedPutAndActualSizeValidation() throws Exception {
        String key = "stt-temp/org-1/meeting-10/segments/0/parts/0001.webm";
        var issued = storage.issuePartUploadUrl(key,"audio/webm");
        assertThat(java.net.URLDecoder.decode(URI.create(issued.presignedUrl()).getRawPath().substring(1),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(key);
        mvc.perform(put(URI.create(issued.presignedUrl())).contentType("audio/webm").content(AUDIO))
                .andExpect(status().isNoContent());
        assertThat(storage.objectMatches(key,10)).isTrue();
        assertThat(storage.objectMatches(key,9)).isFalse();
        storage.deleteRecording(key);
        assertThat(ROOT.resolve(key)).doesNotExist();
    }

    @Test
    void onlinePendingKeysKeepUnicodeFilenamesAndFrontendPathExtraction() throws Exception {
        String key = "recordings/org-1/member-11/online-pending/abc/회의 녹음.wav";
        var issued = storage.issuePartUploadUrl(key,"audio/wav");
        assertThat(org.springframework.web.util.UriUtils.decode(URI.create(issued.presignedUrl()).getRawPath().substring(1),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(key);
        mvc.perform(put(URI.create(issued.presignedUrl())).contentType("audio/wav").content(AUDIO))
                .andExpect(status().isNoContent());
        mvc.perform(get(URI.create(storage.issuePlaybackUrl(key).url())))
                .andExpect(status().isOk()).andExpect(content().bytes(AUDIO));
        storage.deleteRecording(key);
    }

    private Upload issue() throws Exception {
        String body = mvc.perform(post("/api/meetings/10/recordings/manual/upload-url")
                        .header("Authorization",bearer(11L,1L)).contentType("application/json")
                        .content("{\"fileName\":\"audio.wav\",\"contentType\":\"audio/wav\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).path("data");
        return new Upload(data.path("s3Key").asString(),data.path("uploadUrl").asString());
    }
    private void register(String key, long size, int expectedStatus) throws Exception {
        mvc.perform(post("/api/meetings/10/recordings/manual")
                        .header("Authorization",bearer(11L,1L)).contentType("application/json")
                        .content("{\"s3Key\":\""+key+"\",\"fileName\":\"audio.wav\",\"sizeBytes\":"+size+"}"))
                .andExpect(status().is(expectedStatus));
    }
    private String playback() throws Exception {
        String body = mvc.perform(get("/api/meetings/10/recordings/playback-url")
                        .header("Authorization",bearer(11L,1L)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("url").asString();
    }
    private String bearer(Long member, Long company) {
        return "Bearer " + tokens.createAccessToken(new AuthPrincipal(member,company,"OWNER",false,null));
    }
    private record Upload(String key, String url) { }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("local-recording-test-"); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
}
