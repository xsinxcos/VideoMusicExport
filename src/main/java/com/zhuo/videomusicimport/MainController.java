package com.zhuo.videomusicimport;

import com.zhuo.videomusicimport.saver.Saver;
import com.zhuo.videomusicimport.saver.SaverFactory;
import com.zhuo.videomusicimport.spider.Downloader;
import com.zhuo.videomusicimport.spider.DownloaderFactory;
import com.zhuo.videomusicimport.utils.FFmpegUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class MainController {
    @FXML
    private TextField urlInput;

    @FXML
    private TextField audioNameInput;

    @FXML
    private RadioButton localDownload;

    @FXML
    private RadioButton cloudDownload;

    @FXML
    private RadioButton bilibiliPlatform;

    @FXML
    private RadioButton youtubePlatform;

    @FXML
    private ToggleGroup downloadType;

    @FXML
    private ToggleGroup platformType;

    @FXML
    protected void onDownloadButtonClick() throws IOException {
        String url = urlInput.getText();
        String audioName = audioNameInput.getText();
        Downloader downloader = null;
        Saver saver = null;
        String downloadPath = SettingsController.getDownloadPath();

        // 验证输入
        if (url == null || url.trim().isEmpty()) {
            showAlert("错误", "请输入视频链接");
            return;
        }
        if (bilibiliPlatform.isSelected()) {
            downloader = DownloaderFactory.getDownloader(DownloaderFactory.BILIBILI);
        }
        if (localDownload.isSelected()) {
            saver = SaverFactory.getSaver(SaverFactory.local);
        }
        if (downloader != null && saver != null) {
            File videoFile = null;
            try {
                videoFile = downloader.crawl(url);
                byte[] audioAsBytes = FFmpegUtils.extractAudioAsBytes(videoFile);
                if (audioName.isBlank()) {
                    audioName = "audio_" + System.currentTimeMillis() + ".mp3";
                } else {
                    audioName += ".mp3";
                }
                saver.save(audioAsBytes, downloadPath + "/" + audioName);
                // 显示成功提示
                showSuccess("下载成功", "音频已保存到指定目录");
            } catch (Exception e) {
                showAlert("下载失败", e.getMessage());
                throw new RuntimeException(e);
            } finally {
                //删除视频文件
                if (videoFile != null) {
                    videoFile.delete();
                }
            }
        }
    }

    @FXML
    protected void onSettingsButtonClick() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 500, 200);
            Stage stage = new Stage();
            stage.setTitle("设置");
            stage.initModality(Modality.APPLICATION_MODAL);

            SettingsController controller = fxmlLoader.getController();
            controller.setStage(stage);

            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("错误", "无法打开设置窗口: " + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showSuccess(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}