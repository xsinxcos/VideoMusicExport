package com.zhuo.videomusicimport.spider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuo.videomusicimport.SettingsController;
import com.zhuo.videomusicimport.saver.Saver;
import com.zhuo.videomusicimport.utils.MultiThreadDownloader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliDownloader implements Downloader {
    private final Pattern bvidAndcidPattern = Pattern.compile("\"bvid\":\"(.*?)\".*?\"cid\":(\\d+)");

    private final ObjectMapper mapper = new ObjectMapper();

    private final MultiThreadDownloader multiThreadDownloader = new MultiThreadDownloader();

    OkHttpClient client = new OkHttpClient().newBuilder().build();


    @Override
    public File crawl(String url) {
        Map<String, String> dataMap = getAidAndCid(url);
        String downLoadURL = getDownLoadURL(dataMap.get("bvid"), dataMap.get("cid"));
        return downLoad(downLoadURL, url, dataMap.get("bvid"), dataMap.get("cid"));
    }

    private Map<String, String> getAidAndCid(String url) {
        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("Host", "www.bilibili.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();
        try (Response response = client.newCall(request).execute()) {
            // 3. 检查响应状态
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            // 4. 获取 HTML 内容
            String html = Objects.requireNonNull(response.body()).string();
            Matcher matcher = bvidAndcidPattern.matcher(html.replaceAll("\\s+", "")); // 去除空格

            if (matcher.find()) {
                String bvid = matcher.group(1); // BV1EbNHewEeu
                String cid = matcher.group(2);  // 28245164041
                return Map.of("bvid", bvid, "cid", cid);
            }

        } catch (IOException e) {
            System.err.println("请求失败: " + e.getMessage());
        }
        return null;
    }

    private String getDownLoadURL(String bvid, String cid) {
        Request request = new Request.Builder()
                .url("https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + cid)
                .method("GET", null)
                .addHeader("Host", "api.bilibili.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonNode rootNode = mapper.readTree(Objects.requireNonNull(response.body()).bytes());
            return rootNode.get("data").get("durl").get(0).get("backup_url").get(0).asText();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File downLoad(String downloadURL, String referer, String bv, String cid) {
        long timeMillis = System.currentTimeMillis();
        // 使用设置中的下载目录
        String downloadPath = SettingsController.getDownloadPath();
        Path dirPath = Paths.get(downloadPath);
        String filename = bv + "-" + cid + ".mp4";
        File outputFile = dirPath.resolve(filename).toFile();
        // 存在文件直接返回
        if (outputFile.exists()) {
            return outputFile;
        }
        URI uri = URI.create(downloadURL);
        String host = uri.getHost();
        Request request = new Request.Builder()
                .url(downloadURL)
                .method("GET", null)
                .addHeader("Host", host)
                .addHeader("Referer", referer)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();

        multiThreadDownloader.download(request, outputFile.getAbsolutePath(), (progress, downloaded, total) -> {
        });
        return outputFile;
    }
}
