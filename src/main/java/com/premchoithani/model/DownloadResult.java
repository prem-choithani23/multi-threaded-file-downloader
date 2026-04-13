package com.premchoithani.model;

public class DownloadResult {

    private String fileName;
    private String url;
    private long timeTakenMs;
    private String message;
    private boolean success;

    public DownloadResult() {
    }

    public DownloadResult(String fileName, String url, long timeTakenMs, String message, boolean success) {
        this.fileName = fileName;
        this.url = url;
        this.timeTakenMs = timeTakenMs;
        this.message = message;
        this.success = success;
    }

    @Override
    public String toString() {
        return "DownloadResult{" +
                "fileName='" + fileName + '\'' +
                ", url='" + url + '\'' +
                ", timeTakenMs=" + timeTakenMs +
                ", message='" + message + '\'' +
                ", success=" + success +
                '}';
    }


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

    public long getTimeTakenMs() {
        return timeTakenMs;
    }

    public void setTimeTakenMs(long timeTakenMs) {
        this.timeTakenMs = timeTakenMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
