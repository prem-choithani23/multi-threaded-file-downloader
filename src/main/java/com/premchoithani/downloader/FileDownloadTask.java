package com.premchoithani.downloader;

import com.premchoithani.config.DownloadConfig;
import com.premchoithani.model.DownloadResult;
import com.premchoithani.progress.ProgressRenderer;

import javax.net.ssl.HttpsURLConnection;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class FileDownloadTask implements Callable<DownloadResult> {

    DownloadConfig downloadConfig;
    String fileUrl;
    String fileName;
    private final int totalLines;
    private int lineNumber;

    public FileDownloadTask(String url, String name, DownloadConfig config, int totalLines ,int lineNumber) {
        this.fileUrl = url;
        this.fileName = name;
        this.downloadConfig = config;
        this.totalLines = totalLines;
        this.lineNumber = lineNumber;
    }



    @Override
    public DownloadResult call() throws Exception {

        Instant startTime = Instant.now();
        
        DownloadResult result = new DownloadResult();
        result.setUrl(fileUrl);
        result.setFileName(fileName);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                downloadFile(fileName);

                result.setSuccess(true);
                result.setMessage(String.format("File: %s , Status: success , Attempts : %d" ,fileName , attempt ));
                Duration difference = Duration.between(startTime ,Instant.now());

                result.setTimeTakenMs(difference.toMillis());

                break;
            } catch (Exception e) {

                if(attempt ==3){
                    System.out.println("All attempts faild..... : " + fileName);
                    result.setMessage("Failed to download file : " + fileName);
                    result.setSuccess(false);
                }else {
                    System.out.println("Attempt " + attempt + " failed: " + e.getClass().getName() + " — " + e.getMessage());
                }

            }
        }
        



        
        
        return result;
    }

    public void downloadFile(String outputFile) throws IOException {
        URL url = new URL(fileUrl);

        boolean success = false;

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();

        connection.setRequestMethod("GET");
        connection.connect();


        int connectionLength = connection.getContentLength();


        if(connectionLength == -1){
            
            throw new RuntimeException("Unknown file size...");
        }


        try (
                InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream(downloadConfig.getOutputDir() + outputFile)
        ) {

            byte[] buffer = new byte[8192];

            int bytesRead;
            int totalRead = 0;


            int lastPercent = -1;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                int percent = (int)((totalRead * 100L) / connectionLength);

                if (percent != lastPercent) {
                    ProgressRenderer renderer = ProgressRenderer.getInstance();
                    renderer.render(fileName, percent, totalLines , lineNumber);
                    lastPercent = percent;
                }
            }
            success = true;

        }catch (Exception e){
            throw new RuntimeException(e);
        }


        if(success){
            ProgressRenderer renderer = ProgressRenderer.getInstance();
            renderer.complete(fileName, downloadConfig.getTotalFiles() , lineNumber);
        }



    }
}
