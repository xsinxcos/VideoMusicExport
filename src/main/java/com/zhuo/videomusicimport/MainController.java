package com.zhuo.videomusicimport;

import com.zhuo.videomusicimport.saver.Saver;
import com.zhuo.videomusicimport.saver.SaverFactory;
import com.zhuo.videomusicimport.spider.Downloader;
import com.zhuo.videomusicimport.spider.DownloaderFactory;
import com.zhuo.videomusicimport.utils.FFmpegUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    @FXML
    private TextField urlInput;

    @FXML
    private TextField audioNameInput;

    @FXML
    private RadioButton localDownload;

    @FXML
    private RadioButton bilibiliPlatform;

    @FXML
    private RadioButton localPlatform;

    @FXML
    private ToggleGroup downloadType;

    @FXML
    private ToggleGroup platformType;

    @FXML
    private ComboBox<String> formatComboBox;

    @FXML
    protected void onDownloadButtonClick() {
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

        // 根据平台选择创建下载器
        if (localPlatform.isSelected()) {
            downloader = DownloaderFactory.getDownloader(DownloaderFactory.LOCAL);
        } else if (bilibiliPlatform.isSelected()) {
            downloader = DownloaderFactory.getDownloader(DownloaderFactory.BILIBILI);
        }

        if (localDownload.isSelected()) {
            saver = SaverFactory.getSaver(SaverFactory.local);
        }

        if (downloader != null && saver != null) {
            // 创建进度对话框
            Dialog<Void> progressDialog = new Dialog<>();
            progressDialog.setTitle("处理中");
            progressDialog.setHeaderText(null);
            progressDialog.initModality(Modality.APPLICATION_MODAL);

            // 创建进度条
            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(300);
            Label statusLabel = new Label("准备开始...");

            // 设置对话框内容
            DialogPane dialogPane = progressDialog.getDialogPane();
            dialogPane.setContent(new javafx.scene.layout.VBox(10, statusLabel, progressBar));
            dialogPane.getButtonTypes().add(ButtonType.CANCEL);

            // 创建后台任务
            final Downloader finalDownloader = downloader;
            final Saver finalSaver = saver;
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    File videoFile = null;
                    try {
                        // 更新状态：下载视频
                        updateProgress(0, 3);
                        updateMessage("正在下载视频...");
                        videoFile = finalDownloader.crawl(url);

                        // 更新状态：提取音频
                        updateProgress(1, 3);
                        updateMessage("正在提取音频...");
                        // 使用选定的音频格式
                        byte[] audioAsBytes = FFmpegUtils.extractAudioAsBytes(videoFile, formatComboBox.getValue());

                        // 准备音频文件名
                        String finalAudioName;
                        if (audioName.isBlank()) {
                            finalAudioName = "audio_" + System.currentTimeMillis() + "." + formatComboBox.getValue();
                        } else {
                            finalAudioName = audioName + "." + formatComboBox.getValue();
                        }

                        // 更新状态：保存音频
                        updateProgress(2, 3);
                        updateMessage("正在保存音频...");
                        finalSaver.save(audioAsBytes, downloadPath + "/" + finalAudioName);

                        // 完成
                        updateProgress(3, 3);
                        updateMessage("处理完成！");

                        Platform.runLater(() -> {
                            progressDialog.setResult(null);
                            progressDialog.close();
                            showSuccess("下载成功", "音频已保存到指定目录");
                        });

                        return null;
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            progressDialog.close();
                            showAlert("下载失败", e.getMessage());
                        });
                        throw e;
                    } finally {
                        if (videoFile != null) {
                            videoFile.delete();
                        }
                    }
                }
            };

            // 绑定进度条和状态标签
            progressBar.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.messageProperty());

            // 处理取消按钮
            dialogPane.getButtonTypes().setAll(ButtonType.CANCEL);
            dialogPane.lookupButton(ButtonType.CANCEL).setOnMouseClicked(event -> {
                task.cancel();
                progressDialog.close();
            });

            // 启动任务
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();

            // 显示对话框
            progressDialog.showAndWait();
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化音频格式下拉框
        formatComboBox.getItems().addAll(
            FFmpegUtils.FORMAT_MP3,
            FFmpegUtils.FORMAT_WAV,
            FFmpegUtils.FORMAT_AAC,
            FFmpegUtils.FORMAT_FLAC,
            FFmpegUtils.FORMAT_OGG,
            FFmpegUtils.FORMAT_M4A
        );
        formatComboBox.setValue(FFmpegUtils.FORMAT_MP3); // 默认选择MP3格式
    }
}

