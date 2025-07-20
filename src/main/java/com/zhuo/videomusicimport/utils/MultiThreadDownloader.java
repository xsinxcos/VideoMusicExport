package com.zhuo.videomusicimport.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多线程分片下载器
 * 支持断点续传、自动重试、智能分片
 */
public class MultiThreadDownloader {
    
    private final OkHttpClient client;
    private final int threadCount;
    private final long chunkSize;
    private final int maxRetries;
    private final ExecutorService executor;
    
    public MultiThreadDownloader() {
        this(8, 1024 * 1024 * 2, 3); // 默认8线程，2MB分片，3次重试
    }
    
    public MultiThreadDownloader(int threadCount, long chunkSize, int maxRetries) {
        this.threadCount = threadCount;
        this.chunkSize = chunkSize;
        this.maxRetries = maxRetries;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.executor = Executors.newFixedThreadPool(threadCount);
    }
    
    /**
     * 下载文件
     * @param request OkHttp请求对象
     * @param savePath 保存路径
     * @param callback 进度回调
     * @return 下载结果
     */
    public DownloadResult download(Request request, String savePath, ProgressCallback callback) {
        try {
            // 1. 获取文件信息
            FileInfo fileInfo = getFileInfo(request);
            if (fileInfo == null) {
                return new DownloadResult(false, "无法获取文件信息");
            }
            
            Path path = Paths.get(savePath);
            Files.createDirectories(path.getParent());
            
            // 2. 检查断点续传
            long existingSize = Files.exists(path) ? Files.size(path) : 0;
            if (existingSize >= fileInfo.totalSize) {
                return new DownloadResult(true, "文件已存在且完整");
            }
            
            // 3. 计算分片
            List<ChunkInfo> chunks = calculateChunks(fileInfo.totalSize, existingSize);
            
            // 4. 执行多线程下载
            return executeDownload(request, savePath, chunks, fileInfo.totalSize, callback);
            
        } catch (Exception e) {
            return new DownloadResult(false, "下载失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件信息
     */
    private FileInfo getFileInfo(Request request) throws IOException {
        Request headRequest = request.newBuilder()
            .head()
            .build();
            
        try (Response response = client.newCall(headRequest).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            
            String contentLength = response.header("Content-Length");
            boolean supportRange = "bytes".equals(response.header("Accept-Ranges"));
            
            long totalSize = contentLength != null ? Long.parseLong(contentLength) : -1;
            
            return new FileInfo(totalSize, supportRange);
        }
    }
    
    /**
     * 计算分片信息
     */
    private List<ChunkInfo> calculateChunks(long totalSize, long existingSize) {
        List<ChunkInfo> chunks = new ArrayList<>();
        
        if (totalSize <= 0) {
            // 无法确定文件大小，使用单线程下载
            chunks.add(new ChunkInfo(0, existingSize, -1));
            return chunks;
        }
        
        // 智能分片算法
        long remainingSize = totalSize - existingSize;
        long actualChunkSize = Math.min(chunkSize, remainingSize / threadCount);
        actualChunkSize = Math.max(actualChunkSize, 1024 * 512); // 最小512KB
        
        long start = existingSize;
        int chunkIndex = 0;
        
        while (start < totalSize) {
            long end = Math.min(start + actualChunkSize - 1, totalSize - 1);
            chunks.add(new ChunkInfo(chunkIndex++, start, end));
            start = end + 1;
        }
        
        return chunks;
    }
    
    /**
     * 执行多线程下载
     */
    private DownloadResult executeDownload(Request request, String savePath, 
                                         List<ChunkInfo> chunks, long totalSize, 
                                         ProgressCallback callback) {
        try {
            // 创建临时文件用于写入
            RandomAccessFile file = new RandomAccessFile(savePath, "rw");
            if (totalSize > 0) {
                file.setLength(totalSize);
            }
            
            // 进度跟踪
            AtomicLong downloadedBytes = new AtomicLong(0);
            AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
            
            // 提交下载任务
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            
            for (ChunkInfo chunk : chunks) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> 
                    downloadChunk(request, file, chunk, downloadedBytes, totalSize, 
                                callback, startTime), executor);
                futures.add(future);
            }
            
            // 等待所有任务完成
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allTasks.get(10, TimeUnit.MINUTES); // 10分钟超时
            
            // 检查是否所有分片都成功
            boolean allSuccess = futures.stream()
                .allMatch(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        return false;
                    }
                });
            
            file.close();
            
            if (allSuccess) {
                if (callback != null) {
                    callback.onProgress(100, totalSize, totalSize);
                }
                return new DownloadResult(true, "下载完成");
            } else {
                return new DownloadResult(false, "部分分片下载失败");
            }
            
        } catch (Exception e) {
            return new DownloadResult(false, "下载执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载单个分片
     */
    private boolean downloadChunk(Request originalRequest, RandomAccessFile file, 
                                ChunkInfo chunk, AtomicLong downloadedBytes, 
                                long totalSize, ProgressCallback callback,
                                AtomicLong startTime) {
        int retryCount = 0;
        
        while (retryCount <= maxRetries) {
            try {
                Request.Builder requestBuilder = originalRequest.newBuilder();
                
                // 添加Range头
                if (chunk.end > 0) {
                    requestBuilder.addHeader("Range", 
                        String.format("bytes=%d-%d", chunk.start, chunk.end));
                }
                
                Request request = requestBuilder.build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code());
                    }
                    
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("响应体为空");
                    }
                    
                    // 写入文件
                    try (InputStream inputStream = body.byteStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long position = chunk.start;
                        
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            synchronized (file) {
                                file.seek(position);
                                file.write(buffer, 0, bytesRead);
                            }
                            
                            position += bytesRead;
                            long downloaded = downloadedBytes.addAndGet(bytesRead);
                            
                            // 更新进度
                            if (callback != null && totalSize > 0) {
                                long elapsed = System.currentTimeMillis() - startTime.get();
                                double progress = (double) downloaded / totalSize * 100;
                                double speed = downloaded / (elapsed / 1000.0); // bytes/s
                                
                                callback.onProgress(progress, downloaded, totalSize);
                                
                                if (elapsed % 1000 < 100) { // 每秒更新一次速度
                                    callback.onSpeedUpdate(speed);
                                }
                            }
                        }
                    }
                }
                
                return true; // 成功
                
            } catch (Exception e) {
                retryCount++;
                if (retryCount <= maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount); // 递增延迟重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    System.err.println("分片 " + chunk.index + " 下载失败: " + e.getMessage());
                }
            }
        }
        
        return false; // 失败
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    // 内部类
    private static class FileInfo {
        final long totalSize;
        final boolean supportRange;
        
        FileInfo(long totalSize, boolean supportRange) {
            this.totalSize = totalSize;
            this.supportRange = supportRange;
        }
    }
    
    private static class ChunkInfo {
        final int index;
        final long start;
        final long end;
        
        ChunkInfo(int index, long start, long end) {
            this.index = index;
            this.start = start;
            this.end = end;
        }
    }

    public record DownloadResult(boolean success, String message) {
    }
    
    public interface ProgressCallback {
        void onProgress(double progress, long downloaded, long total);
        default void onSpeedUpdate(double bytesPerSecond) {}
    }

//    public static void main(String[] args) {
//
//        MultiThreadDownloader downloader = new MultiThreadDownloader(8, 1024*1024*2, 3);
//
//        Request request = new Request.Builder()
//                .url("https://upos-sz-estgoss.bilivideo.com/upgcxcode/03/01/585600103/585600103_nb2-1-16.mp4?e=ig8euxZM2rNcNbRVhwdVhwdlhWdVhwdVhoNvNC8BqJIzNbfqXBvEqxTEto8BTrNvN0GvT90W5JZMkX_YN0MvXg8gNEV4NC8xNEV4N03eN0B5tZlqNxTEto8BTrNvNeZVuJ10Kj_g2UB02J0mN0B5tZlqNCNEto8BTrNvNC7MTX502C8f2jmMQJ6mqF2fka1mqx6gqj0eN0B599M=&trid=ecbb84458802412eb2519148148e963u&mid=0&deadline=1751108024&nbs=1&uipk=5&oi=1885179411&os=upos&platform=pc&og=cos&gen=playurlv3&upsig=03984d249bce8c6675298096df73aa5a&uparams=e,trid,mid,deadline,nbs,uipk,oi,os,platform,og,gen&bvc=vod&nettype=0&bw=290688&agrr=1&buvid=&build=0&dl=0&f=u_0_0&orderid=1,3")
//                .method("GET", null)
//                .addHeader("Host", "upos-sz-estgoss.bilivideo.com")
//                .addHeader("Referer", "https://www.bilibili.com/video/BV17F411T7Ao?spm_id_from=333.788.videopod.episodes&vd_source=f9a2b09b0f09436426d0a2b68590a1fc&p=92")
//                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
//                .build();
//
//        // 进度回调
//        MultiThreadDownloader.ProgressCallback callback = new MultiThreadDownloader.ProgressCallback() {
//            @Override
//            public void onProgress(double progress, long downloaded, long total) {
//                System.out.printf("进度: %.2f%% (%d/%d bytes)\n",
//                        progress, downloaded, total);
//            }
//
//            @Override
//            public void onSpeedUpdate(double bytesPerSecond) {
//                double mbps = bytesPerSecond / (1024 * 1024);
//                System.out.printf("下载速度: %.2f MB/s\n", mbps);
//            }
//        };
//
//        // 开始下载
//        MultiThreadDownloader.DownloadResult result = downloader.download(
//                request, "temp/video/video.mp4", callback);
//        System.out.println("下载结果: " + result.message);
//
//        // 关闭下载器
//        downloader.shutdown();
//    }
}