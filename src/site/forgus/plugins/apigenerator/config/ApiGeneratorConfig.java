package site.forgus.plugins.apigenerator.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;


@State(name = "ApiGeneratorConfig")
public class ApiGeneratorConfig implements PersistentStateComponent<ApiGeneratorConfig> {

    public Set<String> excludeFieldNames = new HashSet<>();
    public String excludeFields = "serialVersionUID";
    public String dirPath = "";
    public String prefix = "└";
    public Boolean cnFileName = false;
    public Boolean overwrite = true;
    /**
     * showdoc api 接口地址
     */
    public String showDocServerUrl = "";
    /**
     * 认证凭证。登录showdoc，进入具体项目后，点击右上角的”项目设置”-“开放API”便可看到
     */
    public String apiKey = "";
    /**
     * 同apiKey
     */
    public String apiToken = "";

    public String defaultCat = "api_generator";

    @Nullable
    @Override
    public ApiGeneratorConfig getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ApiGeneratorConfig state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
