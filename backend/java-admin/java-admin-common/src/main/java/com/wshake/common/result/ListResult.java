package com.wshake.common.result;

import java.util.Collections;
import java.util.List;

/**
 * JSON 数组响应体。{@code data} 为 null 时回退到空列表（序列化为 <code>[]</code>）。
 *
 * <p>用途：让 Swagger/OpenAPI 通过 {@code ListResult<UserVO>} 直接展开 array schema，
 * 而不是 {@code Result<List<UserVO>>} 这种嵌套泛型。
 *
 * @param <T> 列表元素类型
 * @author wshake
 */
public class ListResult<T> extends Result<List<T>> {

    public ListResult() {}

    /** 等价于 {@code of(null)}：data 回退为空 List。 */
    public static <T> ListResult<T> of() {
        return of(null);
    }

    /** 构造成功响应；{@code data} 为 null 时回退为空 List（序列化为 <code>[]</code>）。 */
    public static <T> ListResult<T> of(List<T> data) {
        ListResult<T> r = new ListResult<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMsg(ResultCode.SUCCESS.getMsg());
        r.setData(data != null ? data : Collections.emptyList());
        return r;
    }
}
