package com.zhuo.videomusicimport.saver;

import com.zhuo.videomusicimport.spider.Downloader;

public class SaverFactory {
    private static final LocalSaver localSaver = new LocalSaver();

    public static final String local = "LOCAL";

    public static Saver getSaver(String location) {
        return localSaver;
    }
}
