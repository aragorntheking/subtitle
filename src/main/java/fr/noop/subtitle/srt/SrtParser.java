/*
 *  This file is part of the noOp organization .
 *
 *  (c) Cyrille Lebeaupin <clebeaupin@noop.fr>
 *
 *  For the full copyright and license information, please view the LICENSE
 *  file that was distributed with this source code.
 *
 */

package fr.noop.subtitle.srt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.noop.subtitle.model.SubtitleParser;
import fr.noop.subtitle.model.SubtitleParsingException;
import fr.noop.subtitle.util.SubtitlePlainText;
import fr.noop.subtitle.util.SubtitleStyle;
import fr.noop.subtitle.util.SubtitleStyledText;
import fr.noop.subtitle.util.SubtitleTextLine;
import fr.noop.subtitle.util.SubtitleTimeCode;

/**
 * Created by clebeaupin on 21/09/15.
 */
public class SrtParser implements SubtitleParser {
    private enum CursorStatus {
        NONE,
        CUE_ID,
        CUE_TIMECODE,
        CUE_TEXT;
    }

    private String charset; // Charset of the input files

    public SrtParser(String charset) {
        this.charset = charset;
    }

    @Override
    public SrtObject parse(InputStream is) throws IOException, SubtitleParsingException {
    	return parse(is, true);
    }
    
    @Override
    public SrtObject parse(InputStream is, boolean strict) throws IOException, SubtitleParsingException {
        // Create srt object
        SrtObject srtObject = new SrtObject();

        // Read each lines
        BufferedReader br = new BufferedReader(new InputStreamReader(is, this.charset));
        String textLine = "";
        CursorStatus cursorStatus = CursorStatus.NONE;
        SrtCue cue = null;
        String hexCode = null;
        boolean italic = false;
        boolean bold = false;
        boolean underline = false;
        boolean color = false;

        while ((textLine = br.readLine()) != null) {
            textLine = textLine.trim();

            if (cursorStatus == CursorStatus.NONE) {
                if (textLine.isEmpty()) {
                    continue;
                }

                // New cue
                cue = new SrtCue();

                // First textLine is the cue number
                try {
                    Integer.parseInt(textLine);
                } catch (NumberFormatException e) {
                    throw new SubtitleParsingException(String.format(
                            "Unable to parse cue number: %s",
                            textLine));
                }

                cue.setId(textLine);
                cursorStatus = CursorStatus.CUE_ID;
                continue;
            }

            // Second textLine defines the start and end time codes
            // 00:01:21,456 --> 00:01:23,417
            if (cursorStatus == CursorStatus.CUE_ID) {
                if (!textLine.substring(13, 16).equals("-->")) {
                    throw new SubtitleParsingException(String.format(
                            "Timecode textLine is badly formated: %s", textLine));
                }

                cue.setStartTime(this.parseTimeCode(textLine.substring(0, 12)));
                cue.setEndTime(this.parseTimeCode(textLine.substring(17)));
                cursorStatus = CursorStatus.CUE_TIMECODE;
                continue;
            }

            // Following lines are the cue lines
            if (!textLine.isEmpty() && (
                    cursorStatus == CursorStatus.CUE_TIMECODE ||
                    cursorStatus ==  CursorStatus.CUE_TEXT)) {
                SubtitleTextLine line = new SubtitleTextLine();
                SubtitleStyle textStyle = new SubtitleStyle();
                if (textLine.contains("<i>")) {
                    italic = true;
                    textLine = textLine.replaceAll("<i>", "");
                }
                if (italic) {
                    textStyle.setFontStyle(SubtitleStyle.FontStyle.ITALIC);
                }
                if (textLine.contains("</i>")) {
                    italic = false;
                    textLine = textLine.replaceAll("</i>", "");
                }
                if (textLine.contains("<b>")) {
                    bold = true;
                    textLine = textLine.replaceAll("<b>", "");
                }
                if (bold) {
                    textStyle.setFontWeight(SubtitleStyle.FontWeight.BOLD);
                }
                if (textLine.contains("</b>")) {
                    bold = false;
                    textLine = textLine.replaceAll("</b>", "");
                }
                if (textLine.contains("<u>")) {
                    underline = true;
                    textLine = textLine.replaceAll("<u>", "");
                }
                if (underline) {
                    textStyle.setTextDecoration(SubtitleStyle.TextDecoration.UNDERLINE);
                }
                if (textLine.contains("</u>")) {
                    underline = false;
                    textLine = textLine.replaceAll("</u>", "");
                }
                if (textLine.contains("<font color=")) {
                    color = true;
                    Pattern pattern = Pattern.compile("#(?:[a-f\\d]{3}){1,2}\\b");
                    Matcher matcher = pattern.matcher(textLine);
                    if (matcher.find()) {
                        hexCode = matcher.group();
                    }
                    textLine = textLine.replaceAll("<font color=\"#(?:[a-f\\d]{3}){1,2}\\b\">", "");
                }
                if (color && hexCode != null) {
                    textStyle.setColor(HexRGB.Color.getEnumFromHexCode(hexCode).getColorName());
                }
                if (textLine.contains("</font>")) {
                    color = false;
                    textLine = textLine.replaceAll("</font>", "");
                }

                if (textStyle.hasProperties()) {
                    line.addText(new SubtitleStyledText(textLine, textStyle));
                } else {
                    line.addText(new SubtitlePlainText(textLine));
                }
                cue.addLine(line);
                cursorStatus = CursorStatus.CUE_TEXT;
                continue;
            }

            if (cursorStatus == CursorStatus.CUE_TEXT && textLine.isEmpty()) {
                // End of cue
                srtObject.addCue(cue);
                cue = null;
                cursorStatus = CursorStatus.NONE;
                continue;
            }

            throw new SubtitleParsingException(String.format(
                    "Unexpected line: %s", textLine));
        }

        if (cue != null) {
            srtObject.addCue(cue);
        }

        return srtObject;
    }

    private SubtitleTimeCode parseTimeCode(String timeCodeString) throws SubtitleParsingException {
        try {
            int hour = Integer.parseInt(timeCodeString.substring(0, 2));
            int minute = Integer.parseInt(timeCodeString.substring(3, 5));
            int second = Integer.parseInt(timeCodeString.substring(6, 8));
            int millisecond = Integer.parseInt(timeCodeString.substring(9, 12));
            return new SubtitleTimeCode(hour, minute, second, millisecond);
        } catch (NumberFormatException e) {
            throw new SubtitleParsingException(String.format(
                    "Unable to parse time code: %s", timeCodeString));
        }
    }
}
