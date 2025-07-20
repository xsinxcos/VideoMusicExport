package com.zhuo.videomusicimport.utils;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FFmpegUtils {

    /**
     * 从视频文件中提取音频并返回字节数组
     *
     * @param videoFile 输入视频文件
     * @return 音频数据的字节数组
     * @throws FrameGrabber.Exception
     * @throws FrameRecorder.Exception
     * @throws IOException
     */
    public static byte[] extractAudioAsBytes(File videoFile)
            throws FrameGrabber.Exception, FrameRecorder.Exception, IOException {
        // 设置日志级别为错误级别，减少控制台输出
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);

        // 创建临时文件用于存储MP3数据
        Path tempFile = Files.createTempFile("audio_", ".mp3");
        try {
            // 创建视频抓取器
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();

            // 创建音频记录器
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(tempFile.toString(),
                    grabber.getAudioChannels());

            // 设置音频相关参数
            recorder.setFormat("mp3");
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setAudioQuality(0); // 最高质量
            recorder.setAudioBitrate(grabber.getAudioBitrate());

            // 开始记录
            recorder.start();

            // 逐帧处理
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                if (frame.samples != null) { // 只处理音频帧
                    recorder.record(frame);
                }
            }

            // 关闭资源
            recorder.stop();
            recorder.release();
            grabber.stop();
            grabber.release();

            // 读取临时文件内容
            byte[] audioData = Files.readAllBytes(tempFile);
            return audioData;
        } finally {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查文件是否为视频文件
     *
     * @param file 要检查的文件
     * @return 是否为视频文件
     */
    public static boolean isVideoFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            String mimeType = Files.probeContentType(file.toPath());
            return mimeType != null && mimeType.startsWith("video/");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
