package fr.noop.subtitle;

import fr.noop.subtitle.model.SubtitleParser;
import fr.noop.subtitle.model.SubtitleObject;
import fr.noop.subtitle.model.SubtitleParsingException;
import fr.noop.subtitle.model.SubtitleObject.Property;
import fr.noop.subtitle.util.SubtitleTimeCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.apache.commons.io.input.BOMInputStream;

public class Analyze {
    public Analyze() {
    }

    public void run(String filePath, String outputFilePath) {
        // Build parser for input file
        SubtitleParser subtitleParser = null;

        try {
            subtitleParser = new Convert().buildParser(filePath, "utf-8");
        } catch(IOException e) {
            System.out.println(String.format("Unable to build parser for file %s: %s", filePath, e.getMessage()));
            System.exit(1);
        }

        InputStream is = null;
        BOMInputStream bom = null;

        // Open input file
        try {
            is = new FileInputStream(filePath);
            bom = new BOMInputStream(is);
        } catch(IOException e) {
            System.out.println(String.format("Input file %s does not exist: %s", filePath, e.getMessage()));
            System.exit(1);
        }

        // Parse input file
        SubtitleObject inputSubtitle = null;

        try {
            inputSubtitle = subtitleParser.parse(bom, true);
        } catch (IOException e) {
            System.out.println(String.format("Unable ro read input file %s: %s", filePath, e.getMessage()));
            System.exit(1);
        } catch (SubtitleParsingException e) {
            System.out.println(String.format("Unable to parse input file %s;: %s", filePath, e.getMessage()));
            System.exit(1);
        }

        JSONObject obj = new JSONObject();

        if (inputSubtitle.hasProperty(Property.FRAME_RATE)) {
            obj.put("frame_rate", (float) inputSubtitle.getProperty(Property.FRAME_RATE));
        }
        if (inputSubtitle.hasProperty(Property.START_TIMECODE_PRE_ROLL)) {
            obj.put("start_timecode", (SubtitleTimeCode) inputSubtitle.getProperty(Property.START_TIMECODE_PRE_ROLL));
        }
        obj.put("first_cue", (SubtitleTimeCode) inputSubtitle.getCues().get(0).getStartTime());

        // Write output file
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath))){
            obj.write(writer);
        } catch (IOException e) {
            System.out.println(String.format("Unable to write output file %s: %s", outputFilePath, e.getMessage()));
            System.exit(1);
        }
    }
}
