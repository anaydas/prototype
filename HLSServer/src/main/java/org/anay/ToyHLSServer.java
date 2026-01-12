package org.anay;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ToyHLSServer {

    private static final String OUTPUT_DIR = "src/main/resources/hls_output";
    private static final String INPUT_FILE = "src/main/resources/input/BigBuckBunny.mp4"; // Change this to your video path

    public static void main(String[] args) throws Exception {
        // 1. Setup output directory
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) dir.mkdir();

        // 2. Start Transcoding first
        System.out.println("Starting Transcoding...");
        Process ffmpegProcess = startTranscoding(INPUT_FILE, OUTPUT_DIR);

        // Add a shutdown hook to kill FFmpeg if Java is closed
        Runtime.getRuntime().addShutdownHook(new Thread(ffmpegProcess::destroy));

        System.out.println("Waiting for FFmpeg to generate the playlist...");
        File playlist = new File(OUTPUT_DIR + "/master.m3u8");
        while (!playlist.exists()) {
            Thread.sleep(500); // Check every half second
        }

        System.out.println("Playlist found! Starting Web Server...");

        // 3. Start the Web Server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/stream", exchange -> {
            String fileName = exchange.getRequestURI().getPath().replace("/stream/", "");
            Path file = Paths.get(OUTPUT_DIR, fileName);

            if (Files.exists(file)) {
                // Set Headers for HLS and CORS
                String contentType = "application/octet-stream";
                if (fileName.endsWith(".m3u8")) contentType = "application/x-mpegURL";
                if (fileName.endsWith(".ts")) contentType = "video/MP2T";

                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

                byte[] bytes = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });

        server.start();
        System.out.println("HLS Server is live!");
        System.out.println("Playlist URL: http://localhost:8080/stream/master.m3u8");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static Process startTranscoding(String inputPath, String outputDir) throws IOException {
        // Point to the local file in your project root
        String ffmpegBinary = "./ffmpeg";

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegBinary, "-re", "-i", inputPath,
                "-codec:v", "libx264", "-profile:v", "baseline",
                "-codec:a", "aac", "-ar", "44100", "-ac", "2",
                "-f", "hls",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-hls_segment_filename", outputDir + "/seg_%03d.ts",
                outputDir + "/master.m3u8"
        );

        return pb.inheritIO().start();
    }
}
