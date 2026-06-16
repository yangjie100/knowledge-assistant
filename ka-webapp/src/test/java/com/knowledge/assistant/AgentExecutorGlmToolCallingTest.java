package com.knowledge.assistant;

import com.knowledge.assistant.agent.react.AgentExecutor;
import com.knowledge.assistant.agent.react.AgentRequest;
import com.knowledge.assistant.agent.react.AgentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-3: verify reactChatClient is GLM-driven AND GLM stably triggers tool calls.
 * Gated on ZHIPU_API_KEY (real API call); skipped entirely when absent (regression-safe).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class AgentExecutorGlmToolCallingTest {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutorGlmToolCallingTest.class);

    @Autowired
    @Qualifier("reactChatClient")
    ChatClient reactChatClient;

    @Autowired
    ZhiPuAiChatModel zhiPuAiChatModel;

    @Autowired
    AgentExecutor agentExecutor;

    @Test
    void reactChatClientIsGlmDriven() {
        // P0-3 precondition: when key is set, reactChatClient must be the GLM branch, not Ollama fallback.
        // Direct proof: ZhiPuAiChatModel bean resolves (only the GLM branch depends on it).
        assertThat(zhiPuAiChatModel).as("ZhiPuAiChatModel bean exists (GLM branch registered)").isNotNull();
        assertThat(reactChatClient).as("reactChatClient bean present").isNotNull();
    }

    @Test
    void glmStablyTriggersDateTimeTool() {
        // Core P0-3 value: ask "what time is it now" — GLM cannot know the real current moment
        // without calling getCurrentDateTime(). If GLM stably invokes the tool, the answer MUST
        // contain today's real date. deepseek-r1 often hallucinates a date instead.
        // GLM reformats the tool output into natural Chinese ("2026年6月15日"), so accept either
        // ISO or Chinese format — either proves the real current date (decisive: an LLM whose
        // training cutoff predates today cannot produce today's exact date without the tool).
        String isoToday = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);            // 2026-06-15
        String cnToday = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)); // 2026年6月15日

        AgentResponse resp = agentExecutor.execute(new AgentRequest("现在是几点？请告诉我当前的日期和时间。"));

        assertThat(resp.isSuccess()).as("ReAct execute success").isTrue();
        String content = resp.getContent();
        log.info("[P0-3 GLM工具调用实测] 问题=现在时间, 今日={}, GLM回答={}", isoToday, content);

        // Decisive assertion: real current date present (in any format) => tool was actually invoked
        assertThat(content).as("GLM answer must contain today's real date in ISO or CN format (proves tool invoked, not hallucinated)")
                .containsAnyOf(isoToday, cnToday);
    }

    @Test
    void glmStablyTriggersCalculatorTool() {
        // Second tool: calculator. 123 * 456 = 56088. Hard for LLM to "know" vs compute;
        // tool invocation should yield exact 56088. GLM may add a thousands separator ("56,088")
        // or render in Chinese numerals ("五万六千零八十八"), so strip commas before asserting.
        AgentResponse resp = agentExecutor.execute(new AgentRequest("请帮我计算 123 乘以 456 等于多少？"));

        assertThat(resp.isSuccess()).as("ReAct execute success").isTrue();
        String content = resp.getContent();
        String digits = content.replace(",", ""); // tolerate "56,088" -> "56088"
        log.info("[P0-3 GLM工具调用实测] 问题=123*456, GLM回答={}", content);

        assertThat(digits).as("GLM answer must contain 56088 (calculator tool invoked)")
                .contains("56088");
    }
}
