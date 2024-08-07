package hexlet.code.controller.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import hexlet.code.exception.ResourceNotFoundException;
import hexlet.code.model.User;
import hexlet.code.repository.UserRepository;
import hexlet.code.util.ModelUtils;

import net.datafaker.Faker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Faker faker;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ModelUtils modelUtils;

    private JwtRequestPostProcessor token;

    private JwtRequestPostProcessor anotherToken;

    private User testUser;

    private User testAnotherUser;

    @BeforeEach
    public void setUp() {
        testUser = modelUtils.generateData().getUser();
        userRepository.save(testUser);
        token = jwt().jwt(builder -> builder.subject(testUser.getEmail()));

        testAnotherUser = modelUtils.generateData().getUser();
        userRepository.save(testAnotherUser);
        anotherToken = jwt().jwt(builder -> builder.subject(testAnotherUser.getEmail()));
    }

    @AfterEach
    public void clean() {
        userRepository.deleteById(testUser.getId());
    }

    @Test
    public void testIndex() throws Exception {
        var request = get("/api/users").with(token);
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        var body = result.getResponse().getContentAsString();
        assertThatJson(body).isArray();
    }

    @Test
    public void testShow() throws Exception {
        var id = testUser.getId();
        var request = get("/api/users/{id}", id).with(token);
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        var responseBody = result.getResponse().getContentAsString();

        assertThatJson(responseBody).and(
                body -> body.node("firstName").isEqualTo(testUser.getFirstName()),
                body -> body.node("lastName").isEqualTo(testUser.getLastName()),
                body -> body.node("email").isEqualTo(testUser.getEmail())
        );
    }

    @Test
    public void testCreate() throws Exception {
        var createData = Map.of(
                "firstName", faker.name().firstName(),
                "lastName", faker.name().lastName(),
                "email", faker.internet().emailAddress(),
                "password", faker.internet().password(3, 20)
        );

        var request = post("/api/users")
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(createData));

        mockMvc.perform(request).andExpect(status().isCreated());

        var user = userRepository.findByEmail(createData.get("email"))
                .orElseThrow(() -> new ResourceNotFoundException("\ntestCreate() in UserControllerTest failed\n"));

        assertThat(user.getFirstName()).isEqualTo(createData.get("firstName"));
        assertThat(user.getLastName()).isEqualTo(createData.get("lastName"));
        assertThat(user.getEncryptedPassword()).isNotEqualTo(createData.get("password"));
    }

    @Test
    public void testUpdate() throws Exception {
        var id = testUser.getId();

        var updateData = Map.of(
                "firstName", faker.name().firstName(),
                "lastName", faker.name().lastName(),
                "email", faker.internet().emailAddress(),
                "password", faker.internet().password(3, 20)
        );

        var request = put("/api/users/{id}", id)
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(updateData));

        mockMvc.perform(request).andExpect(status().isOk());

        var updatedUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("\ntestUpdate in UserControllerTest failed\n"));

        assertThat(updatedUser.getEmail()).isEqualTo(updateData.get("email"));
        assertThat(updatedUser.getFirstName()).isEqualTo(updateData.get("firstName"));
        assertThat(updatedUser.getLastName()).isEqualTo(updateData.get("lastName"));
        assertThat(updatedUser.getEncryptedPassword()).isNotEqualTo(updateData.get("password"));
    }

    @Test
    public void testPartialUpdate() throws Exception {
        var id = testUser.getId();

        var updateData = Map.of(
                "firstName", faker.name().firstName()
        );

        var request = put("/api/users/{id}", id)
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(updateData));

        mockMvc.perform(request).andExpect(status().isOk());

        var updatedUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("\ntestUpdate in UserControllerTest failed\n"));

        assertThat(updatedUser.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(updatedUser.getFirstName()).isEqualTo(updateData.get("firstName"));
        assertThat(updatedUser.getLastName()).isEqualTo(testUser.getLastName());
        assertThat(updatedUser.getEncryptedPassword()).isEqualTo(testUser.getPassword());
    }

    @Test
    public void testDelete() throws Exception {
        var id = testUser.getId();

        assertThat(userRepository.findById(id)).isPresent();

        var request = delete("/api/users/{id}", id).with(token);

        mockMvc.perform(request).andExpect(status().isNoContent());

        assertThat(userRepository.findById(id)).isEmpty();
    }

    @Test
    public void testDeleteByAnotherUser() throws Exception {
        var id = testUser.getId();

        var request = delete("/api/users/{id}", id)
                .with(anotherToken)
                .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(request).andExpect(status().isForbidden());

        var user = userRepository.findById(id).orElse(null);

        assertThat(user).isNotNull();
    }

    @Test
    public void testUpdateByAnotherUser() throws Exception {
        var id = testUser.getId();

        var updateData = Map.of(
                "email", faker.internet().emailAddress(),
                "password", faker.internet().password(3, 20)
        );

        var request = put("/api/users/{id}", id)
                .with(anotherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(updateData));

        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    @Test
    public void testIndexWithoutAuth() throws Exception {
        var request = get("/api/users");

        mockMvc.perform(request).andExpect(status().isUnauthorized());
    }

    @Test
    public void testShowWithoutAuth() throws Exception {
        var id = testUser.getId();
        var request = get("/api/users/{id}", id);

        mockMvc.perform(request).andExpect(status().isUnauthorized());
    }

    @Test
    public void testDeleteWithoutAuth() throws Exception {
        var id = testUser.getId();
        var request = delete("/api/users/{id}", id);

        mockMvc.perform(request).andExpect(status().isUnauthorized());
    }

    @Test
    public void testCreateWithoutNames() throws Exception {
        var createData = Map.of(
                "email", faker.internet().emailAddress(),
                "password", faker.internet().password(3, 20)
        );

        var request = post("/api/users")
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(createData));

        mockMvc.perform(request).andExpect(status().isCreated());

        var user = userRepository.findByEmail(createData.get("email"))
                .orElseThrow(() -> new ResourceNotFoundException("\ntestCreate() in UserControllerTest failed\n"));

        assertThat(user.getFirstName()).isNull();
        assertThat(user.getLastName()).isNull();
        assertThat(user.getEncryptedPassword()).isNotEqualTo(createData.get("password"));
    }

    @Test
    public void testCreateWithInvalidData() throws Exception {
        var createData = Map.of(
                "firstName", faker.name().firstName(),
                "lastName", faker.name().lastName(),
                "email", "not a valid email",
                "password", "a"
        );

        var request = post("/api/users")
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(createData));

        mockMvc.perform(request).andExpect(status().isBadRequest());
    }
}
