package cn.superhuang.data.scalpel.web.error;

import cn.superhuang.data.scalpel.search.InvalidSearchRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailsExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestResource())
                .setControllerAdvice(new ProblemDetailsExceptionHandler(new ProblemDetailFactory()))
                .setValidator(validator)
                .build();
    }

    @Test
    void validationFailureUsesTheSharedProblemContract() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:validation-failed"))
                .andExpect(jsonPath("$.title").value("请求参数校验失败"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void malformedJsonUsesTheSharedProblemContract() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:malformed-request"))
                .andExpect(jsonPath("$.title").value("请求内容无效"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("请求内容无法解析"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void responseStatusExceptionKeepsItsDetailAndGetsAStableCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:business-conflict"))
                .andExpect(jsonPath("$.title").value("资源状态冲突"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("编码已存在"))
                .andExpect(jsonPath("$.instance").value("/test/conflict"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidSearchRequestUsesItsDedicatedProblemCode() throws Exception {
        mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:invalid-search-request"))
                .andExpect(jsonPath("$.title").value("查询条件无效"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("筛选字段不支持"))
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"))
                .andExpect(jsonPath("$.instance").value("/test/search"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void webBindingAndMethodErrorsUseTheSharedProblemContract() throws Exception {
        mockMvc.perform(get("/test/type").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:bad-request"))
                .andExpect(jsonPath("$.title").value("请求无效"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("请求参数类型不正确"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.instance").value("/test/type"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/test/type"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:method-not-allowed"))
                .andExpect(jsonPath("$.title").value("请求方法不支持"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.detail").value("当前资源不支持该请求方法"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.instance").value("/test/type"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void dataIntegrityAndUnexpectedFailuresDoNotExposeInternalDetails() throws Exception {
        mockMvc.perform(get("/test/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:business-conflict"))
                .andExpect(jsonPath("$.title").value("资源状态冲突"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("数据操作与当前资源状态冲突"))
                .andExpect(jsonPath("$.instance").value("/test/data-conflict"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:datascalpel:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("系统内部错误"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("系统内部错误"))
                .andExpect(jsonPath("$.instance").value("/test/unexpected"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    @RequestMapping("/test")
    static class TestResource {

        @PostMapping("/validation")
        ValidationRequest validate(@Valid @RequestBody ValidationRequest request) {
            return request;
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "编码已存在");
        }

        @GetMapping("/search")
        void search() {
            throw new InvalidSearchRequestException("筛选字段不支持");
        }

        @GetMapping("/type")
        String type(@RequestParam("page") Integer page) {
            return page.toString();
        }

        @GetMapping("/data-conflict")
        void dataConflict() {
            throw new DataIntegrityViolationException("sensitive database constraint detail");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive internal state");
        }
    }

    record ValidationRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
