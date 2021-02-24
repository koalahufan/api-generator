package site.forgus.plugins.apigenerator.yapi.model;

import java.io.Serializable;

/**
 * showdoc api请求封装类
 *
 * @author huzh
 * @version 1.0
 * @date 2021/2/22 10:13
 */
public class ShowDocInterface implements Serializable {
    private static final long serialVersionUID = 6207451273915876331L;

    /**
     * api_key，认证凭证。登录showdoc，进入具体项目后，点击右上角的”项目设置”-“开放API”便可看到
     */
    private String api_key;

    /**
     * 同上
     */
    private String api_token;

    /**
     * 可选参数。当页面文档处于目录下时，请传递目录名。当目录名不存在时，showdoc会自动创建此目录。
     * 需要创建多层目录的时候请用斜杆隔开，例如 “一层/二层/三层”
     */
    private String cat_name;

    /**
     * 页面标题。请保证其唯一。（或者，当页面处于目录下时，请保证页面标题在该目录下唯一）。当页面标题不存在时，showdoc将会创建此页面。
     * 当页面标题存在时，将用page_content更新其内容
     */
    private String page_title;

    /**
     * 页面内容，可传递markdown格式的文本或者html源码
     */
    private String page_content;

    /**
     * 可选，页面序号。默认是99。数字越小，该页面越靠前
     */
    private Integer s_number;

    public String getApi_key() {
        return api_key;
    }

    public void setApi_key(String api_key) {
        this.api_key = api_key;
    }

    public String getApi_token() {
        return api_token;
    }

    public void setApi_token(String api_token) {
        this.api_token = api_token;
    }

    public String getCat_name() {
        return cat_name;
    }

    public void setCat_name(String cat_name) {
        this.cat_name = cat_name;
    }

    public String getPage_title() {
        return page_title;
    }

    public void setPage_title(String page_title) {
        this.page_title = page_title;
    }

    public String getPage_content() {
        return page_content;
    }

    public void setPage_content(String page_content) {
        this.page_content = page_content;
    }

    public Integer getS_number() {
        return s_number;
    }

    public void setS_number(Integer s_number) {
        this.s_number = s_number;
    }
}
