package com.wshake.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.result.ListResult;
import com.wshake.common.result.ObjectResult;
import com.wshake.common.result.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Result} 序列化测试（Q13 决策）。
 *
 * <p>验证响应体严格 3 字段：{@code code}, {@code msg}, {@code data}。
 * <p>{@code traceId} <strong>不</strong>出现在 body 中。
 * <p>同时覆盖 {@link ObjectResult} / {@link ListResult} 的 null 回退语义。
 *
 * @author wshake
 */
class ResultFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void result_ok_hasOnlyThreeFields() throws Exception {
        Result<String> r = Result.ok("hello");

        String json = mapper.writeValueAsString(r);

        // 严格 3 字段
        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        assertThat(json).contains("\"data\":\"hello\"");
        // 验证不存在的字段
        assertThat(json).doesNotContain("\"traceId\"");
        assertThat(json).doesNotContain("\"message\"");   // 旧字段名
    }

    @Test
    void result_error_hasOnlyThreeFields() throws Exception {
        Result<Object> r = Result.error(2002, "凭证错误");

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"code\":2002");
        assertThat(json).contains("\"msg\":\"凭证错误\"");
        assertThat(json).doesNotContain("\"data\"");      // error 不带 data
        assertThat(json).doesNotContain("\"traceId\"");
        assertThat(json).doesNotContain("\"message\"");
    }

    @Test
    void result_isSuccess() {
        assertThat(Result.ok().isSuccess()).isTrue();
        assertThat(Result.error(1, "x").isSuccess()).isFalse();
    }

    // ---------------- ObjectResult ----------------

    @Test
    void objectResult_of_dataNull_serializesAsEmptyObject() throws Exception {
        ObjectResult<Map<String, Object>> r = ObjectResult.of();

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        // null 回退为空 Map → 序列化为 {}
        assertThat(json).contains("\"data\":{}");
    }

    @Test
    void objectResult_of_withMap_serializesEntries() throws Exception {
        ObjectResult<Map<String, Object>> r = ObjectResult.of(Map.of("name", "admin"));

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":{\"name\":\"admin\"}");
    }

    @Test
    void objectResult_of_withPojo_serializesFields() throws Exception {
        ObjectResult<UserView> r = ObjectResult.of(new UserView(1L, "admin"));

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":{");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"username\":\"admin\"");
    }

    @Test
    void objectResult_isStillResultSubclass() {
        ObjectResult<Map<String, Object>> r = ObjectResult.of();
        assertThat(r).isInstanceOf(Result.class);
        assertThat(r.isSuccess()).isTrue();
    }

    // ---------------- ListResult ----------------

    @Test
    void listResult_of_dataNull_serializesAsEmptyArray() throws Exception {
        ListResult<String> r = ListResult.of();

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        // null 回退为空 List → 序列化为 []
        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void listResult_of_withList_serializesElements() throws Exception {
        ListResult<String> r = ListResult.of(List.of("a", "b"));

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":[\"a\",\"b\"]");
    }

    @Test
    void listResult_of_emptyList_remainsEmptyArray() throws Exception {
        ListResult<String> r = ListResult.of(List.of());

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void listResult_isStillResultSubclass() {
        ListResult<String> r = ListResult.of();
        assertThat(r).isInstanceOf(Result.class);
        assertThat(r.isSuccess()).isTrue();
    }

    // ---------------- Result.okObj / Result.okList 入口 ----------------

    @Test
    void result_okObj_returnsObjectResultSubclass_nullDataAsEmptyObject() throws Exception {
        ObjectResult<Map<String, Object>> r = Result.okObj();

        assertThat(r).isInstanceOf(ObjectResult.class);
        String json = mapper.writeValueAsString(r);
        assertThat(json).contains("\"data\":{}");
    }

    @Test
    void result_okObj_withPojo_serializesFields() throws Exception {
        ObjectResult<UserView> r = Result.okObj(new UserView(2L, "alice"));

        String json = mapper.writeValueAsString(r);
        assertThat(json).contains("\"id\":2");
        assertThat(json).contains("\"username\":\"alice\"");
    }

    @Test
    void result_okList_returnsListResultSubclass_nullDataAsEmptyArray() throws Exception {
        ListResult<String> r = Result.okList();

        assertThat(r).isInstanceOf(ListResult.class);
        String json = mapper.writeValueAsString(r);
        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void result_okList_withList_serializesElements() throws Exception {
        ListResult<String> r = Result.okList(List.of("x", "y"));

        String json = mapper.writeValueAsString(r);
        assertThat(json).contains("\"data\":[\"x\",\"y\"]");
    }

    /** 测试用 POJO。 */
    record UserView(Long id, String username) {}
}
