package com.example.itgluemock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ItGlueMockApplicationTests {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void searchReturnsConfigurationMatch() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/search").param("query", "acme"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("data")).isNotEmpty();
        assertThat(response.path("data").get(0).path("attributes").path("name").asText())
                .containsIgnoringCase("acme");
    }

    @Test
    void configurationIncludeOrganization() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/configurations/100").param("include", "organization"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode included = response.path("included");
        assertThat(included).isNotNull();
        assertThat(included).isNotEmpty();
        assertThat(included.get(0).path("type").asText()).isEqualTo("organizations");
    }
}
