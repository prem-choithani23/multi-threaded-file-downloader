package com.premchoithani.progress;

import com.premchoithani.config.DownloadConfig;

import java.util.concurrent.atomic.AtomicInteger;

public class ProgressRenderer {

    private static final ProgressRenderer INSTANCE  = new ProgressRenderer();


    ThreadLocal<Integer> lineNumber = new ThreadLocal<>();
    AtomicInteger lineCounter = new AtomicInteger(0);

    private ProgressRenderer(){
    }

    public static ProgressRenderer getInstance(){
        return INSTANCE;
    }

    public String buildBar(String fileName, int percent) {
        int barWidth = 50; // total width of bar

        int filled = (percent * barWidth) / 100;
        int empty = barWidth - filled;

        StringBuilder bar = new StringBuilder();

        bar.append(String.format("%-20s ", fileName)); // left-aligned name

        bar.append("[");
        for (int i = 0; i < filled; i++) {
            bar.append("#"); // filled
        }
        for (int i = 0; i < empty; i++) {
            bar.append(" "); // empty
        }
        bar.append("] ");

        bar.append(String.format("%3d%%", percent));

        return bar.toString();
    }


    public synchronized void render(String fileName , int percent , int totalLines){
        if(lineNumber.get() == null){
            lineNumber.set(lineCounter.incrementAndGet());
        }

        int myLine = lineNumber.get();

        String ansi = "\033[s"           // save cursor
                + "\033[" + ( totalLines - myLine) + "A"  // move up
                + "\033[2K"                  // clear line
                + "\r"                       // go to line start
                + buildBar(fileName, percent) // your progress bar string
                + "\033[u";                  // restore cursor
        System.out.print(ansi);
    }

}
