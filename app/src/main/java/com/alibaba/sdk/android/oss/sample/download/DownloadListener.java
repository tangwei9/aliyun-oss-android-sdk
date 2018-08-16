package com.alibaba.sdk.android.oss.sample.download;

/**
 * 下载监听接口
 */
public interface DownloadListener {

    /**
     * 下载中的进度回调
     * @param progress
     *        下载进度
     */
    void onProgress(float progress);
    /**
     * 下载完成
     */
    void onFinished();

    /**
     * 暂停下载
     */
    void onPause();

    /**
     * 取消下载
     */
    void onCancel();
}


