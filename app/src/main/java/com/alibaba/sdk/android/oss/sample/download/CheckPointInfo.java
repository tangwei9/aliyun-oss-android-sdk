package com.alibaba.sdk.android.oss.sample.download;

/**
 * 断点信息类
 */
public class CheckPointInfo {
    private String fileName;    // 文件名称
    private String url;         // 网络资源定位符
    private String filePath;    // 文件的本地下载目录
    private long chunkSize;     // 分片大小

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(long chunkSize) {
        this.chunkSize = chunkSize;
    }

    public CheckPointInfo(String fileName, String url, String filePath) {
        this.fileName = fileName;
        this.url = url;
        this.filePath = filePath;
    }
}
