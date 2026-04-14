package com.premchoithani.progress;

import com.premchoithani.config.DownloadConfig;



public class ProgressRenderer {

    private static final ProgressRenderer INSTANCE  = new ProgressRenderer();

    private DownloadConfig config = new DownloadConfig();

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


    public synchronized void render(String fileName, int percent, int totalLines , int lineNumber) {

        String ansi = "\033[u"           // restore to saved position (top of area)
                + "\033[" + (lineNumber - 1) + "B"  // move down to my line
                + "\033[2K\r"      // clear and go to line start
                + buildBar(fileName , percent)
                + "\033[u"         // restore again to keep cursor stable
                + "\033[" + totalLines + "B" ; // move to bottom (below all bars) // go back to bottom

        System.out.print(ansi);
        System.out.flush();

    }

    public synchronized void complete(String fileName, int totalLines , int lineNumber) {


        String ansi = "\033[u"                 // restore to saved position (top)
                + "\033[" + (lineNumber - 1) + "B" // move down to my line
                + "\033[2K\r"                 // clear line + carriage return
                + buildBar(fileName, 100) + " ✔"
                + "\033[u"                   // restore again
                + "\033[" + totalLines + "B"; // move cursor back to bottom

        System.out.print(ansi);
        System.out.flush();

    }

}
