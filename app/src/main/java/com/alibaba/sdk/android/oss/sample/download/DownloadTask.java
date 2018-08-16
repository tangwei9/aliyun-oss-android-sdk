package com.alibaba.sdk.android.oss.sample.download;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 下载任务类
 */
public class DownloadTask extends Handler {

    private final int THREAD_COUNT = 4;//线程数
    private CheckPointInfo mCheckPointInfo;
    private ObjectStat mObjectStat;
    private long mFileLength;


    private boolean isDownloading = false;
    private int childCanleCount;//子线程取消数量
    private int childPauseCount;//子线程暂停数量
    private int childFinshCount;
    private long[] mProgress;
    private File[] mCacheFiles;
    private File mTmpFile;//临时占位文件

    private boolean pause;//是否暂停
    private boolean cancel;//是否取消下载

    private final static int MSG_PROGRESS = 1;
    private final static int MSG_FINISHED = 2;
    private final static int MSG_PAUSE = 3;
    private final static int MSG_CANCEL = 4;

    private DownloadListener mListener; // 下载监听回调
    private static final String TAG = "DownloadTask";

    DownloadTask(CheckPointInfo checkPointInfo, DownloadListener listener) {
        this.mCheckPointInfo = checkPointInfo;
        this.mListener = listener;
        this.mProgress = new long[THREAD_COUNT];
        this.mCacheFiles = new File[THREAD_COUNT];
        this.mObjectStat = new ObjectStat();
        mObjectStat.setUrl(checkPointInfo.getUrl());
    }

    public synchronized void start() {
        try {
            Log.e(TAG, "start: " + isDownloading + "\t" + mCheckPointInfo.getUrl());
            if (isDownloading) return;
            isDownloading = true;

            // 获取资源信息
            Map<String, String> header = new HashMap<>();
            if (mObjectStat.getEtag() != null) {
                header.put("If-Match", mObjectStat.getEtag());
            }

            DownloadNetwork.sharedNetwork().headObject(mCheckPointInfo.getUrl(), header, new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() != 200) {
                        close(response.body());
                        resetStatus();
                        return;
                    }

                    // 获取资源大小
                    mFileLength = Long.parseLong(response.header("Content-Length"));
                    close(response.body());

                    String objectETag = response.header("ETag");
                    String lastModified = response.header("Last-Modified");

                    mObjectStat.setEtag(objectETag);
                    mObjectStat.setLastModified(lastModified);
                    mObjectStat.setContentLength(mFileLength);

                    // 在本地创建一个与资源同样大小的文件来占位
                    mTmpFile = new File(mCheckPointInfo.getFilePath(), mCheckPointInfo.getFileName() + ".tmp");
                    if (!mTmpFile.getParentFile().exists()) mTmpFile.getParentFile().mkdirs();
                    RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                    tmpAccessFile.setLength(mFileLength);
                    /** 将下载任务分配给每个线程 */
                    long chunkSize = mFileLength / THREAD_COUNT;    // 计算每个线程理论上下载的数量

                    /** 为每个线程配置并分配任务 */
                    for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                        long startIndex = threadId * chunkSize;     // 线程开始下载的位置
                        long endIndex = (threadId + 1) * chunkSize - 1; // 线程结束下载的位置
                        if (threadId == (THREAD_COUNT - 1)) {   // 如果是最后一个线程,将剩下的内容全部交给这个线程完成
                            endIndex = mFileLength - 1;
                        }
                        download(startIndex, endIndex, threadId);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            resetStatus();
        }
    }

    private void download(final long startIndex, final long endIndex, final int threadId) throws IOException {
        long newStartIndex = startIndex;
        // 分段请求网络连接,分段将文件保存到本地.
        // 加载下载位置缓存文件
        final File cacheFile = new File(mCheckPointInfo.getFilePath(), "thread" + threadId + "_" + mCheckPointInfo.getFileName() + ".cache");
        mCacheFiles[threadId] = cacheFile;
        final RandomAccessFile cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");
        if (cacheFile.exists()) {
            String startIndexStr = cacheAccessFile.readLine();
            try {
                newStartIndex = Integer.parseInt(startIndexStr);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        final long finalStartIndex = newStartIndex;
        Map<String , String > headers = new HashMap<>();
        headers.put("Range","bytes=" + newStartIndex + "-" + endIndex);
        if (mObjectStat.getEtag() != null) {
            headers.put("If-Match", mObjectStat.getEtag());
        }

        DownloadNetwork.sharedNetwork().downloadFileByRange(mCheckPointInfo.getUrl(),headers, new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                isDownloading = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() != 206) {
                    resetStatus();
                    return;
                }

                InputStream is = response.body().byteStream();
                RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                tmpAccessFile.seek(finalStartIndex);

                /** 将网络流中的数据写到本地文件 */
                byte[] buffer = new byte[1024 << 2];
                int length = -1;
                int total = 0; //记录本次下载文件的大小
                long progress = 0;
                while ((length = is.read(buffer)) > 0) {
                    if (cancel) {
                        // 关闭资源
                        close(cacheAccessFile,is,response.body());
                        cleanFile(cacheFile);
                        sendEmptyMessage(MSG_CANCEL);
                        return;
                    }

                    if (pause) {
                        close(cacheAccessFile, is, response.body());
                        sendEmptyMessage(MSG_PAUSE);
                        return;
                    }

                    tmpAccessFile.write(buffer, 0, length);
                    total += length;
                    progress = finalStartIndex + total;

                    // 将当前的位置保存到文件中
                    cacheAccessFile.seek(0);
                    cacheAccessFile.write((progress + "").getBytes("UTF-8"));

                    // 发送进度消息
                    mProgress[threadId] = progress - startIndex;
                    sendEmptyMessage(MSG_PROGRESS);
                }
                // 关闭资源
                close(cacheAccessFile, is, response.body());
                // 删除临时文件
                cleanFile(cacheFile);
                // 发送完成消息
                sendEmptyMessage(MSG_FINISHED);
             }
        });
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case MSG_PROGRESS://进度
                long progress = 0;
                for (int i = 0, length = mProgress.length; i < length; i++) {
                    progress += mProgress[i];
                }
                mListener.onProgress(progress * 1.0f / mFileLength);
                break;
            case MSG_PAUSE://暂停
                childPauseCount++;
                if (childPauseCount % THREAD_COUNT != 0) return;
                resetStatus();
                mListener.onPause();
                break;
            case MSG_FINISHED://完成
                childFinshCount++;
                if (childFinshCount % THREAD_COUNT != 0) return;
                mTmpFile.renameTo(new File(mCheckPointInfo.getFilePath(), mCheckPointInfo.getFileName()));//下载完毕后，重命名目标文件名
                resetStatus();
                mListener.onFinished();
                break;
            case MSG_CANCEL://取消
                childCanleCount++;
                if (childCanleCount % THREAD_COUNT != 0) return;
                resetStatus();
                mProgress = new long[THREAD_COUNT];
                mListener.onCancel();
                break;
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        pause = true;
    }

    /**
     * 取消
     */
    public void cancel() {
        cancel = true;
        cleanFile(mTmpFile);
        if (!isDownloading) {
            if (null != mListener) {
                cleanFile(mCacheFiles);
                resetStatus();
                mListener.onCancel();
            }
        }
    }

    /**
     * 关闭资源
     * @param closeables
     */
    private void close(Closeable... closeables) {
        int length = closeables.length;
        try {
            for (int i = 0; i < length; i++) {
                Closeable closeable = closeables[i];
                if (null != closeable)
                    closeables[i].close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < length; i++) {
                closeables[i] = null;
            }
        }
    }

    /**
     * 删除临时文件
     * @param files
     */
    private void cleanFile(File... files) {
        for (int i = 0, length = files.length; i < length; i++) {
            if (null != files[i] && files[i].exists()) {
                files[i].delete();
            }
        }
    }


    private void resetStatus() {
        pause = false;
        cancel = false;
        isDownloading = false;
    }


    public boolean isDownloading() {
        return isDownloading;
    }
}
