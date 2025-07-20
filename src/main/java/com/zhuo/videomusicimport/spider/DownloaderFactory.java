package com.zhuo.videomusicimport.spider;


public class DownloaderFactory {
    private static final BiliBiliDownloader biliBiliDownloader = new BiliBiliDownloader();

    private static final DefaultDownloader defaultDownloader = new DefaultDownloader();

    public static final String BILIBILI = "bilibili";

    public static final String LOCAL = "local";

    public static Downloader getDownloader(String location) {
        if(BILIBILI.equalsIgnoreCase(location)){
            return biliBiliDownloader;
        } else if (LOCAL.equalsIgnoreCase(location)) {
            return defaultDownloader;
        }
        return defaultDownloader;
    }
}
