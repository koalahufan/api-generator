package site.forgus.plugins.apigenerator.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.*;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import site.forgus.plugins.apigenerator.util.AssertUtils;
import site.forgus.plugins.apigenerator.util.NotificationUtil;
import site.forgus.plugins.apigenerator.yapi.sdk.ConfigException;
import site.forgus.plugins.apigenerator.yapi.sdk.YApiSdk;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collections;

public class ApiGeneratorSetting implements Configurable {

    private ApiGeneratorConfig oldState;


    JBTextField dirPathTextField;
    JBTextField prefixTextField;
    JBCheckBox cnFileNameCheckBox;
    JBCheckBox overwriteCheckBox;

    JBTextField showDocUrlTextField;
    JBTextField tokenTextField;
    JBTextField keyTextField;
    JBTextField defaultCatTextField;
    JBTextField excludeFields;

    public ApiGeneratorSetting(Project project) {
        oldState = ServiceManager.getService(project,ApiGeneratorConfig.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Api Generator Setting";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JBTabbedPane jbTabbedPane = new JBTabbedPane();
        GridBagLayout layout = new GridBagLayout();
        //normal setting
        JBPanel normalPanel = new JBPanel(layout);

        normalPanel.add(buildLabel(layout, "Exclude Fields:"));
        excludeFields = new JBTextField(oldState.excludeFields);
        layout.setConstraints(excludeFields, getValueConstraints());
        normalPanel.add(excludeFields);

        normalPanel.add(buildLabel(layout, "Save Directory:"));
        dirPathTextField = buildTextField(layout, oldState.dirPath);
        normalPanel.add(dirPathTextField);

        normalPanel.add(buildLabel(layout, "Indent Style:"));
        prefixTextField = buildTextField(layout, oldState.prefix);
        normalPanel.add(prefixTextField);

        overwriteCheckBox = buildJBCheckBox(layout, "Overwrite exists docs", oldState.overwrite);
        normalPanel.add(overwriteCheckBox);

        cnFileNameCheckBox = buildJBCheckBox(layout, "Extract filename from doc comments", oldState.cnFileName);
        normalPanel.add(cnFileNameCheckBox);


        jbTabbedPane.addTab("Api Setting", normalPanel);

        //YApi setting
        JBPanel showDocPanel = new JBPanel(layout);

        showDocPanel.add(buildLabel(layout, "showDoc server url:"));
        showDocUrlTextField = buildTextField(layout, oldState.showDocServerUrl);
        showDocPanel.add(showDocUrlTextField);

        showDocPanel.add(buildLabel(layout, "api key:"));
        keyTextField = buildTextField(layout, oldState.apiKey);
        showDocPanel.add(keyTextField);

        showDocPanel.add(buildLabel(layout, "api token:"));
        tokenTextField = buildTextField(layout, oldState.apiToken);
        showDocPanel.add(tokenTextField);

        showDocPanel.add(buildLabel(layout, "Default save category:"));
        defaultCatTextField = buildTextField(layout, oldState.defaultCat);
        showDocPanel.add(defaultCatTextField);

        jbTabbedPane.addTab("ShowDoc Setting", showDocPanel);
        return jbTabbedPane;
    }

    private JBCheckBox buildJBCheckBox(GridBagLayout layout, String text, boolean selected) {
        JBCheckBox checkBox = new JBCheckBox();
        checkBox.setText(text);
        checkBox.setSelected(selected);
        layout.setConstraints(checkBox, getValueConstraints());
        return checkBox;
    }

    private JBLabel buildLabel(GridBagLayout layout, String name) {
        JBLabel jbLabel = new JBLabel(name);
        layout.setConstraints(jbLabel, getLabelConstraints());
        return jbLabel;
    }

    private JBTextField buildTextField(GridBagLayout layout, String text) {
        JBTextField textField = new JBTextField(text);
        layout.setConstraints(textField, getValueConstraints());
        return textField;
    }

    private GridBagConstraints getLabelConstraints() {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.fill = GridBagConstraints.EAST;
        labelConstraints.gridwidth = 1;
        return labelConstraints;
    }

    private GridBagConstraints getValueConstraints() {
        GridBagConstraints textConstraints = new GridBagConstraints();
        textConstraints.fill = GridBagConstraints.WEST;
        textConstraints.gridwidth = GridBagConstraints.REMAINDER;
        return textConstraints;
    }

    @Override
    public boolean isModified() {
        return !oldState.prefix.equals(prefixTextField.getText()) ||
                oldState.cnFileName != cnFileNameCheckBox.isSelected() ||
                oldState.overwrite != overwriteCheckBox.isSelected() ||
                !oldState.showDocServerUrl.equals(showDocUrlTextField.getText()) ||
                !oldState.apiKey.equals(keyTextField.getText()) ||
                !oldState.apiToken.equals(tokenTextField.getText()) ||
                !oldState.defaultCat.equals(defaultCatTextField.getText()) ||
                !oldState.dirPath.equals(dirPathTextField.getText()) ||
                !oldState.excludeFields.equals(excludeFields.getText());
    }

    @Override
    public void apply() {
        oldState.excludeFields = excludeFields.getText();
        if (!StringUtils.isEmpty(excludeFields.getText())) {
            String[] split = excludeFields.getText().split(",");
            Collections.addAll(oldState.excludeFieldNames, split);
        }
        oldState.dirPath = dirPathTextField.getText();
        oldState.prefix = prefixTextField.getText();
        oldState.cnFileName = cnFileNameCheckBox.isSelected();
        oldState.overwrite = overwriteCheckBox.isSelected();
        oldState.showDocServerUrl = showDocUrlTextField.getText();
        oldState.apiKey = keyTextField.getText();
        oldState.apiToken = tokenTextField.getText();
        oldState.defaultCat = defaultCatTextField.getText();
    }

}
