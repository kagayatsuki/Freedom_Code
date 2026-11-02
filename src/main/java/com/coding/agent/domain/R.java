package com.coding.agent.domain;


import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.slf4j.MDC;

import static com.coding.agent.domain.R.Code.SUCCESS;
import static com.coding.agent.domain.R.Msg.OK;

@Data
public class R<T> {
    interface Code {
        int SUCCESS = 200;
        int FAILED = 0;
    }
    interface Msg {
        String OK = "OK";
        String INVALID_VERIFY_CODE = "验证码错误";
        String SERVER_INTER_ERROR = "服务器内部错误";

        String DB_SAVE_EXCEPTION = "数据新增失败";
        String DB_DELETE_EXCEPTION = "数据删除失败";
        String DB_BATCH_DELETE_EXCEPTION = "数据批量删除失败";
        String DB_UPDATE_EXCEPTION = "数据更新失败";
        String DB_SORT_FIELD_NOT_FOUND = "排序字段不存在";
        String OPERATE_FAILED = "操作失败";

        String REQUEST_PARAM_ILLEGAL = "请求参数不合法";
        String REQUEST_OPERATE_FREQUENTLY = "操作频繁,请稍后重试";
        String REQUEST_TIME_OUT = "请求超时";

        String USER_NOT_EXISTS = "用户信息不存在";
        String INVALID_USER_TYPE = "无效的用户类型";
    }
    interface Constant {
        String REQUEST_ID_HEADER = "requestId";
        String REQUEST_FROM_HEADER = "x-request-from";

        String GATEWAY_ORIGIN_NAME = "gateway";
        String FEIGN_ORIGIN_NAME = "feign";

        // 数据字段 - id
        String DATA_FIELD_NAME_ID = "id";

        // 数据字段 - create_time
        String DATA_FIELD_NAME_CREATE_TIME = "create_time";
        String DATA_FIELD_NAME_CREATE_TIME_CAMEL = "createTime";

        // 数据字段 - update_time
        String DATA_FIELD_NAME_UPDATE_TIME = "update_time";
        String DATA_FIELD_NAME_UPDATE_TIME_CAMEL = "updateTime";

        // 数据字段 - liked_times
        String DATA_FIELD_NAME_LIKED_TIME = "liked_times";
        String DATA_FIELD_NAME_LIKED_TIME_CAMEL = "likedTimes";

        // 数据字段 - creater
        String DATA_FIELD_NAME_CREATER = "creater";

        // 数据字段 - updater
        String DATA_FIELD_NAME_UPDATER = "updater";

        // 数据已经删除标识值
        boolean DATA_DELETE = true;
        // 数据未删除标识值
        boolean DATA_NOT_DELETE = false;
        // 响应结果是否被R标记过
        String BODY_PROCESSED_MARK_HEADER = "IS_BODY_PROCESSED";
    }

    private int code;
    @ApiModelProperty(value = "响应消息", example = "OK")
    private String msg;
    @ApiModelProperty(value = "响应数据")
    private T data;
    @ApiModelProperty(value = "请求id", example = "1af123c11412e")
    private String requestId;

    public static R<Void> ok() {
        return new R<Void>(SUCCESS, OK, null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, OK, data);
    }

    public static <T> R<T> error(String msg) {
        return new R<>(Code.FAILED, msg, null);
    }

    public static <T> R<T> error(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.requestId = MDC.get(Constant.REQUEST_ID_HEADER);
    }

    public boolean success(){
        return code == SUCCESS;
    }

    public R<T> requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}
