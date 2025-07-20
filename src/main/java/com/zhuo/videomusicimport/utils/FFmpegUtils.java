package com.zhuo.videomusicimport.utils;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FFmpegUtils {
    // 支持的音频格式常量
    public static final String FORMAT_MP3 = "mp3";
    public static final String FORMAT_WAV = "wav";
    public static final String FORMAT_AAC = "aac";
    public static final String FORMAT_FLAC = "flac";
    public static final String FORMAT_OGG = "ogg";
    public static final String FORMAT_M4A = "m4a";

    /**
     * 使用默认格式(MP3)从视频文件中提取音频
     *
     * @param videoFile 输入视频文件
     * @return 音频数据的字节数组
     */
    public static byte[] extractAudioAsBytes(File videoFile)
            throws FrameGrabber.Exception, FrameRecorder.Exception, IOException {
        return extractAudioAsBytes(videoFile, FORMAT_MP3);
    }

    /**
     * 从视频文件中提取指定格式的音频
     *
     * @param videoFile 输入视频文件
     * @param format 目标音频格式，支持的格式：mp3, wav, aac, flac, ogg, m4a
     * @return 音频数据的字节数组
     */
    public static byte[] extractAudioAsBytes(File videoFile, String format)
            throws FrameGrabber.Exception, FrameRecorder.Exception, IOException {
        // 验证格式
        format = format.toLowerCase();
        if (!isFormatSupported(format)) {
            throw new IllegalArgumentException("Unsupported audio format: " + format);
        }

        // 设置日志级别为错误级别，减少控制台输出
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);

        // 创建临时文件用于存储音频数据
        Path tempFile = Files.createTempFile("audio_", "." + format);
        try {
            // 创建视频抓取器
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();

            // 创建音频记录器
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(tempFile.toString(),
                    grabber.getAudioChannels());

            // 设置音频相关参数
            recorder.setFormat(format);
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setAudioQuality(0); // 最高质量

            // 根据不同格式设置适当的比特率
            if (FORMAT_MP3.equals(format) || FORMAT_AAC.equals(format)) {
                recorder.setAudioBitrate(grabber.getAudioBitrate());
            } else if (FORMAT_FLAC.equals(format)) {
                // FLAC 使用固定比特率
                recorder.setAudioBitrate(1024000); // ~1024kbps
            }

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
            return Files.readAllBytes(tempFile);
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
     * 检查是否支持指定的音频格式
     *
     * @param format 音频格式
     * @return 是否支持
     */
    public static boolean isFormatSupported(String format) {
        if (format == null) return false;
        format = format.toLowerCase();
        return format.equals(FORMAT_MP3) ||
               format.equals(FORMAT_WAV) ||
               format.equals(FORMAT_AAC) ||
               format.equals(FORMAT_FLAC) ||
               format.equals(FORMAT_OGG) ||
               format.equals(FORMAT_M4A);
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
