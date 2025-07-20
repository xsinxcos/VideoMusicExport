package com.zhuo.videomusicimport.spider;

import com.zhuo.videomusicimport.saver.Saver;

import java.io.File;

public interface Downloader {
    File crawl(String url);
}
