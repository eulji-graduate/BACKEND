package com.module06.backend.cap.infrastructure.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.module06.backend.cap.application.port.out.RecordingMediaAssemblyPort;

@Component
@Profile("local")
public class LocalFfmpegRecordingAssembler implements RecordingMediaAssemblyPort {
    private final String ffmpeg;
    private final String ffprobe;
    private final long timeoutSeconds;
    public LocalFfmpegRecordingAssembler(
            @Value("${app.local-assembly.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${app.local-assembly.ffprobe:ffprobe}") String ffprobe,
            @Value("${app.local-assembly.timeout-seconds:600}") long timeoutSeconds) {
        if (ffmpeg.isBlank() || ffprobe.isBlank() || timeoutSeconds <= 0) throw new IllegalArgumentException("Invalid local media tool configuration");
        this.ffmpeg=executable(ffmpeg); this.ffprobe=executable(ffprobe); this.timeoutSeconds=timeoutSeconds;
    }

    private String executable(String value) {
        return value.contains("/") ? Path.of(value).toAbsolutePath().normalize().toString() : value;
    }

    @Override
    public Result assemble(Path work, List<SourcePart> parts) {
        try {
            List<StreamFile> streams = groupStreams(work,parts);
            if (streams.isEmpty()) throw new IllegalStateException("No audio chunks to assemble");
            List<String> concat = new ArrayList<>();
            for (int index=0;index<streams.size();index++) {
                StreamFile stream=streams.get(index);
                String name="normalized-"+index+".wav";
                run(work,List.of(ffmpeg,"-nostdin","-hide_banner","-loglevel","error","-y",
                        "-protocol_whitelist","file,pipe","-f",stream.format(),"-i",stream.file().toString(),
                        "-map","0:a:0","-ar","48000","-ac","1","-c:a","pcm_s16le",work.resolve(name).toString()),timeoutSeconds);
                concat.add("file '"+name+"'");
            }
            Path listing=work.resolve("concat.txt");
            Files.write(listing,concat);
            Path output=work.resolve("recording.ogg");
            run(work,List.of(ffmpeg,"-nostdin","-hide_banner","-loglevel","error","-y",
                    "-protocol_whitelist","file,pipe","-f","concat","-safe","1","-i",listing.toString(),
                    "-c:a","libvorbis","-q:a","5",output.toString()),timeoutSeconds);
            String probe=run(work,List.of(ffprobe,"-v","error","-f","ogg","-show_entries","format=duration",
                    "-of","default=noprint_wrappers=1:nokey=1",output.toString()),Math.min(timeoutSeconds,30));
            double seconds=Double.parseDouble(probe.trim());
            if (!Double.isFinite(seconds) || seconds<=0 || seconds>Integer.MAX_VALUE || Files.size(output)<=0) {
                throw new IllegalStateException("Invalid assembled recording duration");
            }
            return new Result(output,(int)Math.ceil(seconds));
        } catch (IOException error) { throw new IllegalStateException("Local audio assembly failed",error); }
    }

    private List<StreamFile> groupStreams(Path work,List<SourcePart> parts) throws IOException {
        List<StreamFile> streams=new ArrayList<>();
        OutputStream current=null;
        int segment=-1;
        String format=null;
        try {
            for (SourcePart part:parts.stream().sorted(Comparator.comparingInt(SourcePart::segment)
                    .thenComparingInt(SourcePart::seq)).toList()) {
                String nextFormat=format(part.contentType());
                byte[] header;
                try (InputStream input=Files.newInputStream(part.file())) { header=input.readNBytes(12); }
                boolean newHeader=header.length>=4 && header[0]==0x1a && header[1]==0x45
                        && header[2]==(byte)0xdf && header[3]==(byte)0xa3;
                boolean ftyp=header.length>=8 && header[4]=='f' && header[5]=='t' && header[6]=='y' && header[7]=='p';
                if (current==null || part.segment()!=segment || !nextFormat.equals(format) || newHeader || ftyp) {
                    if (current!=null) current.close();
                    Path file=work.resolve("stream-"+streams.size()+".media");
                    streams.add(new StreamFile(file,nextFormat));
                    current=Files.newOutputStream(file);
                    segment=part.segment(); format=nextFormat;
                }
                try (InputStream input=Files.newInputStream(part.file())) { input.transferTo(current); }
            }
        } finally { if (current!=null) current.close(); }
        return streams;
    }

    private String format(String type) {
        if (type!=null && (type.startsWith("audio/webm") || type.startsWith("video/webm"))) return "matroska";
        if (type!=null && (type.startsWith("audio/mp4") || type.startsWith("video/mp4"))) return "mov";
        throw new IllegalArgumentException("Unsupported recording chunk type");
    }

    protected String run(Path work,List<String> command,long timeout) {
        Path stdout=work.resolve("process-"+UUID.randomUUID()+".out");
        Path stderr=work.resolve("process-"+UUID.randomUUID()+".err");
        Process process=null;
        try {
            process=new ProcessBuilder(command).directory(work.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            if (!process.waitFor(timeout,TimeUnit.SECONDS)) throw new IllegalStateException("Local media process timed out");
            if (process.exitValue()!=0) {
                String detail;
                try (var lines=Files.lines(stderr)) { detail=String.join("\n",lines.limit(20).toList()); }
                throw new IllegalStateException("Local media process failed: "+detail);
            }
            return Files.readString(stdout);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot execute local media tool; check ffmpeg/ffprobe paths",error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local media process interrupted",error);
        } finally {
            if (process!=null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
    private record StreamFile(Path file,String format) { }
}
