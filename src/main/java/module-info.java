module com.zhuo.videomusicimport {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires okhttp3;
    requires com.fasterxml.jackson.databind;
    requires java.prefs;
    requires org.bytedeco.javacv;
    requires org.bytedeco.ffmpeg;

    opens com.zhuo.videomusicimport to javafx.fxml;
    exports com.zhuo.videomusicimport;
    exports com.zhuo.videomusicimport.utils;
    opens com.zhuo.videomusicimport.utils to javafx.fxml;
}