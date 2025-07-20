package com.zhuo.videomusicimport.spider;


import com.zhuo.videomusicimport.saver.Saver;

import java.io.File;

public class DefaultDownloader implements Downloader{
    @Override
    public File crawl(String url) {
        return new File(url);
    }
}
