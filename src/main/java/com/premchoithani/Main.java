package com.premchoithani;

import com.premchoithani.config.DownloadConfig;
import com.premchoithani.downloader.FileDownloadTask;
import com.premchoithani.model.DownloadResult;

import java.io.IOException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {

        DownloadConfig downloadConfig = new DownloadConfig();
        ExecutorService executorService = Executors.newFixedThreadPool(downloadConfig.getThreadPoolSize());

        List<FileDownloadTask> tasks = new ArrayList<>();

        List<String> urls  = downloadConfig.readInputUrls();

        int totalLines = urls.size();

        System.out.print("\033[2J\033[H"); // clear screen first, cursor at row 1
        Thread.sleep(100);                  // let terminal process

// reserve lines AFTER clear
        for (int i = 0; i < totalLines; i++) {
            System.out.println();
        }
        System.out.print("\033[" + totalLines + "A"); // go back up
        System.out.print("\033[s");                   // save anchor

        String fileName = "temp_file";
        for(int i=0;i<urls.size();i++){
            String tempFileName = fileName + (i + 1);
            FileDownloadTask task  = new FileDownloadTask(urls.get(i), tempFileName , downloadConfig , totalLines ,i + 1);
            tasks.add(task);
        }

        List<Future<DownloadResult>> results = executorService.invokeAll(tasks);

        for(Future<DownloadResult> future : results){

            try{
                future.get(200 , TimeUnit.MILLISECONDS);
            }catch (ExecutionException e){
                System.out.println(e.getCause().getMessage());
            }catch (TimeoutException t){

            }
        }

        executorService.shutdown();
    }
}