package com.alibaba.sdk.android.oss.sample.download;


import android.util.Range;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class DownloadNetwork {
    private OkHttpClient mOkHttpClient;
    private static DownloadNetwork mInstance;
    private final static int CONNECT_TIMEOUT = 60;//超时时间，秒
    private final static int READ_TIMEOUT = 60;//读取时间，秒
    private final static int WRITE_TIMEOUT = 60;//写入时间，秒

    /**
     * @return DownloadNetwork实例对象
     */
    public static DownloadNetwork sharedNetwork() {
        if (null == mInstance) {
            synchronized (DownloadNetwork.class) {
                if (null == mInstance) {
                    mInstance = new DownloadNetwork();
                }
            }
        }
        return mInstance;
    }

    /**
     * 按照Range分段下载
     * @param url
     * @param headers
     * @param callback
     * @throws IOException
     */
    public void downloadFileByRange(String url, Map<String, String> headers, Callback callback) throws IOException {
        // 创建一个Request
        // 设置分段下载的头信息。 Range:做分段数据请求,断点续传时需要指定下载的区间。
        Request.Builder builder = new Request.Builder().url(url).method("GET",null);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getValue() != null) {
                builder = builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Request request = builder.build();
        asyncRequest(request, callback);
    }

    /**
     * 首先请求object的信息,获取文件大小,Etag,Last-Modified
     * @param url
     * @param callback
     */
    public void headObject(String url, Map<String,String> headers, Callback callback) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).method("HEAD",null);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder = builder.addHeader(entry.getKey(), entry.getValue());
        }

        Request request = builder.build();
        asyncRequest(request, callback);
    }

    private void asyncRequest(Request request, Callback callback) throws IllegalStateException {
        Call call = mOkHttpClient.newCall(request);
        call.enqueue(callback);
    }

    /**
     * 构造方法,配置OkHttpClient
     */
    private DownloadNetwork() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS);
        mOkHttpClient = builder.build();
    }
}
