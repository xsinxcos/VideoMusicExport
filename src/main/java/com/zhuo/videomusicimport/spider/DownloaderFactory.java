package com.zhuo.videomusicimport.spider;


public class DownloaderFactory {
    private static final BiliBiliDownloader biliBiliDownloader = new BiliBiliDownloader();

    private static final DefaultDownloader defaultDownloader = new DefaultDownloader();

    public static final String BILIBILI = "bilibili";

    public static Downloader getDownloader(String location) {
        if(BILIBILI.equalsIgnoreCase(location)){
            return biliBiliDownloader;
        }
        return defaultDownloader;
    }
}
