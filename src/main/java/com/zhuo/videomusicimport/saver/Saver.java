package com.zhuo.videomusicimport.saver;

import java.io.File;

public interface Saver {
    void save(File file);

    void save(byte[] file ,String filename);
}
