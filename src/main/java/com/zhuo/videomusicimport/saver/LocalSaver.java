package com.zhuo.videomusicimport.saver;

import com.zhuo.videomusicimport.SettingsController;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalSaver implements Saver {
    @Override
    public void save(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("源文件不存在");
        }

        try {
            // 获取设置中的下载目录
            String targetDir = SettingsController.getDownloadPath();
            Path targetPath = Path.of(targetDir, sourceFile.getName());

            // 如果目标文件已存在，则先删除
            Files.deleteIfExists(targetPath);

            // 将文件移动到目标目录
            Files.move(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("文件已保存到: " + targetPath);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void save(byte[] file ,String filename) {
        if (file == null || file.length == 0) {
            throw new IllegalArgumentException("文件数据为空");
        }

        try {
            Path targetPath = Path.of(filename);

            // 如果目标文件已存在，则先删除
            Files.deleteIfExists(targetPath);

            // 将字节数组写入文件
            Files.write(targetPath, file);

            System.out.println("文件已保存到: " + targetPath);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }
}
