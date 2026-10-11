package dev.bettermusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricsfileTest {
    static final String WORDS="""
        version: '1.0'
        metadata: {title: Original, artist: Example, offset_ms: 1000}
        lines:
          - text: 'Your world'
            start_ms: 10000
            end_ms: 13000
            words:
              - {text: 'Your ', start_ms: 10000, end_ms: 11000}
              - {text: world, start_ms: 11000, end_ms: 13000}
        plain: |
          Your world
        """;
    @Test void suppliedWordsWhitespaceAndEndTimesSurvive(){
        var content=Lyricsfile.parse(WORDS);assertTrue(content.wordSync());assertEquals(1,content.lines().size());
        var line=content.lines().get(0);assertEquals(10,line.time());assertEquals(13,line.end());assertEquals("Your ",line.highlighted(10.5));assertEquals("Your world",line.highlighted(11));
        assertEquals("Your world\n",content.plain());
    }
    @Test void invalidWordsKeepLineTimingAndUnsupportedVersionsAreRejected(){
        for(String invalid:new String[]{WORDS.replace("start_ms: 11000","start_ms: 9000"),WORDS.replace("text: world","text: other"),WORDS.replace("start_ms: 11000","start_ms: .nan")}){
            var content=Lyricsfile.parse(invalid);assertEquals(1,content.lines().size());assertFalse(content.wordSync());assertEquals("Your world",content.lines().get(0).text());
        }
        assertTrue(Lyricsfile.parse(WORDS.replace("'1.0'","'2.0'")).lines().isEmpty());
    }
    @Test void unsafeYamlDuplicateKeysAndOversizedDocumentsAreRejected(){
        for(String source:new String[]{"!!java.net.URL [https://example.com]",WORDS+"version: '1.0'\n",WORDS+"a: &recursive [*recursive]\n"," ".repeat(1_000_001)})assertTrue(Lyricsfile.parse(source).lines().isEmpty());
    }
    @Test void plainAndInstrumentalRemainDistinctWithoutFabricatedTimes(){
        var plain=Lyricsfile.parse("version: '1.0'\nplain: Original text\n");assertEquals("Original text",plain.plain());assertTrue(plain.lines().isEmpty());
        var instrumental=Lyricsfile.parse(WORDS.replace("offset_ms: 1000","instrumental: true"));assertTrue(instrumental.instrumental());assertTrue(instrumental.lines().isEmpty());assertTrue(instrumental.plain().isEmpty());
    }
}
