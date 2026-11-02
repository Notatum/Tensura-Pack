package ar.juan.devouringorb.client;

import ar.juan.devouringorb.AudioRules;
import java.nio.file.*;
import java.util.*;

/** Standalone client-library test: lists and decodes local audio without opening the game. */
public final class OrbAudioRefreshVerification {
    public static void main(String[] args) throws Exception {
        Path input=Path.of(args[0]),game=Files.createTempDirectory("orb-audio-check-");
        AudioLibrary.prepare(game);
        List<Path> songs;
        try(var files=Files.list(input)) {songs=files.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".mp3")).toList();}
        int count=0;
        for(Path song:songs) {
            String name=song.getFileName().toString();Files.copy(song,game.resolve("orbe_audio/musica").resolve(name));
            if(!AudioRules.allowedName(name) || !AudioLibrary.files(true).contains(name)) throw new IllegalStateException("Refresh omitted an actual song of length "+name.length());
            try(var stream=FileAudioStreams.open(AudioLibrary.resolve(true,name),false)) {
                if(stream.read(8192).remaining()==0 || stream.getFormat().getSampleRate()<=0) throw new IllegalStateException("MP3 decode failed");
            }
            System.out.println("PASS refreshed, resolved and decoded actual MP3; filename characters="+name.length());count++;
        }
        if(!AudioRules.allowedName("a".repeat(251)+".mp3") || AudioRules.allowedName("a".repeat(252)+".mp3")) throw new IllegalStateException("Wrong 255-character boundary");
        for(String unsafe:List.of("../song.mp3","/song.mp3","C:music.mp3","sub\\song.mp3","hidden\n.mp3",".hidden.mp3"))
            if(AudioRules.allowedName(unsafe)) throw new IllegalStateException("Unsafe filename allowed");
        Path later=game.resolve("orbe_audio/musica/añadida después.wav");Files.copy(game.resolve("orbe_audio/musica/Abismo_ambiente.wav"),later);
        if(!AudioLibrary.files(true).contains(later.getFileName().toString())) throw new IllegalStateException("Refresh failed to reread the directory");
        System.out.println("PASS 255-character boundary, unsafe names rejected, directory reread without restarting");
        System.out.println("ORB-AUDIO actualSongs="+count+" errors=0");
    }
}
