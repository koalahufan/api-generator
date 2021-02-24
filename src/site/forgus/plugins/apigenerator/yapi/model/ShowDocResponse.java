package site.forgus.plugins.apigenerator.yapi.model;

import java.io.Serializable;
import java.util.Map;

/**
 * showdoc接口api返回结果封装类
 *
 * @author huzh
 * @version 1.0
 * @date 2021/2/22 10:09
 */
public class ShowDocResponse implements Serializable {
    private static final long serialVersionUID = -8309368670827586501L;

    /**
     * 错误码
     */
    private String error_code;

    /**
     * 错误信息
     */
    private String error_message;

    /**
     * 成功的数据
     */
    private Map<String,String> data;


    public String getError_code() {
        return error_code;
    }

    public void setError_code(String error_code) {
        this.error_code = error_code;
    }

    public String getError_message() {
        return error_message;
    }

    public void setError_message(String error_message) {
        this.error_message = error_message;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }
}
