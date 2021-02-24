package site.forgus.plugins.apigenerator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import site.forgus.plugins.apigenerator.config.ApiGeneratorConfig;
import site.forgus.plugins.apigenerator.constant.WebAnnotation;
import site.forgus.plugins.apigenerator.normal.FieldFactory;
import site.forgus.plugins.apigenerator.normal.FieldInfo;
import site.forgus.plugins.apigenerator.normal.MethodInfo;
import site.forgus.plugins.apigenerator.util.*;
import site.forgus.plugins.apigenerator.yapi.enums.RequestMethodEnum;
import site.forgus.plugins.apigenerator.yapi.model.ShowDocInterface;
import site.forgus.plugins.apigenerator.yapi.model.ShowDocSDK;
import site.forgus.plugins.apigenerator.yapi.model.YApiForm;
import site.forgus.plugins.apigenerator.yapi.model.YApiQuery;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiGenerateAction extends AnAction {

    protected ApiGeneratorConfig config;

    private static final String SLASH = "/";

    /**
     * 事件监听
     *
     * @param actionEvent 事件内容
     */
    @Override
    public void actionPerformed(AnActionEvent actionEvent) {
        Editor editor = actionEvent.getDataContext().getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        PsiFile psiFile = actionEvent.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) {
            return;
        }
        Project project = editor.getProject();
        if (project == null) {
            return;
        }
        config = ServiceManager.getService(project, ApiGeneratorConfig.class);
        PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass selectedClass = PsiTreeUtil.getContextOfType(referenceAt, PsiClass.class);
        if (selectedClass == null) {
            NotificationUtil.errorNotify("this operate only support in class file", project);
            return;
        }
        // 属于dubbo接口，或者内部的方法，直接使用api会更方便
        // todo 可以作为索引作用
        if (selectedClass.isInterface()) {
            generateMarkdownForInterface(project, referenceAt, selectedClass);
            return;
        }
        // 属于controller接口
        if (haveControllerAnnotation(selectedClass)) {
            uploadApiToShowDoc(project, referenceAt, selectedClass);
            return;
        }
        // 没找到是哪种类型，最后直接生成markdown文档
        generateMarkdownForClass(project, selectedClass);
    }

    /**
     * 将api文档上传到showDoc上
     *
     * @param project       编辑器的项目信息
     * @param referenceAt   鼠标的定位的位置信息
     * @param selectedClass 被选中的class
     */
    private void uploadApiToShowDoc(Project project, PsiElement referenceAt, PsiClass selectedClass) {
        PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(referenceAt, PsiMethod.class);
        if (selectedMethod != null) {
            uploadSelectedMethodToShowDoc(project, selectedMethod);
            return;
        }
        uploadHttpMethodsToShowDoc(project, selectedClass);
    }

    /**
     * 上传指定接口类中的api文档到showdoc上
     *
     * @param project  编辑器的项目信息
     * @param psiClass 选中的接口类
     */
    private void uploadHttpMethodsToShowDoc(Project project, PsiClass psiClass) {
        if (!haveControllerAnnotation(psiClass)) {
            NotificationUtil.warnNotify("Upload api failed, reason:\n not REST api.", project);
            return;
        }
        if (StringUtils.isEmpty(config.getState().showDocServerUrl)) {
            String serverUrl = Messages.showInputDialog("Input ShowDoc Server Url", "ShowDoc Server Url", Messages.getInformationIcon());
            if (StringUtils.isEmpty(serverUrl)) {
                NotificationUtil.warnNotify("ShowDoc server url can not be empty.", project);
                return;
            }
            config.getState().showDocServerUrl = serverUrl;
        }
        if (StringUtils.isEmpty(config.getState().apiKey)) {
            String apiKey = Messages.showInputDialog(
                    "Input Api Key", "Api Key", Messages.getInformationIcon());
            if (StringUtils.isEmpty(apiKey)) {
                NotificationUtil.warnNotify("Api Key can not be empty.", project);
                return;
            }
            config.getState().apiKey = apiKey;
        }
        if (StringUtils.isEmpty(config.getState().apiToken)) {
            String apiToken = Messages.showInputDialog(
                    "Input Api Token", "Api Token", Messages.getInformationIcon());
            if (StringUtils.isEmpty(apiToken)) {
                NotificationUtil.warnNotify("Api Token can not be empty.", project);
                return;
            }
            config.getState().apiToken = apiToken;
        }
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            if (hasMappingAnnotation(method)) {
                uploadToShowDoc(project, method);
            }
        }
    }

    private void generateMarkdownForInterface(Project project, PsiElement referenceAt, PsiClass selectedClass) {
        PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(referenceAt, PsiMethod.class);
        if (selectedMethod != null) {
            try {
                generateMarkdownForSelectedMethod(project, selectedMethod);
            } catch (IOException e) {
                NotificationUtil.errorNotify(e.getMessage(), project);
            }
            return;
        }
        try {
            generateMarkdownsForAllMethods(project, selectedClass);
        } catch (IOException e) {
            NotificationUtil.errorNotify(e.getMessage(), project);
        }
    }

    private void generateMarkdownForClass(Project project, PsiClass psiClass) {
        String dirPath = getDirPath(project);
        if (!mkDirectory(project, dirPath)) {
            return;
        }
        boolean generateSuccess = false;
        try {
            generateSuccess = generateDocForClass(project, psiClass, dirPath);
        } catch (IOException e) {
            NotificationUtil.errorNotify(e.getMessage(), project);
        }
        if (generateSuccess) {
            NotificationUtil.infoNotify("generate api doc success.", project);
        }
    }

    protected void generateMarkdownForSelectedMethod(Project project, PsiMethod selectedMethod) throws IOException {
        String dirPath = getDirPath(project);
        if (!mkDirectory(project, dirPath)) {
            return;
        }
        boolean generateSuccess = generateDocForMethod(project, selectedMethod, dirPath);
        if (generateSuccess) {
            NotificationUtil.infoNotify("generate api doc success.", project);
        }
    }

    protected void generateMarkdownsForAllMethods(Project project, PsiClass selectedClass) throws IOException {
        String dirPath = getDirPath(project);
        if (!mkDirectory(project, dirPath)) {
            return;
        }
        boolean generateSuccess = false;
        for (PsiMethod psiMethod : selectedClass.getMethods()) {
            if (generateDocForMethod(project, psiMethod, dirPath)) {
                generateSuccess = true;
            }
        }
        if (generateSuccess) {
            NotificationUtil.infoNotify("generate api doc success.", project);
        }
    }

    /**
     * 上传指定方法的api文档到showdoc上
     *
     * @param project 编辑器的项目信息
     * @param method  选中的方法
     */
    private void uploadSelectedMethodToShowDoc(Project project, PsiMethod method) {
        if (!hasMappingAnnotation(method)) {
            NotificationUtil.warnNotify("Upload api failed, reason:\n not REST api.", project);
            return;
        }
        if (StringUtils.isEmpty(config.getState().showDocServerUrl)) {
            // 弹框显示的数据
            String serverUrl = Messages.showInputDialog(
                    "Input ShowDoc Server Url", "ShowDoc Server Url", Messages.getInformationIcon());
            if (StringUtils.isEmpty(serverUrl)) {
                NotificationUtil.warnNotify("ShowDoc server url can not be empty.", project);
                return;
            }
            config.getState().showDocServerUrl = serverUrl;
        }
        if (StringUtils.isEmpty(config.getState().apiKey)) {
            String apiKey = Messages.showInputDialog(
                    "Input Api Key", "Api Key", Messages.getInformationIcon());
            if (StringUtils.isEmpty(apiKey)) {
                NotificationUtil.warnNotify("Api Key can not be empty.", project);
                return;
            }
            config.getState().apiKey = apiKey;
        }
        if (StringUtils.isEmpty(config.getState().apiToken)) {
            String apiToken = Messages.showInputDialog(
                    "Input Api Token", "Api Token", Messages.getInformationIcon());
            if (StringUtils.isEmpty(apiToken)) {
                NotificationUtil.warnNotify("Api Token can not be empty.", project);
                return;
            }
            config.getState().apiToken = apiToken;
        }
        uploadToShowDoc(project, method);
    }

    /**
     * 构建内容并上传到showDoc
     * todo 修改内容，结合markdown生成的方法，生成markdown格式的文件，上传到api上去
     *
     * @param project   编辑器的项目信息
     * @param psiMethod 选中的方法
     */
    private void uploadToShowDoc(Project project, PsiMethod psiMethod) {
        ShowDocInterface showDocInterface = buildShowDocApi(psiMethod);
        try {
            ShowDocSDK.saveInterface(config.getState().showDocServerUrl, showDocInterface);
        } catch (Exception e) {
            NotificationUtil.errorNotify("Upload api failed, cause: " + e.getMessage(), project);
            return;
        }
        NotificationUtil.infoNotify("Upload api success.", project);
    }

    /**
     * 封装 ShowDoc api请求参数
     *
     * @param psiMethod 被选中的方法
     * @return ShowDoc请求参数封装类
     */
    private ShowDocInterface buildShowDocApi(PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        PsiAnnotation controller = null;
        PsiAnnotation classRequestMapping = null;
        for (PsiAnnotation annotation : containingClass.getAnnotations()) {
            String text = annotation.getText();
            if (text.endsWith(WebAnnotation.Controller)) {
                controller = annotation;
            } else if (text.contains(WebAnnotation.RequestMapping)) {
                classRequestMapping = annotation;
            }
        }
        if (controller == null) {
            return null;
        }
        MethodInfo methodInfo = new MethodInfo(psiMethod);
        PsiAnnotation methodMapping = getMethodMapping(psiMethod);
        ShowDocInterface showDocInterface = new ShowDocInterface();
        ApiGeneratorConfig state = config.getState();
        showDocInterface.setApi_key(state.apiKey);
        showDocInterface.setApi_token(state.apiToken);
        showDocInterface.setCat_name(state.defaultCat + "/" +
                DesUtil.getTitle(psiMethod.getContainingClass().getDocComment()));
        showDocInterface.setPage_title(DesUtil.getTitle(psiMethod.getDocComment()));
        showDocInterface.setPage_content(generateMDString(psiMethod, classRequestMapping, methodMapping));
        return showDocInterface;
    }

    /**
     * 生成md字符串
     *
     * @param selectedMethod      选择的方法
     * @param classRequestMapping 类的注释
     * @param methodMapping       方法的注释
     * @return md文档字符串
     */
    private String generateMDString(
            PsiMethod selectedMethod, PsiAnnotation classRequestMapping, PsiAnnotation methodMapping) {
        MethodInfo methodInfo = new MethodInfo(selectedMethod);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("**简要描述：**\n\n");
        stringBuilder.append("- ").append(methodInfo.getDesc()).append("\n\n");
        stringBuilder.append("**请求URL：**\n");
        stringBuilder.append("- `").append(buildPath(classRequestMapping, methodMapping)).append("`\n\n");
        stringBuilder.append("**请求方式：**\n");
        stringBuilder.append("- ").append(getMethodFromAnnotation(methodMapping).toString()).append("\n\n");
        if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
            stringBuilder.append("**请求参数：**\n\n");
            paramTableHeader(stringBuilder);
            for (FieldInfo fieldInfo : methodInfo.getRequestFields()) {
                fieldInfo(stringBuilder, fieldInfo);
            }
        }
        if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
            stringBuilder.append("**请求示例**\n");
            stringBuilder.append("```json\n");
            stringBuilder.append(JsonUtil.buildPrettyJson(methodInfo.getRequestFields())).append("\n");
            stringBuilder.append("```\n");
        }
        if (AssertUtils.isNotEmpty(methodInfo.getResponse().getChildren())) {
            stringBuilder.append("**返回参数说明**\n\n");
            paramTableHeader(stringBuilder);
            for (FieldInfo fieldInfo : methodInfo.getResponse().getChildren()) {
                fieldInfo(stringBuilder, fieldInfo);
            }
        }

        if (AssertUtils.isNotEmpty(methodInfo.getResponse().getChildren())) {
            stringBuilder.append("**返回示例**\n");
            stringBuilder.append("```json\n");
            stringBuilder.append(JsonUtil.buildPrettyJson(methodInfo.getResponse())).append("\n");
            stringBuilder.append("```\n");
        }
        return stringBuilder.toString();
    }

    /**
     * 写入请求参数表头
     *
     * @param stringBuilder 构建的字符串
     */
    private void paramTableHeader(StringBuilder stringBuilder) {
        stringBuilder.append("|名称|类型|必填|值域范围|描述/示例|\n");
        stringBuilder.append("|---|---|---|---|---|\n");
    }

    /**
     * 写入请求参数
     *
     * @param stringBuilder 待构建的字符串
     * @param info          变量信息
     */
    private void fieldInfo(StringBuilder stringBuilder, FieldInfo info) {
        stringBuilder.append(buildFieldStr(info));
        if (info.hasChildren()) {
            for (FieldInfo fieldInfo : info.getChildren()) {
                fieldInfo(stringBuilder, fieldInfo);
            }
        }
    }


    private String buildPath(PsiAnnotation classRequestMapping, PsiAnnotation methodMapping) {
        String classPath = getPathFromAnnotation(classRequestMapping);
        String methodPath = getPathFromAnnotation(methodMapping);
        return classPath + methodPath;
    }


    private String getPathFromAnnotation(PsiAnnotation annotation) {
        if (annotation == null) {
            return "";
        }
        PsiNameValuePair[] psiNameValuePairs = annotation.getParameterList().getAttributes();
        if (psiNameValuePairs.length == 1 && psiNameValuePairs[0].getName() == null) {
            return appendSlash(psiNameValuePairs[0].getLiteralValue());
        }
        if (psiNameValuePairs.length >= 1) {
            for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
                if (psiNameValuePair.getName().equals("value") || psiNameValuePair.getName().equals("path")) {
                    String text = psiNameValuePair.getValue().getText();
                    if (StringUtils.isEmpty(text)) {
                        return "";
                    }
                    text = text.replace("\"", "").replace("{", "").replace("}", "");
                    if (text.contains(",")) {
                        return appendSlash(text.split(",")[0]);
                    }
                    return appendSlash(text);
                }
            }
        }
        return "";
    }

    private String appendSlash(String path) {
        if (StringUtils.isEmpty(path)) {
            return "";
        }
        String p = path;
        if (!path.startsWith(SLASH)) {
            p = SLASH + path;
        }
        if (path.endsWith(SLASH)) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private RequestMethodEnum getMethodFromAnnotation(PsiAnnotation methodMapping) {
        String text = methodMapping.getText();
        if (text.contains(WebAnnotation.RequestMapping)) {
            return extractMethodFromAttribute(methodMapping);
        }
        return extractMethodFromMappingText(text);
    }

    private RequestMethodEnum extractMethodFromMappingText(String text) {
        if (text.contains(WebAnnotation.GetMapping)) {
            return RequestMethodEnum.GET;
        }
        if (text.contains(WebAnnotation.PutMapping)) {
            return RequestMethodEnum.PUT;
        }
        if (text.contains(WebAnnotation.DeleteMapping)) {
            return RequestMethodEnum.DELETE;
        }
        if (text.contains(WebAnnotation.PatchMapping)) {
            return RequestMethodEnum.PATCH;
        }
        return RequestMethodEnum.POST;
    }

    private RequestMethodEnum extractMethodFromAttribute(PsiAnnotation annotation) {
        PsiNameValuePair[] psiNameValuePairs = annotation.getParameterList().getAttributes();
        for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
            if ("method".equals(psiNameValuePair.getName())) {
                return RequestMethodEnum.valueOf(extractMethodName(psiNameValuePair));
            }
        }
        return RequestMethodEnum.POST;
    }

    private String extractMethodName(PsiNameValuePair psiNameValuePair) {
        PsiAnnotationMemberValue value = psiNameValuePair.getValue();
        if (value != null) {
            PsiReference reference = value.getReference();
            if (reference != null) {
                PsiElement resolve = reference.resolve();
                if (resolve != null) {
                    return resolve.getText();
                }
            }
            PsiElement[] children = value.getChildren();
            if (children.length == 0) {
                return RequestMethodEnum.POST.name();
            }
            if (children.length > 1) {
                for (PsiElement child : children) {
                    if (child instanceof PsiReference) {
                        PsiElement resolve = ((PsiReference) child).resolve();
                        if (resolve != null) {
                            return resolve.getText();
                        }
                    }
                }
            }
        }
        return RequestMethodEnum.POST.name();
    }

    private PsiAnnotation getMethodMapping(PsiMethod psiMethod) {
        for (PsiAnnotation annotation : psiMethod.getAnnotations()) {
            String text = annotation.getText();
            if (text.contains("Mapping")) {
                return annotation;
            }
        }
        return null;
    }

    private boolean hasMappingAnnotation(PsiMethod method) {
        PsiAnnotation[] annotations = method.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            if (annotation.getText().contains("Mapping")) {
                return true;
            }
        }
        return false;
    }

    private boolean haveControllerAnnotation(PsiClass psiClass) {
        PsiAnnotation[] annotations = psiClass.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            if (annotation.getText().contains(WebAnnotation.Controller)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void update(AnActionEvent e) {
        //perform action if and only if EDITOR != null
        boolean enabled = e.getData(CommonDataKeys.EDITOR) != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    private String getDirPath(Project project) {
        String dirPath = config.getState().dirPath;
        if (StringUtils.isEmpty(dirPath)) {
            return project.getBasePath() + "/target/api_docs";
        }

        if (dirPath.endsWith(SLASH)) {
            return dirPath.substring(0, dirPath.lastIndexOf(SLASH));
        }
        return dirPath;
    }

    private boolean generateDocForClass(Project project, PsiClass psiClass, String dirPath) throws IOException {
        if (!mkDirectory(project, dirPath)) {
            return false;
        }
        String fileName = psiClass.getName();
        File apiDoc = new File(dirPath + SLASH + fileName + ".md");
        boolean notExist = apiDoc.createNewFile();
        if (!notExist) {
            if (!config.getState().overwrite) {
                int choose = Messages.showOkCancelDialog(fileName + ".md already exists,do you want to overwrite it?", "Overwrite Warning!", "Yes", "No", Messages.getWarningIcon());
                if (Messages.CANCEL == choose) {
                    return false;
                }
            }
        }
        try (Writer md = new FileWriter(apiDoc)) {
            List<FieldInfo> fieldInfos = listFieldInfos(psiClass);
            md.write("## 示例\n");
            if (AssertUtils.isNotEmpty(fieldInfos)) {
                md.write("```json\n");
                md.write(JsonUtil.buildPrettyJson(fieldInfos) + "\n");
                md.write("```\n");
            }
            md.write("## 参数说明\n");
            if (AssertUtils.isNotEmpty(fieldInfos)) {
                writeParamTableHeader(md);
                for (FieldInfo fieldInfo : fieldInfos) {
                    writeFieldInfo(md, fieldInfo);
                }
            }
        }
        return true;
    }

    private void writeParamTableHeader(Writer md) throws IOException {
        md.write("名称|类型|必填|值域范围|描述/示例\n");
        md.write("---|---|---|---|---\n");
    }

    public List<FieldInfo> listFieldInfos(PsiClass psiClass) {
        List<FieldInfo> fieldInfos = new ArrayList<>();
        for (PsiField psiField : psiClass.getAllFields()) {
            if (config.getState().excludeFieldNames.contains(psiField.getName())) {
                continue;
            }
            fieldInfos.add(FieldFactory.buildField(psiClass.getProject(), psiField.getName(), psiField.getType(), DesUtil.getDescription(psiField), psiField.getAnnotations()));
        }
        return fieldInfos;
    }

    private boolean generateDocForMethod(Project project, PsiMethod selectedMethod, String dirPath) throws IOException {
        if (!mkDirectory(project, dirPath)) {
            return false;
        }
        MethodInfo methodInfo = new MethodInfo(selectedMethod);
        String fileName = getFileName(methodInfo);
        File apiDoc = new File(dirPath + SLASH + fileName + ".md");
        boolean notExist = apiDoc.createNewFile();
        if (!notExist) {
            if (!config.getState().overwrite) {
                int choose = Messages.showOkCancelDialog(fileName + ".md already exists,do you want to overwrite it?", "Overwrite Warning!", "Yes", "No", Messages.getWarningIcon());
                if (Messages.CANCEL == choose) {
                    return false;
                }
            }
        }
        Model pomModel = getPomModel(project);
        try (Writer md = new FileWriter(apiDoc)) {
            md.write("## " + fileName + "\n");
            md.write("## 功能介绍\n");
            md.write(methodInfo.getDesc() + "\n");
            md.write("## Maven依赖\n");
            md.write("```xml\n");
            md.write("<dependency>\n");
            md.write("\t<groupId>" + pomModel.getGroupId() + "</groupId>\n");
            md.write("\t<artifactId>" + pomModel.getArtifactId() + "</artifactId>\n");
            md.write("\t<version>" + pomModel.getVersion() + "</version>\n");
            md.write("</dependency>\n");
            md.write("```\n");
            md.write("## 接口声明\n");
            md.write("```java\n");
            md.write("package " + methodInfo.getPackageName() + ";\n\n");
            md.write("public interface " + methodInfo.getClassName() + " {\n\n");
            md.write("\t" + methodInfo.getReturnStr() + " " + methodInfo.getMethodName() + methodInfo.getParamStr() + ";\n\n");
            md.write("}\n");
            md.write("```\n");
            md.write("## 请求参数\n");
            md.write("### 请求参数示例\n");
            if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
                md.write("```json\n");
                md.write(JsonUtil.buildPrettyJson(methodInfo.getRequestFields()) + "\n");
                md.write("```\n");
            }
            md.write("### 请求参数说明\n");
            if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
                writeParamTableHeader(md);
                for (FieldInfo fieldInfo : methodInfo.getRequestFields()) {
                    writeFieldInfo(md, fieldInfo);
                }
            }
            md.write("\n## 返回结果\n");
            md.write("### 返回结果示例\n");
            if (AssertUtils.isNotEmpty(methodInfo.getResponse().getChildren())) {
                md.write("```json\n");
                md.write(JsonUtil.buildPrettyJson(methodInfo.getResponse()) + "\n");
                md.write("```\n");
            }
            md.write("### 返回结果说明\n");
            if (AssertUtils.isNotEmpty(methodInfo.getResponse().getChildren())) {
                writeParamTableHeader(md);
                for (FieldInfo fieldInfo : methodInfo.getResponse().getChildren()) {
                    writeFieldInfo(md, fieldInfo, "");
                }
            }
        }
        return true;
    }

    private boolean mkDirectory(Project project, String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                NotificationUtil.errorNotify("invalid directory path!", project);
                return false;
            }
        }
        return true;
    }

    private Model getPomModel(Project project) {
        PsiFile pomFile = FilenameIndex.getFilesByName(project, "pom.xml", GlobalSearchScope.projectScope(project))[0];
        String pomPath = pomFile.getContainingDirectory().getVirtualFile().getPath() + "/pom.xml";
        return readPom(pomPath);
    }

    private String getFileName(MethodInfo methodInfo) {
        if (!config.getState().cnFileName) {
            return methodInfo.getMethodName();
        }
        if (StringUtils.isEmpty(methodInfo.getDesc()) || !methodInfo.getDesc().contains(" ")) {
            return methodInfo.getMethodName();
        }
        return methodInfo.getDesc().split(" ")[0];
    }

    private void writeFieldInfo(Writer writer, FieldInfo info) throws IOException {
        writer.write(buildFieldStr(info));
        if (info.hasChildren()) {
            for (FieldInfo fieldInfo : info.getChildren()) {
                writeFieldInfo(writer, fieldInfo, getPrefix());
            }
        }
    }

    private String buildFieldStr(FieldInfo info) {
        return "|" + getFieldName(info) + "|" + info.getPsiType().getPresentableText() + "|" + getRequireStr(info.isRequire()) + "|" + getRange(info.getRange()) + "|" + info.getDesc() + "|" + "\n";
    }

    private String getFieldName(FieldInfo info) {
        if (info.hasChildren()) {
            return "**" + info.getName() + "**";
        }
        return info.getName();
    }

    private void writeFieldInfo(Writer writer, FieldInfo info, String prefix) throws IOException {
        writer.write(prefix + buildFieldStr(info));
        if (info.hasChildren()) {
            for (FieldInfo fieldInfo : info.getChildren()) {
                writeFieldInfo(writer, fieldInfo, getPrefix() + prefix);
            }
        }
    }

    private String getPrefix() {
        String prefix = config.getState().prefix;
        if (" ".equals(prefix)) {
            return "&emsp";
        }
        return prefix;
    }

    private String getRequireStr(boolean isRequire) {
        return isRequire ? "Y" : "N";
    }

    private String getRange(String range) {
        return AssertUtils.isEmpty(range) ? "N/A" : range;
    }

    public Model readPom(String pom) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            return reader.read(new FileReader(pom));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
