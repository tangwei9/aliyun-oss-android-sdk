package com.alibaba.sdk.android.oss.sample.download;

import android.os.Environment;
import android.text.TextUtils;

import com.alibaba.sdk.android.oss.ClientException;

import java.io.File;
import java.security.InvalidParameterException;
import java.security.spec.InvalidParameterSpecException;
import java.util.HashMap;
import java.util.Map;

/**
 * 下载任务管理器
 */
public class DownloadManager {
    private static final String DEFAULT_DOWNLOAD_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "oss-download" + File.separator;
    private Map<String, DownloadTask>  mTasks;
    private static DownloadManager mInstance;
    private static final String TAG = "DownloadManager";

    private String getFileNameFromURL(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private DownloadManager() {
        mTasks = new HashMap<>();
    }

    public static DownloadManager sharedManager() {//管理器初始化
        if (mInstance == null) {
            synchronized (DownloadManager.class) {
                if (mInstance == null) {
                    mInstance = new DownloadManager();
                }
            }
        }
        return mInstance;
    }

    public void addTask(String url, DownloadListener listener) {
        addTask(url, null, null, listener);
    }

    public void addTask(String url, String filePath, DownloadListener listener) {
        addTask(url, filePath, null, listener);
    }

    public void addTask(String url, String filePath, String fileName, DownloadListener listener) throws InvalidParameterException {
        if (TextUtils.isEmpty(url)) {
            throw new InvalidParameterException("url should not be empty!");
        }

        if (TextUtils.isEmpty(filePath)) {
            filePath = DEFAULT_DOWNLOAD_DIR;
        }

        if (TextUtils.isEmpty(fileName)) {
            fileName = getFileNameFromURL(url);
        }

        mTasks.put(url, new DownloadTask(new CheckPointInfo(fileName, url, filePath), listener));
    }

    /**
     * 批量暂停
     * @param urls
     */
    public void pause(String... urls) {
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mTasks.containsKey(url)) {
                mTasks.get(url).pause();
            }
        }
    }

    /**
     * 批量取消
     * @param urls
     */
    public void cancel(String... urls) {
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mTasks.containsKey(url)) {
                mTasks.get(url).cancel();
            }
        }
    }

    /**
     * 批量下载
     * @param urls
     */
    public void download(String... urls) {
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mTasks.containsKey(url)) {
                mTasks.get(url).start();
            }
        }
    }

    public boolean isDownloading(String url) {
        boolean result = false;
        if (mTasks.containsKey(url)) {
            result = mTasks.get(url).isDownloading();
        }
        return result;
    }
}
