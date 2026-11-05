package com.module06.backend.identity.company.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.company.application.usecase.GetCompanyProfileUseCase;
import com.module06.backend.identity.company.application.usecase.OnboardCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.UpdateCompanyProfileUseCase;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.metering.domain.repository.BillingSubscriptionRepository;

/** Exercises the real security filter chain and method authorization with company-scoped principals. */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyProfileSecurityTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GetCompanyProfileUseCase getCompanyProfileUseCase;
    @MockitoBean
    private UpdateCompanyProfileUseCase updateCompanyProfileUseCase;
    @MockitoBean
    private OnboardCompanyUseCase onboardCompanyUseCase;
    @MockitoBean
    private BillingSubscriptionRepository billingSubscriptionRepository;

    @ParameterizedTest
    @CsvSource({"OWNER,false", "MEMBER,true", "LEADER,true"})
    void ownerOrAdminReadsOnlyAuthenticatedCompany(String role, boolean admin) throws Exception {
        when(getCompanyProfileUseCase.getProfile(77L)).thenReturn(
                new Company(77L, "DEMO", "Demo company", "123-45-67890",
                        "Owner", null, null, null, "02-1234-5678", null));

        mockMvc.perform(get("/api/companies/me").param("companyId", "999")
                        .with(authentication(principal(role, admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(77));

        verify(getCompanyProfileUseCase).getProfile(77L);
        verify(billingSubscriptionRepository).findByCompanyId(77L);
        verifyNoMoreInteractions(getCompanyProfileUseCase, billingSubscriptionRepository);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER", "LEADER"})
    void ordinaryMemberCannotReadCompany(String role) throws Exception {
        mockMvc.perform(get("/api/companies/me").with(authentication(principal(role, false))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getCompanyProfileUseCase, billingSubscriptionRepository);
    }

    @Test
    void anonymousCannotReadCompany() throws Exception {
        mockMvc.perform(get("/api/companies/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(getCompanyProfileUseCase, billingSubscriptionRepository);
    }

    @ParameterizedTest
    @CsvSource({"MEMBER", "LEADER"})
    void adminCannotModifyCompanyOrOnboard(String role) throws Exception {
        mockMvc.perform(patch("/api/companies/me").with(authentication(principal(role, true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/companies/me/onboarding").with(authentication(principal(role, true)))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"teams":[{"tempId":"t1","name":"Development","subTeams":[]}],
                                 "jobPositions":[{"tempId":"p1","name":"Member","defaultRole":"MEMBER"}],
                                 "invites":[]}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(updateCompanyProfileUseCase, onboardCompanyUseCase);
    }

    private Authentication principal(String role, boolean admin) {
        // Deliberately omit ROLE_ADMIN: authorization must honor the actual isAdmin flag.
        return new TestingAuthenticationToken(new AuthPrincipal(3L, 77L, role, admin, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
