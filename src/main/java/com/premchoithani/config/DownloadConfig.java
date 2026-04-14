package com.premchoithani.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DownloadConfig{

    private int threadPoolSize = 3;
    private String inputFilePath = "input/urls.txt";
    private String outputDir  = "downloads/";

    public DownloadConfig(int threadPoolSize , String inputFilePath) {
        this.threadPoolSize = threadPoolSize;
        this.inputFilePath = inputFilePath;
    }
    public DownloadConfig()  {
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }


    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }


    public List<String> readInputUrls() throws IOException {

        List<String> urls = new ArrayList<>();

        try(BufferedReader br = new BufferedReader(new FileReader(inputFilePath)))
        {
            br.lines().forEach(urls :: add);
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
        return urls;
    }

    public int getTotalFiles() throws IOException {
        return readInputUrls().size();
    }


    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }
}
