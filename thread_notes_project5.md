# Java Threading — Project 5 Notes
## The Real File Downloader: HTTP, ANSI Progress Bars & ThreadLocal

---

## Index
- [1. HttpURLConnection — Real Downloads](#1-httpurlconnection--real-downloads)
- [2. Chunked Reading — Why and How](#2-chunked-reading--why-and-how)
- [3. ANSI Escape Codes — Terminal Control](#3-ansi-escape-codes--terminal-control)
- [4. Singleton Pattern — ProgressRenderer](#4-singleton-pattern--progressrenderer)
- [5. ThreadLocal — Use and Removal](#5-threadlocal--use-and-removal)
- [6. Retry Mechanism](#6-retry-mechanism)
- [7. try-with-resources — Resource Safety](#7-try-with-resources--resource-safety)
- [8. CS Connections — OS, COA, Memory](#8-cs-connections--os-coa-memory)
- [9. Project Code](#9-project-code)
- [10. Mistakes](#10-mistakes)
- [11. Summary](#11-summary)

---

## 1. HttpURLConnection — Real Downloads

```java
URL url = new URL(fileUrl);
HttpURLConnection connection = (HttpURLConnection) url.openConnection();
connection.setRequestMethod("GET");
connection.setRequestProperty("User-Agent", "Mozilla/5.0");
connection.setRequestProperty("Accept", "*/*");
connection.connect();
```

### Key methods

```
connection.getContentLength()  → total file size in bytes (-1 if unknown)
connection.getInputStream()    → byte stream of response body
connection.getResponseCode()   → HTTP status (200 OK, 404, 503 etc.)
```

### Content-Length header

`getContentLength()` reads the HTTP `Content-Length` response header.  
Returns `-1` if server doesn't send it (chunked transfer encoding, streaming).  
Must throw exception if `-1` — cannot show progress without knowing total size.

```java
int contentLength = connection.getContentLength();
if (contentLength == -1) {
    throw new RuntimeException("Unknown file size — cannot track progress");
}
```

### SSL issues

HTTPS connections may fail with `SSLHandshakeException` if:
- Server certificate is self-signed
- JVM trust store doesn't have the CA
- Server requires SNI

Fix: Add `User-Agent` header. Many servers reject connections without it.  
Alternative: Use HTTP for testing, HTTPS for production with proper certs.

---

## 2. Chunked Reading — Why and How

### Why not read entire file at once?

```
100MB file → 100MB heap per thread
3 threads  → 300MB heap just for download buffers
10 threads → 1GB heap

Result: OutOfMemoryError for large files

Chunked reading → 8KB buffer regardless of file size
Constant memory usage no matter how large the file
```

### Why chunked for progress tracking?

```
Read entire file:  0% ──────────────────────────── 100%  (spike, no gradual progress)
Chunked reading:   0% ──── 25% ──── 50% ──── 100%  (smooth, accurate)
```

### The pattern

```java
byte[] buffer = new byte[8192];  // 8KB buffer
int bytesRead;
int totalRead = 0;
int lastPercent = -1;

while ((bytesRead = in.read(buffer)) != -1) {
    out.write(buffer, 0, bytesRead);   // write exactly bytesRead bytes
    totalRead += bytesRead;

    int percent = (int)((totalRead * 100L) / contentLength);

    if (percent != lastPercent) {      // only render on change
        renderer.render(fileName, percent, totalLines, lineNumber);
        lastPercent = percent;
    }
}
```

### Why `100L` not `100`?

```java
(totalRead * 100L)  // cast to long to prevent integer overflow
// totalRead can reach 100,000,000 for 100MB file
// 100,000,000 * 100 = 10,000,000,000 → overflows int (max ~2.1 billion)
// Using 100L forces long arithmetic
```

---

## 3. ANSI Escape Codes — Terminal Control

ANSI escape codes are special character sequences that control terminal behavior — cursor movement, color, clearing lines.

```
\033[2J\033[H        → clear entire screen, cursor to row 1
\033[s               → save current cursor position
\033[u               → restore saved cursor position
\033[{n}A            → move cursor UP n lines
\033[{n}B            → move cursor DOWN n lines
\033[2K              → clear current line
\r                   → move to start of current line
\033[{row};{col}H    → move cursor to absolute row, col
```

### Progress bar rendering — absolute positioning

```java
public synchronized void render(String fileName, int percent, int totalLines, int lineNumber) {
    int targetRow = startRow + lineNumber - 1;
    
    String ansi = "\033[" + targetRow + ";1H"  // absolute position
                + "\033[2K"                     // clear line
                + buildBar(fileName, percent)   // draw bar
                + "\033[" + (startRow + totalLines) + ";1H"; // cursor to bottom
    
    System.out.print(ansi);
    System.out.flush();
}
```

### Why ANSI didn't work in IntelliJ / Maven exec

```
IntelliJ console    → not a real TTY, strips ANSI codes
mvn exec:java       → pipes stdout through Maven logger, strips ANSI
java -jar           → direct TTY connection, ANSI works correctly

Rule: Always test terminal UI with java -jar in a real terminal
```

### Building the progress bar string

```java
public String buildBar(String fileName, int percent) {
    int barWidth = 50;
    int filled = (percent * barWidth) / 100;
    int empty = barWidth - filled;

    StringBuilder bar = new StringBuilder();
    bar.append(String.format("%-20s ", fileName));  // left-aligned, 20 chars
    bar.append("[");
    for (int i = 0; i < filled; i++) bar.append("#");
    for (int i = 0; i < empty; i++) bar.append(" ");
    bar.append("] ");
    bar.append(String.format("%3d%%", percent));
    return bar.toString();
}
```

---

## 4. Singleton Pattern — ProgressRenderer

### Why Singleton?

`System.out` is a shared resource. Multiple threads writing simultaneously = interleaved, garbled output.

```
Thread 1 writing: "temp_file1  [####"
Thread 2 writing: "temp_file2  [##"
→ Terminal sees: "temp_file1  [####temp_file2  [##"  ← garbage
```

One `ProgressRenderer` instance with a `synchronized` render method = only one thread prints at a time.

### Implementation — Eager Singleton

```java
public class ProgressRenderer {
    // Created at class load time — before any thread can access it
    private static final ProgressRenderer INSTANCE = new ProgressRenderer();

    private ProgressRenderer() { }  // private constructor — no external instantiation

    public static ProgressRenderer getInstance() {
        return INSTANCE;
    }

    public synchronized void render(...) { }  // one thread at a time
}
```

### Eager vs Lazy Singleton

```
Eager  → instance created at class load time
         always thread safe
         slight memory cost even if never used

Lazy   → instance created on first call
         NOT thread safe without double-checked locking
         two threads can both see null and both create instances

// BROKEN lazy singleton
if (instance == null) {          // Thread 1 checks — null
    // CONTEXT SWITCH
    // Thread 2 checks — still null
    instance = new ProgressRenderer(); // both create instances
}
```

---

## 5. ThreadLocal — Use and Removal

### Initial design — ThreadLocal for line assignment

```java
ThreadLocal<Integer> lineNumber = new ThreadLocal<>();
AtomicInteger lineCounter = new AtomicInteger(0);

// First call from a thread → assign line number
if (lineNumber.get() == null) {
    lineNumber.set(lineCounter.incrementAndGet());
}
```

Each thread (pool-1-thread-1, pool-1-thread-2, pool-1-thread-3) gets its own line.  
Thread reuses same line when it picks up the next file.

**Problem:** 3 threads = 3 bars max. New file replaces old file on same line. Can't see all downloads.

### Removal — fixed line per file

```java
// Line number assigned at task creation time, not at thread execution time
FileDownloadTask task = new FileDownloadTask(url, fileName, config, totalLines, i + 1);
```

Each file has its own permanent line. 9 files = 9 bars. ThreadLocal no longer needed.

### ThreadLocal — critical rule for thread pools

```
Thread pool reuses threads.
Thread-1 handles request A → stores data in ThreadLocal
Request A finishes → thread returns to pool
Thread-1 picks up request B → ThreadLocal still has request A's data
Request B sees request A's data → DATA LEAK / SECURITY BUG

Rule: ALWAYS call threadLocal.remove() when done
      Especially critical in web servers and thread pools
```

---

## 6. Retry Mechanism

```java
int maxRetries = 3;
for (int attempt = 1; attempt <= maxRetries; attempt++) {
    try {
        downloadFile(fileName);
        result.setSuccess(true);
        result.setMessage("Success on attempt " + attempt);
        break;  // exit loop on success
    } catch (Exception e) {
        if (attempt == maxRetries) {
            result.setSuccess(false);
            result.setMessage("Failed after " + maxRetries + " attempts");
        } else {
            System.out.println("Attempt " + attempt + " failed, retrying...");
        }
    }
}
```

### Key design decisions

```
break on success    → don't retry if it worked
attempt == maxRetries → only mark failed on last attempt
Exception catch     → catches any failure (network, SSL, IO)
```

---

## 7. try-with-resources — Resource Safety

```java
// WRONG — stream not closed if exception thrown mid-download
InputStream in = connection.getInputStream();
FileOutputStream out = new FileOutputStream(outputPath);
// exception here → streams never closed → resource leak

// CORRECT — both streams closed automatically even on exception
try (
    InputStream in = connection.getInputStream();
    FileOutputStream out = new FileOutputStream(outputPath)
) {
    // read and write
}
```

`try-with-resources` calls `close()` on every resource in the header, in reverse order, even if an exception is thrown. Equivalent to a `finally` block but cleaner and safer.

---

## 8. CS Connections — OS, COA, Memory

| Java Concept | Maps To | Detail |
|---|---|---|
| HttpURLConnection | OS: Socket I/O | TCP connection to remote server |
| InputStream.read(buffer) | OS: read() syscall | Kernel copies bytes from socket buffer to user buffer |
| 8KB chunk size | Memory: Buffer sizing | Matches typical OS page size (4KB-8KB) for efficiency |
| getContentLength() | HTTP: Content-Length header | Server announces total response size upfront |
| ThreadLocal | Memory: Thread-private storage | Each thread has isolated memory region, no sharing |
| Singleton + synchronized | OS: Mutex on shared resource | One thread at a time accesses terminal output |
| ANSI codes | OS: Terminal control sequences | VT100 standard, interpreted by terminal emulator |
| Retry mechanism | OS: Fault tolerance | Transient network failures recovered without user intervention |
| chunked reading progress | COA: Pipeline | Process data in stages rather than all at once |

---

## 9. Project Code

### Key classes summary

```
Main.java              → setup, ExecutorService, task submission, Future collection
FileDownloadTask.java  → Callable, HTTP download, chunked read, retry, progress calls
ProgressRenderer.java  → Singleton, synchronized render, ANSI bar building
DownloadConfig.java    → reads urls.txt, holds threadPoolSize, outputDir
DownloadResult.java    → model: fileName, url, success, message, timeTakenMs
```

### Core download loop

```java
try (
    InputStream in = connection.getInputStream();
    FileOutputStream out = new FileOutputStream(config.getOutputDir() + outputFile)
) {
    byte[] buffer = new byte[8192];
    int bytesRead, totalRead = 0, lastPercent = -1;

    while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
        totalRead += bytesRead;

        int percent = (int)((totalRead * 100L) / contentLength);
        if (percent != lastPercent) {
            ProgressRenderer.getInstance().render(fileName, percent, totalLines, lineNumber);
            lastPercent = percent;
        }
    }
}
```

---

## 10. Mistakes

### Mistake 1 — FileOutputStream not in try-with-resources

```java
// WRONG — leaked if exception thrown
InputStream in = connection.getInputStream();
FileOutputStream out = new FileOutputStream(path);

// CORRECT
try (InputStream in = ...; FileOutputStream out = ...) { }
```

### Mistake 2 — Setting success=true after try-catch

```java
// WRONG — always sets true even if catch ran
try {
    downloadFile();
} catch (Exception e) {
    result.setSuccess(false);
}
result.setSuccess(true);  // BUG — overwrites the false

// CORRECT — set inside try only
try {
    downloadFile();
    result.setSuccess(true);  // only reached if no exception
} catch (Exception e) {
    result.setSuccess(false);
}
```

### Mistake 3 — Integer overflow in percent calculation

```java
// WRONG — overflows for files > ~21MB
int percent = (totalRead * 100) / contentLength;

// CORRECT — 100L forces long arithmetic
int percent = (int)((totalRead * 100L) / contentLength);
```

### Mistake 4 — Wrong order: clear screen after reserving lines

```java
// WRONG — clear wipes reserved lines
for (int i = 0; i < totalLines; i++) System.out.println();
System.out.print("\033[2J\033[H");  // clears everything

// CORRECT — clear first, then reserve
System.out.print("\033[2J\033[H");
for (int i = 0; i < totalLines; i++) System.out.println();
System.out.print("\033[" + totalLines + "A");
System.out.print("\033[s");
```

### Mistake 5 — Testing ANSI in IntelliJ / Maven exec

```
WRONG:  mvn exec:java  → Maven strips ANSI codes
WRONG:  IntelliJ console → not a real TTY

CORRECT: java -jar target/file-downloader.jar  → real terminal, ANSI works
```

### Mistake 6 — ThreadLocal without remove() in thread pool

```java
// WRONG — previous thread's data leaks to next task
threadLocal.set(value);
// task finishes, thread returns to pool
// next task gets old value

// CORRECT
try {
    threadLocal.set(value);
    // do work
} finally {
    threadLocal.remove();  // always clean up
}
```

---

## 11. Summary

Project 5 brought everything together — real I/O, terminal UI, design patterns, and concurrency all working simultaneously.

The core insight of chunked reading: you never load more than 8KB into memory regardless of file size. This is how real download managers work. The `Content-Length` header gives you the denominator for progress calculation — without it, you can't show meaningful progress.

The Singleton + synchronized `ProgressRenderer` solved the shared terminal problem. Multiple threads competing to write to `System.out` without coordination produces garbage. One gatekeeper with a synchronized method ensures clean, ordered output.

ThreadLocal taught an important design lesson — it was the right tool for one design (thread-owns-line), but the wrong tool when requirements changed (file-owns-line). Knowing when to remove an abstraction is as important as knowing when to add one.

ANSI escape codes are powerful but environment-dependent. `mvn exec:java` and IntelliJ consoles strip them. Always test terminal UI code in a real TTY with `java -jar`.

**Golden rules from this project:**
- Always use `try-with-resources` for streams — never rely on manual `close()`
- Use `100L` not `100` in progress calculations — prevent integer overflow
- Test ANSI code output with `java -jar` in real terminal only
- `ThreadLocal.remove()` is mandatory in thread pools — data leaks are security bugs
- Singleton is for shared resources — one printer, one logger, one config
- Clear screen before reserving lines — not after
- Chunked reading = constant memory + accurate progress

**Threading journey complete:**
Project 1 → Thread basics, lifecycle, scheduling
Project 2 → Race conditions, synchronized, locks
Project 3 → wait()/notify(), Producer-Consumer
Project 4 → ExecutorService, Callable, Future
Project 5 → Real I/O, ANSI UI, Singleton, ThreadLocal, Retry
