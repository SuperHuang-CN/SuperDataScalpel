package cn.superhuang.data.scalpel.business.operations;

import cn.superhuang.data.scalpel.business.operations.web.request.RuntimeRunFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeRunFilterBindingTest {
    @Test
    void omittedFlagsBindAsFalseAndExplicitTrueRemainsTrue() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BindingResource()).build();
        mvc.perform(get("/runs")).andExpect(status().isOk()).andExpect(content().string("false:false"));
        mvc.perform(get("/runs").param("activeOnly", "true"))
                .andExpect(status().isOk()).andExpect(content().string("true:false"));
        mvc.perform(get("/runs").param("batchOnly", "true"))
                .andExpect(status().isOk()).andExpect(content().string("false:true"));
    }

    @RestController
    static class BindingResource {
        @GetMapping("/runs")
        String runs(@ModelAttribute RuntimeRunFilter filter) {
            return filter.activeOnly() + ":" + filter.batchOnly();
        }
    }
}
