package com.zhuo.videomusicimport;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.prefs.Preferences;

public class SettingsController {
    @FXML
    private TextField downloadPathField;

    private Stage stage;
    private final Preferences prefs = Preferences.userRoot().node("VideoMusicImport");

    @FXML
    public void initialize() {
        String savedPath = prefs.get("downloadPath", System.getProperty("user.home") + File.separator + "Downloads");
        downloadPathField.setText(savedPath);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    protected void onChooseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择下载目录");

        String currentPath = downloadPathField.getText();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }

        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            downloadPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    protected void onSave() {
        String path = downloadPathField.getText();
        prefs.put("downloadPath", path);
        stage.close();
    }

    @FXML
    protected void onCancel() {
        stage.close();
    }

    public static String getDownloadPath() {
        Preferences prefs = Preferences.userRoot().node("VideoMusicImport");
        return prefs.get("downloadPath", System.getProperty("user.home") + File.separator + "Downloads");
    }
}
